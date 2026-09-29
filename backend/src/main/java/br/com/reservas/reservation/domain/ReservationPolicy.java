package br.com.reservas.reservation.domain;

import br.com.reservas.area.application.AreaBookingInfo;
import br.com.reservas.area.application.OpeningHoursRange;
import br.com.reservas.area.domain.AreaStatus;
import br.com.reservas.settings.application.RuleSettingsSnapshot;
import br.com.reservas.shared.error.BusinessException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/**
 * Motor de regras RN-18..24 (D-12): módulo de domínio puro (sem Spring, sem
 * repositório), testável só com um {@link ZonedDateTime} "agora" já no fuso
 * do condomínio e os parâmetros de {@link RuleSettingsSnapshot}. Uma regra
 * por método privado, chamadas na ordem do contrato (docs/03): RN-18, RN-19
 * (slot + horário de funcionamento), RN-20, RN-21, RN-23, RN-22. RN-24
 * (sobreposição) não está aqui: é a exclusion constraint do banco (V6).
 *
 * <p>{@link #checkDay} reaproveita a mesma lógica de RN-18/RN-20/RN-21 para a
 * disponibilidade por dia (F4-4, sem slot/capacidade/limite por unidade),
 * devolvendo o motivo em vez de lançar — é o mesmo cálculo, só sem o "throw".
 *
 * <p>Generalização de RN-20 para {@code min_advance_days > 1} (o default do
 * MVP é 1): seja D = dias entre hoje e a data pedida. D &lt; min_advance_days
 * é sempre recusado (`SAME_DAY_NOT_ALLOWED`, mensagem genérica quando
 * min_advance_days &gt; 1); exatamente D == min_advance_days é o "dia
 * seguinte" da RN-20 e sofre a janela de horário
 * (`NEXT_DAY_WINDOW_CLOSED`); D &gt; min_advance_days não tem restrição de
 * horário.
 */
public final class ReservationPolicy {

    private ReservationPolicy() {
    }

    public record Request(LocalDate date, LocalTime startTime, LocalTime endTime, int guests) {
    }

    public record Context(ZonedDateTime now, RuleSettingsSnapshot rules, AreaBookingInfo area,
        long activeUnitBookings) {
    }

    public record DayReason(String code, String detail) {
    }

    /** Valida uma solicitação completa (POST /reservations); lança {@link BusinessException} na primeira falha. */
    public static void validate(Request request, Context context) {
        requireActiveArea(context.area());
        requireValidSlot(request.startTime(), request.endTime(), context.rules().slotMinutes());
        requireOpeningHours(request.date(), request.startTime(), request.endTime(), context.area());
        requireAdvanceWindow(request.date(), context.now(), context.rules());
        requireMaxAdvance(request.date(), context.now(), context.rules());
        requireCapacity(request.guests(), context.area());
        requireUnitLimit(context.activeUnitBookings(), context.rules());
    }

    /**
     * RN-28: alteração de reserva pelo ADMIN revalida só RN-18, RN-19 e RN-23
     * (RN-24 é a exclusion constraint do banco); não revalida antecedência
     * (RN-20/21) nem limite por unidade (RN-22), por ser ação administrativa.
     */
    public static void validateForAdminUpdate(Request request, int slotMinutes, AreaBookingInfo area) {
        requireActiveArea(area);
        requireValidSlot(request.startTime(), request.endTime(), slotMinutes);
        requireOpeningHours(request.date(), request.startTime(), request.endTime(), area);
        requireCapacity(request.guests(), area);
    }

    /**
     * RN-33: bloqueio (`POST /blocks`) só revalida RN-19 (slot + horário de
     * funcionamento); RN-24 é a exclusion constraint do banco. Sem RN-18
     * (área não precisa estar ACTIVE), RN-20/21 (sem antecedência mínima/janela),
     * RN-22 (sem limite por unidade) nem RN-23 (sem convidados) — D-49.
     */
    public static void validateForBlock(Request request, int slotMinutes, AreaBookingInfo area) {
        requireValidSlot(request.startTime(), request.endTime(), slotMinutes);
        requireOpeningHours(request.date(), request.startTime(), request.endTime(), area);
    }

    /** RN-18/RN-20/RN-21 aplicadas a um dia inteiro (disponibilidade, F4-4): sem slot, capacidade ou limite. */
    public static Optional<DayReason> checkDay(LocalDate date, ZonedDateTime now, RuleSettingsSnapshot rules,
        AreaBookingInfo area) {
        if (area.status() != AreaStatus.ACTIVE) {
            return Optional.of(new DayReason("AREA_NOT_ACTIVE", "A área não está disponível para reservas."));
        }
        Optional<DayReason> advance = advanceReason(date, now, rules);
        if (advance.isPresent()) {
            return advance;
        }
        if (daysAhead(date, now) > rules.maxAdvanceDays()) {
            return Optional.of(new DayReason("TOO_FAR_AHEAD",
                "Você pode reservar com até " + rules.maxAdvanceDays() + " dias de antecedência."));
        }
        return Optional.empty();
    }

    // RN-18
    private static void requireActiveArea(AreaBookingInfo area) {
        if (area.status() != AreaStatus.ACTIVE) {
            throw validationError("AREA_NOT_ACTIVE", "A área não está disponível para reservas.");
        }
    }

    // RN-19 (slot: múltiplos de slot_minutes, fim > início)
    private static void requireValidSlot(LocalTime start, LocalTime end, int slotMinutes) {
        if (!end.isAfter(start)) {
            throw validationError("INVALID_SLOT", "O horário de término precisa ser depois do de início.");
        }
        int stepSeconds = slotMinutes * 60;
        if (start.toSecondOfDay() % stepSeconds != 0 || end.toSecondOfDay() % stepSeconds != 0) {
            throw validationError("INVALID_SLOT", "Horário precisa ser em múltiplos de " + slotMinutes
                + " minutos.");
        }
    }

    // RN-19 (dentro do horário de funcionamento do dia da semana)
    private static void requireOpeningHours(LocalDate date, LocalTime start, LocalTime end, AreaBookingInfo area) {
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1 = segunda ... 7 = domingo (igual a OpeningHours)
        OpeningHoursRange hours = area.hoursForDay(dayOfWeek)
            .orElseThrow(() -> validationError("OUTSIDE_OPENING_HOURS", "A área não funciona nesse dia da semana."));
        if (start.isBefore(hours.openTime()) || end.isAfter(hours.closeTime())) {
            throw validationError("OUTSIDE_OPENING_HOURS", "O horário precisa estar dentro do funcionamento da "
                + "área (" + format(hours.openTime()) + " às " + format(hours.closeTime()) + ").");
        }
    }

    // RN-20
    private static void requireAdvanceWindow(LocalDate date, ZonedDateTime now, RuleSettingsSnapshot rules) {
        advanceReason(date, now, rules).ifPresent(reason -> {
            throw validationError(reason.code(), reason.detail());
        });
    }

    private static Optional<DayReason> advanceReason(LocalDate date, ZonedDateTime now, RuleSettingsSnapshot rules) {
        long daysAhead = daysAhead(date, now);
        int minAdvanceDays = rules.minAdvanceDays();
        if (daysAhead < minAdvanceDays) {
            String detail = minAdvanceDays <= 1 ? "Não é possível reservar para o mesmo dia."
                : "É preciso reservar com pelo menos " + minAdvanceDays + " dias de antecedência.";
            return Optional.of(new DayReason("SAME_DAY_NOT_ALLOWED", detail));
        }
        if (daysAhead == minAdvanceDays) {
            LocalTime nowTime = now.toLocalTime();
            LocalTime windowStart = rules.nextDayWindowStart();
            LocalTime windowEnd = rules.nextDayWindowEnd();
            if (nowTime.isBefore(windowStart) || nowTime.isAfter(windowEnd)) {
                return Optional.of(new DayReason("NEXT_DAY_WINDOW_CLOSED", "Reservas para amanhã só podem ser "
                    + "feitas entre " + format(windowStart) + " e " + format(windowEnd) + "."));
            }
        }
        return Optional.empty();
    }

    // RN-21
    private static void requireMaxAdvance(LocalDate date, ZonedDateTime now, RuleSettingsSnapshot rules) {
        if (daysAhead(date, now) > rules.maxAdvanceDays()) {
            throw validationError("TOO_FAR_AHEAD", "Você pode reservar com até " + rules.maxAdvanceDays()
                + " dias de antecedência.");
        }
    }

    // RN-23
    private static void requireCapacity(int guests, AreaBookingInfo area) {
        if (guests > area.capacity()) {
            throw validationError("CAPACITY_EXCEEDED", "O número de convidados excede a capacidade da área ("
                + area.capacity() + ").");
        }
    }

    // RN-22 (0 = sem limite)
    private static void requireUnitLimit(long activeUnitBookings, RuleSettingsSnapshot rules) {
        int max = rules.maxActiveBookingsPerUnit();
        if (max > 0 && activeUnitBookings >= max) {
            throw validationError("UNIT_BOOKING_LIMIT_REACHED", "Sua unidade já atingiu o limite de " + max
                + " reservas futuras ativas.");
        }
    }

    private static long daysAhead(LocalDate date, ZonedDateTime now) {
        return ChronoUnit.DAYS.between(now.toLocalDate(), date);
    }

    private static String format(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    private static BusinessException validationError(String code, String detail) {
        return new BusinessException(code, HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}

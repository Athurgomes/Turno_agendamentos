package br.com.reservas.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.area.application.AreaBookingInfo;
import br.com.reservas.area.application.OpeningHoursRange;
import br.com.reservas.area.domain.AreaStatus;
import br.com.reservas.settings.application.RuleSettingsSnapshot;
import br.com.reservas.shared.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `ReservationPolicy`: RN-18..24 (RN-24 é a exclusion constraint do banco,
 * fora daqui). Todo teste usa um "agora" fixo (sem `Clock` do Spring: a
 * política nem depende dele, só de um {@link ZonedDateTime}), no fuso do
 * condomínio.
 */
class ReservationPolicyTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final AreaBookingInfo openArea = area(AreaStatus.ACTIVE, 10, false, null);

    @Test
    @DisplayName("RN-18: área fora de ACTIVE é recusada, mesmo com o resto válido")
    void rejectsInactiveArea() {
        AreaBookingInfo inactive = area(AreaStatus.MAINTENANCE, 10, false, null);
        assertThatThrownBy(() -> ReservationPolicy.validate(validRequest(), context(inactive, todayAt(10, 0), 2, 0)))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("AREA_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("RN-19: fim antes ou igual ao início é INVALID_SLOT")
    void rejectsEndNotAfterStart() {
        ReservationPolicy.Request request = new ReservationPolicy.Request(tomorrow(), LocalTime.of(14, 0),
            LocalTime.of(14, 0), 2);
        assertCode(() -> ReservationPolicy.validate(request, context(openArea, todayAt(7, 0), 2, 0)),
            "INVALID_SLOT");
    }

    @Test
    @DisplayName("RN-19: horário fora de múltiplos de 30 minutos é INVALID_SLOT")
    void rejectsSlotNotAMultipleOfStep() {
        ReservationPolicy.Request request = new ReservationPolicy.Request(tomorrow(), LocalTime.of(14, 10),
            LocalTime.of(16, 0), 2);
        assertCode(() -> ReservationPolicy.validate(request, context(openArea, todayAt(7, 0), 2, 0)),
            "INVALID_SLOT");
    }

    @Test
    @DisplayName("RN-19: dia da semana sem horário cadastrado é OUTSIDE_OPENING_HOURS")
    void rejectsDayWithoutOpeningHours() {
        AreaBookingInfo areaClosedOnDay = new AreaBookingInfo(UUID.randomUUID(), "Quadra", AreaStatus.ACTIVE, 10,
            false, null, null, List.of());
        assertCode(() -> ReservationPolicy.validate(validRequest(), context(areaClosedOnDay, todayAt(7, 0), 2, 0)),
            "OUTSIDE_OPENING_HOURS");
    }

    @Test
    @DisplayName("RN-19: horário fora do intervalo de funcionamento é OUTSIDE_OPENING_HOURS")
    void rejectsTimeOutsideOpeningHours() {
        AreaBookingInfo areaClosingEarly = new AreaBookingInfo(UUID.randomUUID(), "Salão", AreaStatus.ACTIVE, 10,
            false, null, null, List.of(new OpeningHoursRange(tomorrow().getDayOfWeek().getValue(),
                LocalTime.of(8, 0), LocalTime.of(22, 0))));
        ReservationPolicy.Request request = new ReservationPolicy.Request(tomorrow(), LocalTime.of(22, 0),
            LocalTime.of(23, 0), 2);
        assertCode(() -> ReservationPolicy.validate(request, context(areaClosingEarly, todayAt(7, 0), 2, 0)),
            "OUTSIDE_OPENING_HOURS");
    }

    @Test
    @DisplayName("RN-20/aceite F4: hoje 15:59, reserva para amanhã é aceita")
    void acceptsTomorrowAt1559() {
        Optional<ReservationPolicy.DayReason> reason = ReservationPolicy.checkDay(tomorrow(), todayAt(15, 59),
            rules(), openArea);
        assertThat(reason).isEmpty();
    }

    @Test
    @DisplayName("RN-20/aceite F4: hoje 16:01, reserva para amanhã é NEXT_DAY_WINDOW_CLOSED")
    void rejectsTomorrowAt1601() {
        Optional<ReservationPolicy.DayReason> reason = ReservationPolicy.checkDay(tomorrow(), todayAt(16, 1),
            rules(), openArea);
        assertThat(reason).isPresent();
        assertThat(reason.get().code()).isEqualTo("NEXT_DAY_WINDOW_CLOSED");
        assertThat(reason.get().detail())
            .isEqualTo("Reservas para amanhã só podem ser feitas entre 06:00 e 16:00.");
    }

    @Test
    @DisplayName("RN-20/aceite F4: hoje 05:59, reserva para amanhã é recusada (NEXT_DAY_WINDOW_CLOSED)")
    void rejectsTomorrowAt0559() {
        Optional<ReservationPolicy.DayReason> reason = ReservationPolicy.checkDay(tomorrow(), todayAt(5, 59),
            rules(), openArea);
        assertThat(reason).map(ReservationPolicy.DayReason::code).contains("NEXT_DAY_WINDOW_CLOSED");
    }

    @Test
    @DisplayName("RN-20/aceite F4: hoje 16:00 exato (inclusive), reserva para amanhã é aceita")
    void acceptsTomorrowAtExactly1600() {
        Optional<ReservationPolicy.DayReason> reason = ReservationPolicy.checkDay(tomorrow(), todayAt(16, 0),
            rules(), openArea);
        assertThat(reason).isEmpty();
    }

    @Test
    @DisplayName("RN-20/aceite F4: reserva para hoje é SAME_DAY_NOT_ALLOWED")
    void rejectsSameDay() {
        LocalDate today = LocalDate.now(ZONE);
        Optional<ReservationPolicy.DayReason> reason = ReservationPolicy.checkDay(today, todayAt(7, 0), rules(),
            openArea);
        assertThat(reason).map(ReservationPolicy.DayReason::code).contains("SAME_DAY_NOT_ALLOWED");
    }

    @Test
    @DisplayName("RN-21/aceite F4: 61 dias à frente é TOO_FAR_AHEAD")
    void rejectsSixtyOneDaysAhead() {
        LocalDate date = LocalDate.now(ZONE).plusDays(61);
        Optional<ReservationPolicy.DayReason> reason = ReservationPolicy.checkDay(date, todayAt(10, 0), rules(),
            openArea);
        assertThat(reason).map(ReservationPolicy.DayReason::code).contains("TOO_FAR_AHEAD");
    }

    @Test
    @DisplayName("RN-21/aceite F4: 60 dias à frente é aceito (limite máximo, inclusive)")
    void acceptsSixtyDaysAhead() {
        LocalDate date = LocalDate.now(ZONE).plusDays(60);
        Optional<ReservationPolicy.DayReason> reason = ReservationPolicy.checkDay(date, todayAt(10, 0), rules(),
            openArea);
        assertThat(reason).isEmpty();
    }

    @Test
    @DisplayName("RN-23: convidados acima da capacidade é CAPACITY_EXCEEDED")
    void rejectsGuestsOverCapacity() {
        ReservationPolicy.Request request = new ReservationPolicy.Request(tomorrow(), LocalTime.of(14, 0),
            LocalTime.of(16, 0), 11);
        assertCode(() -> ReservationPolicy.validate(request, context(openArea, todayAt(7, 0), 11, 0)),
            "CAPACITY_EXCEEDED");
    }

    @Test
    @DisplayName("RN-22: unidade no limite de reservas ativas é UNIT_BOOKING_LIMIT_REACHED")
    void rejectsUnitAtBookingLimit() {
        assertCode(() -> ReservationPolicy.validate(validRequest(), context(openArea, todayAt(7, 0), 2, 3)),
            "UNIT_BOOKING_LIMIT_REACHED");
    }

    @Test
    @DisplayName("RN-22: limite 0 (parametrizado) significa sem limite, mesmo com muitas reservas ativas")
    void acceptsUnlimitedBookingsWhenMaxIsZero() {
        RuleSettingsSnapshot unlimited = new RuleSettingsSnapshot("America/Sao_Paulo", 1, LocalTime.of(6, 0),
            LocalTime.of(16, 0), 60, 0, 24, 30, 7);
        ReservationPolicy.Context context = new ReservationPolicy.Context(todayAt(7, 0), unlimited, openArea, 50);
        ReservationPolicy.validate(validRequest(), context);
        // sem exceção: passou mesmo com 50 reservas ativas.
    }

    @Test
    @DisplayName("Ordem do contrato: RN-18 é checada antes de RN-19 (área inativa prevalece)")
    void checksAreaActiveBeforeSlot() {
        AreaBookingInfo inactive = area(AreaStatus.INACTIVE, 10, false, null);
        ReservationPolicy.Request invalidSlotRequest = new ReservationPolicy.Request(tomorrow(), LocalTime.of(14, 0),
            LocalTime.of(14, 0), 2);
        assertCode(() -> ReservationPolicy.validate(invalidSlotRequest, context(inactive, todayAt(7, 0), 2, 0)),
            "AREA_NOT_ACTIVE");
    }

    @Test
    @DisplayName("Ordem do contrato: RN-23 é checada antes de RN-22 (capacidade prevalece sobre limite)")
    void checksCapacityBeforeUnitLimit() {
        ReservationPolicy.Request request = new ReservationPolicy.Request(tomorrow(), LocalTime.of(14, 0),
            LocalTime.of(16, 0), 11);
        assertCode(() -> ReservationPolicy.validate(request, context(openArea, todayAt(7, 0), 11, 3)),
            "CAPACITY_EXCEEDED");
    }

    @Test
    @DisplayName("RN-33/D-49: bloqueio em área não ACTIVE (sem RN-18) e sem antecedência (sem RN-20/21) é aceito")
    void blockAcceptsInactiveAreaAndSameDay() {
        AreaBookingInfo inactive = area(AreaStatus.MAINTENANCE, 10, false, null);
        LocalDate today = LocalDate.now(ZONE);
        ReservationPolicy.Request request = new ReservationPolicy.Request(today, LocalTime.of(14, 0),
            LocalTime.of(16, 0), 0);
        ReservationPolicy.validateForBlock(request, 30, inactive);
        // sem exceção: bloqueio não exige área ACTIVE nem antecedência mínima (D-49).
    }

    @Test
    @DisplayName("RN-33/RN-19: bloqueio com horário fora de múltiplos de 30 minutos é INVALID_SLOT")
    void blockRejectsSlotNotAMultipleOfStep() {
        ReservationPolicy.Request request = new ReservationPolicy.Request(tomorrow(), LocalTime.of(14, 10),
            LocalTime.of(16, 0), 0);
        assertCode(() -> ReservationPolicy.validateForBlock(request, 30, openArea), "INVALID_SLOT");
    }

    @Test
    @DisplayName("RN-33/RN-19: bloqueio fora do horário de funcionamento é OUTSIDE_OPENING_HOURS")
    void blockRejectsTimeOutsideOpeningHours() {
        AreaBookingInfo areaClosingEarly = new AreaBookingInfo(UUID.randomUUID(), "Salão", AreaStatus.ACTIVE, 10,
            false, null, null, List.of(new OpeningHoursRange(tomorrow().getDayOfWeek().getValue(),
                LocalTime.of(8, 0), LocalTime.of(22, 0))));
        ReservationPolicy.Request request = new ReservationPolicy.Request(tomorrow(), LocalTime.of(22, 0),
            LocalTime.of(23, 0), 0);
        assertCode(() -> ReservationPolicy.validateForBlock(request, 30, areaClosingEarly), "OUTSIDE_OPENING_HOURS");
    }

    private static void assertCode(ThrowingCallable callable, String expectedCode) {
        assertThatThrownBy(callable)
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo(expectedCode));
    }

    private ReservationPolicy.Context context(AreaBookingInfo area, ZonedDateTime now, int guests,
        long activeUnitBookings) {
        return new ReservationPolicy.Context(now, rules(), area, activeUnitBookings);
    }

    private static ReservationPolicy.Request validRequest() {
        return new ReservationPolicy.Request(tomorrow(), LocalTime.of(14, 0), LocalTime.of(16, 0), 2);
    }

    private static LocalDate tomorrow() {
        return LocalDate.now(ZONE).plusDays(1);
    }

    private static ZonedDateTime todayAt(int hour, int minute) {
        return LocalDate.now(ZONE).atTime(hour, minute).atZone(ZONE);
    }

    private static RuleSettingsSnapshot rules() {
        return new RuleSettingsSnapshot("America/Sao_Paulo", 1, LocalTime.of(6, 0), LocalTime.of(16, 0), 60, 3, 24,
            30, 7);
    }

    private static AreaBookingInfo area(AreaStatus status, int capacity, boolean requiresPayment, BigDecimal price) {
        List<OpeningHoursRange> allDaysAllDay = java.util.stream.IntStream.rangeClosed(1, 7)
            .mapToObj(day -> new OpeningHoursRange(day, LocalTime.of(0, 0), LocalTime.of(23, 30)))
            .toList();
        return new AreaBookingInfo(UUID.randomUUID(), "Salão", status, capacity, requiresPayment, price,
            requiresPayment ? "5562999998888" : null, allDaysAllDay);
    }
}

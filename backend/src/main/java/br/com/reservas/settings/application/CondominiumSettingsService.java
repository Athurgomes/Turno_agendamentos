package br.com.reservas.settings.application;

import br.com.reservas.shared.audit.AuditService;
import br.com.reservas.shared.condominium.CondominiumLookup;
import br.com.reservas.shared.error.BusinessException;
import br.com.reservas.settings.domain.Condominium;
import br.com.reservas.settings.domain.CondominiumSettings;
import br.com.reservas.settings.infra.CondominiumRepository;
import br.com.reservas.settings.infra.CondominiumSettingsRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `/admin/settings` + `/settings/public` (D-43, F2-7). Também é a interface
 * pública que a F4 (`reservation`) usa para ler o "motor de regras" (D-12)
 * via {@link #current()}.
 */
@Service
public class CondominiumSettingsService {

    private final CondominiumRepository condominiums;
    private final CondominiumSettingsRepository settingsRepository;
    private final CondominiumLookup condominiumLookup;
    private final AuditService audit;

    public CondominiumSettingsService(CondominiumRepository condominiums,
        CondominiumSettingsRepository settingsRepository, CondominiumLookup condominiumLookup, AuditService audit) {
        this.condominiums = condominiums;
        this.settingsRepository = settingsRepository;
        this.condominiumLookup = condominiumLookup;
        this.audit = audit;
    }

    /** F4: snapshot do motor de regras + fuso do condomínio (D-12). */
    @Transactional(readOnly = true)
    public RuleSettingsSnapshot current() {
        SettingsSnapshot snapshot = get();
        return new RuleSettingsSnapshot(snapshot.timezone(), snapshot.minAdvanceDays(),
            snapshot.nextDayWindowStart(), snapshot.nextDayWindowEnd(), snapshot.maxAdvanceDays(),
            snapshot.maxActiveBookingsPerUnit(), snapshot.residentCancelDeadlineHours(), snapshot.slotMinutes(),
            snapshot.reportWindowDays());
    }

    @Transactional(readOnly = true)
    public SettingsSnapshot get() {
        Condominium condominium = condominiumOrThrow();
        CondominiumSettings settings = settingsOrThrow(condominium.getId());
        return toSnapshot(condominium, settings);
    }

    @Transactional
    public SettingsSnapshot update(UpdateSettingsCommand command, UUID actorId) {
        Condominium condominium = condominiumOrThrow();
        CondominiumSettings settings = settingsOrThrow(condominium.getId());
        SettingsSnapshot before = toSnapshot(condominium, settings);

        validate(command);

        condominium.updateContactInfo(command.condominiumName(), command.defaultPaymentWhatsapp());
        settings.update(command.minAdvanceDays(), command.nextDayWindowStart(), command.nextDayWindowEnd(),
            command.maxAdvanceDays(), command.maxActiveBookingsPerUnit(), command.residentCancelDeadlineHours(),
            command.reportWindowDays());
        condominiums.save(condominium);
        settingsRepository.save(settings);

        SettingsSnapshot after = toSnapshot(condominium, settings);
        audit.record(actorId, "ADMIN", "SETTINGS_UPDATE", "CONDOMINIUM_SETTINGS", condominium.getId(),
            Map.of("before", asMap(before), "after", asMap(after)), null);
        return after;
    }

    private void validate(UpdateSettingsCommand command) {
        if (command.minAdvanceDays() < 0 || command.maxActiveBookingsPerUnit() < 0
            || command.residentCancelDeadlineHours() < 0 || command.reportWindowDays() < 0
            || command.maxAdvanceDays() < 0) {
            throw validationError("Os parâmetros numéricos não podem ser negativos.");
        }
        if (command.maxAdvanceDays() < command.minAdvanceDays()) {
            throw validationError("O prazo máximo de antecedência deve ser maior ou igual ao mínimo.");
        }
        if (!command.nextDayWindowStart().isBefore(command.nextDayWindowEnd())) {
            throw validationError("O início da janela do dia seguinte deve ser antes do fim.");
        }
    }

    private static BusinessException validationError(String detail) {
        return new BusinessException("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    private Condominium condominiumOrThrow() {
        UUID id = condominiumLookup.currentId()
            .orElseThrow(() -> new IllegalStateException("Nenhum condomínio cadastrado (D-13)."));
        return condominiums.findById(id).orElseThrow();
    }

    private CondominiumSettings settingsOrThrow(UUID condominiumId) {
        return settingsRepository.findById(condominiumId).orElseThrow();
    }

    private static SettingsSnapshot toSnapshot(Condominium condominium, CondominiumSettings settings) {
        return new SettingsSnapshot(condominium.getName(), condominium.getTimezone(),
            condominium.getDefaultPaymentWhatsapp(), settings.getMinAdvanceDays(), settings.getNextDayWindowStart(),
            settings.getNextDayWindowEnd(), settings.getMaxAdvanceDays(), settings.getMaxActiveBookingsPerUnit(),
            settings.getResidentCancelDeadlineHours(), settings.getSlotMinutes(), settings.getReportWindowDays());
    }

    private static Map<String, Object> asMap(SettingsSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("condominiumName", snapshot.condominiumName());
        map.put("defaultPaymentWhatsapp", snapshot.defaultPaymentWhatsapp());
        map.put("minAdvanceDays", snapshot.minAdvanceDays());
        map.put("nextDayWindowStart", snapshot.nextDayWindowStart().toString());
        map.put("nextDayWindowEnd", snapshot.nextDayWindowEnd().toString());
        map.put("maxAdvanceDays", snapshot.maxAdvanceDays());
        map.put("maxActiveBookingsPerUnit", snapshot.maxActiveBookingsPerUnit());
        map.put("residentCancelDeadlineHours", snapshot.residentCancelDeadlineHours());
        map.put("reportWindowDays", snapshot.reportWindowDays());
        return map;
    }
}

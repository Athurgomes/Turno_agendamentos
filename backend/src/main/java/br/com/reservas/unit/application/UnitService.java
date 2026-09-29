package br.com.reservas.unit.application;

import br.com.reservas.auth.application.AccountService;
import br.com.reservas.shared.audit.AuditService;
import br.com.reservas.shared.error.BusinessException;
import br.com.reservas.shared.reservation.ReservationSummary;
import br.com.reservas.unit.domain.Cpf;
import br.com.reservas.unit.domain.Resident;
import br.com.reservas.unit.domain.Unit;
import br.com.reservas.unit.domain.UnitIdentifier;
import br.com.reservas.unit.infra.ResidentRepository;
import br.com.reservas.unit.infra.UnitRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interface pública do módulo `unit` para o ADMIN gerenciar unidades e
 * moradores (RF-UNI-01..04, RN-02, RN-07..10, D-43, D-47, P-11). Também é o
 * ponto que a F4 (`reservation`) vai usar para achar unidade/morador por id
 * (ver {@link #findActiveUnit(UUID)} / {@link #findActiveResident(UUID, UUID)}).
 */
@Service
public class UnitService {

    private final UnitRepository units;
    private final ResidentRepository residents;
    private final AccountService accounts;
    private final AuditService audit;
    private final FutureReservationsPort futureReservations;
    private final Clock clock;

    public UnitService(UnitRepository units, ResidentRepository residents, AccountService accounts,
        AuditService audit, FutureReservationsPort futureReservations, Clock clock) {
        this.units = units;
        this.residents = residents;
        this.accounts = accounts;
        this.audit = audit;
        this.futureReservations = futureReservations;
        this.clock = clock;
    }

    /** RF-UNI-01/02: cria a unidade, os moradores e a conta UNIT (username = identificador, RN-02). */
    @Transactional
    public CreateUnitResult create(UUID condominiumId, UUID actorId, String block, String number,
        List<ResidentInput> residentInputs) {
        String identifier = UnitIdentifier.normalize(block, number);
        if (units.existsByCondominiumIdAndIdentifierIgnoreCaseAndDeletedAtIsNull(condominiumId, identifier)) {
            throw new BusinessException("UNIT_IDENTIFIER_TAKEN", HttpStatus.CONFLICT,
                "Já existe uma unidade ativa com este identificador.");
        }
        validateResidentInputs(residentInputs);

        Unit unit = units.save(new Unit(condominiumId, blankToNull(block), number, identifier));
        List<Resident> saved = saveResidents(unit.getId(), residentInputs);

        String tempPassword = accounts.createUnitAccount(condominiumId, unit.getId(), identifier);
        Credentials credentials = new Credentials(identifier, tempPassword);

        audit.record(actorId, "ADMIN", "UNIT_CREATE", "UNIT", unit.getId(),
            Map.of("identifier", identifier), null);
        return new CreateUnitResult(unit, saved, credentials);
    }

    @Transactional(readOnly = true)
    public Unit findActiveUnitOrThrow(UUID unitId) {
        return units.findByIdAndDeletedAtIsNull(unitId)
            .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada: " + unitId));
    }

    /** F4: unidade ativa por id, sem erro (`reservation` decide o que fazer se ausente). */
    @Transactional(readOnly = true)
    public java.util.Optional<Unit> findActiveUnit(UUID unitId) {
        return units.findByIdAndDeletedAtIsNull(unitId);
    }

    /** F4-5: id da unidade ativa por identificador (agenda S/A, D-53); sem correspondência -&gt; vazio. */
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> findIdByIdentifier(String identifier) {
        return units.findByIdentifierIgnoreCaseAndDeletedAtIsNull(identifier).map(Unit::getId);
    }

    /** F4: identificador de várias unidades ativas de uma vez (evita N+1 em listagens de reservas). */
    @Transactional(readOnly = true)
    public Map<UUID, String> identifiersByIds(Collection<UUID> unitIds) {
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        return units.findByIdInAndDeletedAtIsNull(unitIds).stream()
            .collect(Collectors.toMap(Unit::getId, Unit::getIdentifier));
    }

    @Transactional(readOnly = true)
    public List<Resident> activeResidents(UUID unitId) {
        return residents.findByUnitIdAndDeletedAtIsNull(unitId);
    }

    /** F4: morador ativo da unidade por id, para snapshot nome/telefone no momento da reserva. */
    @Transactional(readOnly = true)
    public java.util.Optional<Resident> findActiveResident(UUID unitId, UUID residentId) {
        return residents.findByIdAndUnitIdAndDeletedAtIsNull(residentId, unitId);
    }

    /**
     * F6 (`report`): morador por id, independente de unidade/status ativo — só
     * para exibição (telefone em `AdminReportDto`); o report já grava o nome
     * num snapshot e a checagem de "morador ativo da própria unidade" é feita
     * na criação, não aqui.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Resident> findResidentById(UUID residentId) {
        return residents.findById(residentId);
    }

    @Transactional(readOnly = true)
    public Page<Unit> search(UUID condominiumId, String search, String block, Pageable pageable) {
        return units.search(condominiumId, blankToNull(search), blankToNull(block), pageable);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<Resident>> residentsByUnits(List<UUID> unitIds) {
        return residents.findByUnitIdInAndDeletedAtIsNull(unitIds).stream()
            .collect(Collectors.groupingBy(Resident::getUnitId));
    }

    /** `PUT /units/{id}` (D-43): substitui a lista de moradores; bloco/número não mudam. */
    @Transactional
    public List<Resident> replaceResidents(UUID unitId, UUID actorId, List<ResidentInput> residentInputs) {
        findActiveUnitOrThrow(unitId);
        validateResidentInputs(residentInputs);

        List<Resident> current = residents.findByUnitIdAndDeletedAtIsNull(unitId);
        Map<UUID, Resident> currentById = current.stream()
            .collect(Collectors.toMap(Resident::getId, r -> r));
        Instant now = Instant.now(clock);

        List<ResidentInput> toCreate = new java.util.ArrayList<>();
        for (ResidentInput input : residentInputs) {
            if (input.id() != null && currentById.containsKey(input.id())) {
                Resident resident = currentById.remove(input.id());
                resident.updateContact(input.name(), input.phone(), input.email());
                resident.setPrimary(input.primary());
                if (input.cpf() != null) {
                    resident.setCpf(Cpf.validate(input.cpf()));
                }
            } else {
                toCreate.add(input);
            }
        }
        // Moradores ativos que não vieram na lista são removidos (soft delete, RN-10); precisa
        // estar na base antes dos inserts, senão a unicidade parcial de CPF por unidade pode
        // disparar num CPF reaproveitado na mesma chamada (mesmo cuidado do transfer, RN-08).
        currentById.values().forEach(resident -> resident.softDelete(now));
        residents.saveAllAndFlush(current);
        for (ResidentInput input : toCreate) {
            residents.save(new Resident(unitId, input.name(), input.phone(), input.email(),
                input.cpf() == null ? null : Cpf.validate(input.cpf()), input.primary()));
        }

        audit.record(actorId, "ADMIN", "UNIT_UPDATE_RESIDENTS", "UNIT", unitId, Map.of(), null);
        return residents.findByUnitIdAndDeletedAtIsNull(unitId);
    }

    /** D-47 + P-11: desativa a unidade e a conta; identificador fica livre para recadastro. */
    @Transactional
    public void deactivate(UUID unitId, UUID actorId) {
        Unit unit = findActiveUnitOrThrow(unitId);
        requireNoFutureReservations(unit);

        Instant now = Instant.now(clock);
        unit.deactivate(now);
        units.save(unit);

        accounts.findAccountIdByUnit(unitId).ifPresent(accountId -> {
            String freedUsername = unit.getIdentifier() + "~" + unitId.toString().substring(0, 8);
            accounts.deactivateUnitAccount(accountId, freedUsername);
        });

        audit.record(actorId, "ADMIN", "UNIT_DEACTIVATE", "UNIT", unitId, Map.of(), null);
    }

    /** `POST /units/{id}/reset-password`. */
    @Transactional
    public Credentials resetPassword(UUID unitId, UUID actorId) {
        Unit unit = findActiveUnitOrThrow(unitId);
        UUID accountId = accounts.findAccountIdByUnit(unitId)
            .orElseThrow(() -> new EntityNotFoundException("Conta da unidade não encontrada."));
        String tempPassword = accounts.resetPassword(accountId);

        audit.record(actorId, "ADMIN", "UNIT_RESET_PASSWORD", "UNIT", unitId, Map.of(), null);
        return new Credentials(unit.getIdentifier(), tempPassword);
    }

    /** RN-10: troca de titularidade. Reservas futuras ficam so listadas (o ADMIN cancela pela rota de reservas). */
    @Transactional
    public TransferResult transfer(UUID unitId, UUID actorId, List<ResidentInput> newResidentInputs) {
        Unit unit = findActiveUnitOrThrow(unitId);
        validateResidentInputs(newResidentInputs);

        Instant now = Instant.now(clock);
        List<Resident> oldResidents = residents.findByUnitIdAndDeletedAtIsNull(unitId);
        oldResidents.forEach(resident -> resident.softDelete(now));
        // RN-08: precisa estar na base antes do insert dos novos, senão a unicidade parcial
        // (unit_id, cpf) WHERE deleted_at IS NULL pode disparar num CPF reaproveitado na troca.
        residents.saveAllAndFlush(oldResidents);
        List<Resident> saved = saveResidents(unitId, newResidentInputs);

        UUID accountId = accounts.findAccountIdByUnit(unitId)
            .orElseThrow(() -> new EntityNotFoundException("Conta da unidade não encontrada."));
        String tempPassword = accounts.resetPassword(accountId);
        List<ReservationSummary> affected = futureReservations.upcomingActiveByUnit(unitId, unit.getIdentifier());

        audit.record(actorId, "ADMIN", "UNIT_TRANSFER", "UNIT", unitId, Map.of(), null);
        return new TransferResult(saved, new Credentials(unit.getIdentifier(), tempPassword), affected);
    }

    private void requireNoFutureReservations(Unit unit) {
        List<ReservationSummary> affected = futureReservations.upcomingActiveByUnit(unit.getId(),
            unit.getIdentifier());
        if (!affected.isEmpty()) {
            throw new UnitHasFutureReservationsException(affected);
        }
    }

    private List<Resident> saveResidents(UUID unitId, List<ResidentInput> inputs) {
        return inputs.stream()
            .map(input -> residents.save(new Resident(unitId, input.name(), input.phone(), input.email(),
                input.cpf() == null ? null : Cpf.validate(input.cpf()), input.primary())))
            .toList();
    }

    // RN-07/RN-08: exatamente 1 principal, CPF sem duplicidade na mesma unidade (checagem prévia a inserir;
    // a unicidade parcial de resident.cpf no banco é a garantia final).
    private void validateResidentInputs(List<ResidentInput> inputs) {
        long primaryCount = inputs.stream().filter(ResidentInput::primary).count();
        if (primaryCount != 1) {
            throw new BusinessException("PRIMARY_RESIDENT_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY,
                "A unidade precisa de exatamente um morador principal.");
        }
        ResidentInput primary = inputs.stream().filter(ResidentInput::primary).findFirst().orElseThrow();
        if (primary.email() == null || primary.email().isBlank() || primary.cpf() == null
            || primary.cpf().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY,
                "O morador principal precisa de e-mail e CPF.");
        }
        List<String> cpfs = inputs.stream()
            .map(ResidentInput::cpf)
            .filter(cpf -> cpf != null && !cpf.isBlank())
            .map(Cpf::validate)
            .toList();
        if (cpfs.size() != cpfs.stream().distinct().count()) {
            throw new BusinessException("DUPLICATE_CPF_IN_UNIT", HttpStatus.UNPROCESSABLE_ENTITY,
                "O mesmo CPF não pode aparecer duas vezes na mesma unidade.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

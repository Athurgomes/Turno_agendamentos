package br.com.reservas.unit.application;

import br.com.reservas.shared.error.BusinessException;
import br.com.reservas.unit.domain.Cpf;
import br.com.reservas.unit.domain.Resident;
import br.com.reservas.unit.domain.Unit;
import br.com.reservas.unit.infra.ResidentRepository;
import br.com.reservas.unit.infra.UnitRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `/me/unit` (RF-UNI-05, RN-09): autoatendimento da conta UNIT sobre a
 * própria unidade. O {@code unitId} sempre vem de {@code CurrentUser} (nunca
 * de path/corpo, RN-01); todo método aqui checa que o recurso pertence a essa
 * unidade antes de qualquer alteração (proteção contra IDOR).
 */
@Service
public class MyUnitService {

    private final UnitRepository units;
    private final ResidentRepository residents;
    private final Clock clock;

    public MyUnitService(UnitRepository units, ResidentRepository residents, Clock clock) {
        this.units = units;
        this.residents = residents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Unit getUnit(UUID unitId) {
        return units.findByIdAndDeletedAtIsNull(unitId)
            .orElseThrow(() -> new EntityNotFoundException("Unidade não encontrada: " + unitId));
    }

    @Transactional(readOnly = true)
    public List<Resident> activeResidents(UUID unitId) {
        return residents.findByUnitIdAndDeletedAtIsNull(unitId);
    }

    /** RN-09: adiciona morador (`primary = false`); CPF opcional, mas se informado precisa ser válido e único. */
    @Transactional
    public Resident addResident(UUID unitId, String name, String phone, String email, String rawCpf) {
        String cpf = rawCpf == null || rawCpf.isBlank() ? null : Cpf.validate(rawCpf);
        if (cpf != null && residents.existsByUnitIdAndCpfAndDeletedAtIsNull(unitId, cpf)) {
            throw new BusinessException("DUPLICATE_CPF_IN_UNIT", HttpStatus.UNPROCESSABLE_ENTITY,
                "O mesmo CPF não pode aparecer duas vezes na mesma unidade.");
        }
        return residents.save(new Resident(unitId, name, phone, email, cpf, false));
    }

    /** RN-09: edita nome/telefone/e-mail de qualquer morador da própria unidade; CPF nunca muda por aqui. */
    @Transactional
    public Resident updateResident(UUID unitId, UUID residentId, String name, String phone, String email) {
        Resident resident = findOwnResidentOrThrow(unitId, residentId);
        if (resident.isPrimary() && (email == null || email.isBlank())) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY,
                "O e-mail do morador principal é obrigatório.");
        }
        resident.updateContact(name, phone, email);
        return resident;
    }

    /** RN-09: remove morador adicional; o principal nunca pode ser removido pela conta UNIT. */
    @Transactional
    public void removeResident(UUID unitId, UUID residentId) {
        Resident resident = findOwnResidentOrThrow(unitId, residentId);
        if (resident.isPrimary()) {
            throw new BusinessException("CANNOT_REMOVE_PRIMARY", HttpStatus.UNPROCESSABLE_ENTITY,
                "O morador principal não pode ser removido.");
        }
        resident.softDelete(Instant.now(clock));
    }

    // RN-01: morador de outra unidade (ou inexistente/já removido) -> 403 FORBIDDEN_RESOURCE, nunca 404
    // (não confirma nem nega a existência do recurso para quem não é dono dele).
    private Resident findOwnResidentOrThrow(UUID unitId, UUID residentId) {
        return residents.findByIdAndUnitIdAndDeletedAtIsNull(residentId, unitId)
            .orElseThrow(() -> new BusinessException("FORBIDDEN_RESOURCE", HttpStatus.FORBIDDEN,
                "Você não tem acesso a este morador."));
    }
}

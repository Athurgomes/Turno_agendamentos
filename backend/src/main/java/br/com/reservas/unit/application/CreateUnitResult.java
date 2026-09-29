package br.com.reservas.unit.application;

import br.com.reservas.unit.domain.Resident;
import br.com.reservas.unit.domain.Unit;
import java.util.List;

public record CreateUnitResult(Unit unit, List<Resident> residents, Credentials credentials) {
}

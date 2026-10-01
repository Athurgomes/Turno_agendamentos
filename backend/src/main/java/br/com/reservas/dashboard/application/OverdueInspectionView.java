package br.com.reservas.dashboard.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Item de `overdueInspections` em `GET /dashboard/home` (F7-1, RF-SIN-01):
 * área não excluída sem vistoria (ambos os campos `null`) ou com a última
 * vistoria há mais de 30 dias.
 */
public record OverdueInspectionView(UUID areaId, String areaName, String status, LocalDate lastInspectionAt,
    Long daysSinceInspection) {
}

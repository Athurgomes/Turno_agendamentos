package br.com.reservas.dashboard.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** `GET /dashboard/summary` (RF-DAS-01/02, docs/03 "Dashboard e exportação"). */
public record DashboardSummaryView(LocalDate from, LocalDate to, long activeUnits, long activeResidents,
    Reservations reservations, Cancellations cancellations, Amounts amounts, Reports reports,
    BigDecimal maintenanceCost) {

    public record Reservations(long total, long pendingPayment, long confirmed, long cancelled) {
    }

    public record Cancellations(long byResident, long byAdmin, long bySystem, BigDecimal residentRate,
        BigDecimal adminRate) {
    }

    public record Amounts(BigDecimal confirmed, BigDecimal pending) {
    }

    public record Reports(long opened, long resolved, long open, BigDecimal averageResolutionHours,
        List<CategoryCount> byCategory) {
    }

    public record CategoryCount(String category, long count) {
    }
}

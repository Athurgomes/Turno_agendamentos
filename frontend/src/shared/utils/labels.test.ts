import { describe, expect, it } from "vitest";
import {
  areaCategoryLabels,
  areaStatusLabels,
  cancelledByLabels,
  inspectionConditionLabels,
  reportCategoryLabels,
  reportStatusLabels,
  reservationEventTypeLabels,
  reservationKindLabels,
  reservationStatusLabel,
  reservationStatusLabels,
  roleLabels,
  weekdayLabels,
} from "./labels";
import type {
  AreaCategory,
  AreaStatus,
  CancelledBy,
  InspectionCondition,
  IsoWeekday,
  ReportCategory,
  ReportStatus,
  ReservationEventType,
  ReservationKind,
  ReservationStatus,
  Role,
} from "../api/types";

function expectAllKeysLabeled<K extends string | number>(
  keys: readonly K[],
  labels: Record<K, string>,
) {
  for (const key of keys) {
    expect(labels[key], `rótulo faltando para ${key}`).toBeTruthy();
  }
}

describe("labels pt-BR", () => {
  it("Role tem rótulo para todos os valores", () => {
    const roles: Role[] = ["ADMIN", "SYNDIC", "UNIT"];
    expectAllKeysLabeled(roles, roleLabels);
    expect(roleLabels.UNIT).toBe("Morador");
  });

  it("AreaCategory tem rótulo para todos os valores (RN-13)", () => {
    const categories: AreaCategory[] = [
      "PARTY_ROOM",
      "BARBECUE",
      "GOURMET_SPACE",
      "POOL",
      "SPORTS_COURT",
      "TENNIS_COURT",
      "GYM",
      "GAME_ROOM",
      "TOY_ROOM",
      "PLAYGROUND",
      "SAUNA",
      "CINEMA",
      "COWORKING",
      "PET_PLACE",
      "OTHER",
    ];
    expectAllKeysLabeled(categories, areaCategoryLabels);
  });

  it("AreaStatus tem rótulo para todos os valores (RN-14)", () => {
    const statuses: AreaStatus[] = [
      "ACTIVE",
      "MAINTENANCE",
      "RENOVATION",
      "INTERDICTED",
      "INACTIVE",
    ];
    expectAllKeysLabeled(statuses, areaStatusLabels);
    expect(areaStatusLabels.ACTIVE).toBe("Disponível");
  });

  it("ReservationKind e ReservationStatus têm rótulo completo", () => {
    const kinds: ReservationKind[] = ["BOOKING", "BLOCK"];
    const statuses: ReservationStatus[] = [
      "PENDING_PAYMENT",
      "CONFIRMED",
      "CANCELLED",
    ];
    expectAllKeysLabeled(kinds, reservationKindLabels);
    expectAllKeysLabeled(statuses, reservationStatusLabels);
  });

  it("reservationStatusLabel deriva 'Realizada' quando CONFIRMED já passou (RN-32)", () => {
    const now = new Date("2026-09-28T12:00:00Z");
    const past = new Date("2026-09-01T12:00:00Z");
    const future = new Date("2026-10-01T12:00:00Z");
    expect(reservationStatusLabel("CONFIRMED", past, now)).toBe("Realizada");
    expect(reservationStatusLabel("CONFIRMED", future, now)).toBe(
      "Confirmada",
    );
  });

  it("ReservationEventType tem rótulo completo, incluindo EXPIRED (RN-31)", () => {
    const values: ReservationEventType[] = [
      "CREATED",
      "UPDATED",
      "CANCELLED",
      "PAYMENT_CONFIRMED",
      "EXPIRED",
    ];
    expectAllKeysLabeled(values, reservationEventTypeLabels);
    expect(reservationEventTypeLabels.EXPIRED).toBe("Expirada");
  });

  it("CancelledBy tem rótulo completo", () => {
    const values: CancelledBy[] = ["RESIDENT", "ADMIN", "SYSTEM"];
    expectAllKeysLabeled(values, cancelledByLabels);
  });

  it("ReportCategory tem rótulo completo (RN-35)", () => {
    const values: ReportCategory[] = [
      "DAMAGE",
      "MALFUNCTION",
      "CLEANLINESS",
      "SAFETY",
      "MISSING_ITEM",
      "OTHER",
    ];
    expectAllKeysLabeled(values, reportCategoryLabels);
  });

  it("ReportStatus tem rótulo completo (RN-36)", () => {
    const values: ReportStatus[] = [
      "OPEN",
      "IN_REVIEW",
      "IN_MAINTENANCE",
      "RESOLVED",
      "DISMISSED",
    ];
    expectAllKeysLabeled(values, reportStatusLabels);
  });

  it("InspectionCondition usa Bom/Regular/Ruim", () => {
    const values: InspectionCondition[] = ["GOOD", "FAIR", "POOR"];
    expectAllKeysLabeled(values, inspectionConditionLabels);
    expect(inspectionConditionLabels.GOOD).toBe("Bom");
    expect(inspectionConditionLabels.FAIR).toBe("Regular");
    expect(inspectionConditionLabels.POOR).toBe("Ruim");
  });

  it("dias da semana ISO 1-7 têm rótulo completo", () => {
    const days: IsoWeekday[] = [1, 2, 3, 4, 5, 6, 7];
    expectAllKeysLabeled(days, weekdayLabels);
    expect(weekdayLabels[1]).toBe("Segunda-feira");
    expect(weekdayLabels[7]).toBe("Domingo");
  });
});

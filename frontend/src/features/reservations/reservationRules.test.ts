import { describe, expect, it } from "vitest";
import { notBookableReasonText, stepForErrorCode } from "./reservationRules";
import type { PublicSettingsDto } from "../../shared/api/types";

const settings: PublicSettingsDto = {
  condominiumName: "Condomínio Exemplo",
  timezone: "America/Sao_Paulo",
  minAdvanceDays: 1,
  nextDayWindowStart: "06:00",
  nextDayWindowEnd: "16:00",
  maxAdvanceDays: 60,
  maxActiveBookingsPerUnit: 3,
  residentCancelDeadlineHours: 24,
  slotMinutes: 30,
  reportWindowDays: 7,
};

describe("notBookableReasonText (RN-20)", () => {
  it("traduz NEXT_DAY_WINDOW_CLOSED usando os parâmetros de /settings/public", () => {
    expect(notBookableReasonText("NEXT_DAY_WINDOW_CLOSED", settings)).toBe(
      "Reservas para amanhã só podem ser feitas entre 06:00 e 16:00.",
    );
  });

  it("traduz TOO_FAR_AHEAD usando maxAdvanceDays", () => {
    expect(notBookableReasonText("TOO_FAR_AHEAD", settings)).toBe(
      "Reservas podem ser feitas com até 60 dias de antecedência.",
    );
  });

  it("traduz SAME_DAY_NOT_ALLOWED", () => {
    expect(notBookableReasonText("SAME_DAY_NOT_ALLOWED", settings)).toBe(
      "Não é possível reservar para o mesmo dia.",
    );
  });
});

describe("stepForErrorCode (RF-RES-03)", () => {
  it("RESERVATION_OVERLAP volta para o passo de horário", () => {
    expect(stepForErrorCode("RESERVATION_OVERLAP")).toBe("time");
  });

  it("UNIT_BOOKING_LIMIT_REACHED fica na revisão (não há campo a corrigir)", () => {
    expect(stepForErrorCode("UNIT_BOOKING_LIMIT_REACHED")).toBe("review");
  });

  it("CAPACITY_EXCEEDED volta para o passo de detalhes", () => {
    expect(stepForErrorCode("CAPACITY_EXCEEDED")).toBe("details");
  });

  it("SAME_DAY_NOT_ALLOWED volta para o passo de data", () => {
    expect(stepForErrorCode("SAME_DAY_NOT_ALLOWED")).toBe("date");
  });
});

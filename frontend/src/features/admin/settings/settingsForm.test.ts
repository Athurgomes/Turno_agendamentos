import { describe, expect, it } from "vitest";
import { settingsFormSchema, settingsToFormValues } from "./settingsForm";
import type { SettingsDto } from "../../../shared/api/types";

/**
 * Fixture igual ao JSON real devolvido por `GET /admin/settings`
 * (backend serializa `LocalTime` com segundos: "06:00:00", não "06:00" —
 * confirmado via curl em 2026-09-28). Ver divergência reportada ao
 * orquestrador (docs/03 promete "HH:mm").
 */
const realApiSettings: SettingsDto = {
  condominiumName: "Residencial Exemplo",
  timezone: "America/Sao_Paulo",
  defaultPaymentWhatsapp: "5511900000000",
  minAdvanceDays: 1,
  nextDayWindowStart: "06:00:00",
  nextDayWindowEnd: "16:00:00",
  maxAdvanceDays: 60,
  maxActiveBookingsPerUnit: 3,
  residentCancelDeadlineHours: 24,
  slotMinutes: 30,
  reportWindowDays: 7,
};

describe("settingsToFormValues", () => {
  it("normaliza HH:mm:ss (formato real da API) para HH:mm aceito pelo formulário", () => {
    const values = settingsToFormValues(realApiSettings);
    expect(values.nextDayWindowStart).toBe("06:00");
    expect(values.nextDayWindowEnd).toBe("16:00");
  });

  it("o resultado passa na validação do schema (regex HH:mm)", () => {
    const values = settingsToFormValues(realApiSettings);
    const result = settingsFormSchema.safeParse(values);
    expect(result.success).toBe(true);
  });
});

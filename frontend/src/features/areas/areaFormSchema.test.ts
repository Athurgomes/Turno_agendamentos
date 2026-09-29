import { describe, expect, it } from "vitest";
import { areaFormSchema, scheduleFromOpeningHours } from "./areaFormSchema";
import type { OpeningHoursDto } from "../../shared/api/types";
import { defaultSchedule } from "./areaFormSchema";

/**
 * Fixture igual ao JSON real de `GET /areas/{id}` — backend serializa
 * `LocalTime` com segundos ("08:00:00", não "08:00" como docs/03 promete).
 * Confirmado via curl em 2026-09-28; divergência reportada ao orquestrador.
 */
const realOpeningHours: OpeningHoursDto[] = [
  { dayOfWeek: 1, openTime: "08:00:00", closeTime: "22:00:00" },
];

describe("scheduleFromOpeningHours", () => {
  it("normaliza HH:mm:ss (formato real da API) para HH:mm aceito pelo formulário", () => {
    const schedule = scheduleFromOpeningHours(realOpeningHours);
    const monday = schedule.find((day) => day.dayOfWeek === 1)!;
    expect(monday.openTime).toBe("08:00");
    expect(monday.closeTime).toBe("22:00");
  });

  it("o resultado passa na validação do schema (regex HH:mm de 30 em 30 min)", () => {
    const schedule = scheduleFromOpeningHours(realOpeningHours);
    const values = {
      name: "Área",
      category: "POOL",
      description: "desc",
      rules: "regras",
      conductGuidelines: "conduta",
      capacity: 10,
      requiresPayment: false,
      price: "",
      paymentWhatsapp: "",
      schedule: schedule.map((d, i) => (i === 0 ? d : defaultSchedule()[i])),
    };
    const result = areaFormSchema.safeParse(values);
    expect(result.success).toBe(true);
  });
});

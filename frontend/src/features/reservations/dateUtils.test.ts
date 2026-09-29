import { describe, expect, it } from "vitest";
import { addDays, addMonths, dateKeyInTimezone, daysInMonth, isoWeekday, monthKey } from "./dateUtils";

describe("dateUtils", () => {
  it("dateKeyInTimezone converte o instante do servidor para a data no fuso do condomínio (D-32)", () => {
    // 2026-09-28T02:30:00Z é 2026-09-27 23:30 em America/Sao_Paulo (UTC-3)
    expect(dateKeyInTimezone(new Date("2026-09-28T02:30:00Z"), "America/Sao_Paulo")).toBe(
      "2026-09-27",
    );
  });

  it("addDays soma dias sem deslocar por fuso local", () => {
    expect(addDays("2026-09-28", 1)).toBe("2026-09-29");
    expect(addDays("2026-01-31", 1)).toBe("2026-02-01");
  });

  it("addMonths vai para o primeiro dia do mês seguinte", () => {
    expect(addMonths("2026-09-15", 1)).toBe("2026-10-01");
    expect(addMonths("2026-12-15", 1)).toBe("2027-01-01");
  });

  it("daysInMonth lista todos os dias do mês em ordem", () => {
    const days = daysInMonth("2026-02-10");
    expect(days[0]).toBe("2026-02-01");
    expect(days[days.length - 1]).toBe("2026-02-28");
  });

  it("isoWeekday: 1 = segunda … 7 = domingo", () => {
    expect(isoWeekday("2026-09-28")).toBe(1); // segunda
    expect(isoWeekday("2026-10-04")).toBe(7); // domingo
  });

  it("monthKey extrai YYYY-MM", () => {
    expect(monthKey("2026-09-28")).toBe("2026-09");
  });
});

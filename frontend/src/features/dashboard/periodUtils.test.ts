import { describe, expect, it } from "vitest";
import { monthRange, periodPresets } from "./periodUtils";

describe("periodUtils (F8-3)", () => {
  it("monthRange devolve o primeiro e o último dia do mês", () => {
    expect(monthRange("2026-09-15")).toEqual({ from: "2026-09-01", to: "2026-09-30" });
    expect(monthRange("2026-02-01")).toEqual({ from: "2026-02-01", to: "2026-02-28" });
  });

  it("periodPresets calcula mês atual, mês anterior, últimos 3 e últimos 6 meses a partir de 'hoje'", () => {
    const presets = periodPresets("2026-09-29");
    expect(presets).toEqual([
      { label: "Mês atual", period: { from: "2026-09-01", to: "2026-09-30" } },
      { label: "Mês anterior", period: { from: "2026-08-01", to: "2026-08-31" } },
      { label: "Últimos 3 meses", period: { from: "2026-07-01", to: "2026-09-30" } },
      { label: "Últimos 6 meses", period: { from: "2026-04-01", to: "2026-09-30" } },
    ]);
  });
});

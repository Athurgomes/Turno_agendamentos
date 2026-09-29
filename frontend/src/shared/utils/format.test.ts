import { describe, expect, it } from "vitest";
import { formatCurrency, formatDate, formatDateTimeInstant, formatPhone, formatTime } from "./format";

describe("formatDate", () => {
  it("interpreta `YYYY-MM-DD` em UTC, sem deslocar de fuso (data de agenda)", () => {
    expect(formatDate("2026-11-10")).toBe("10/11/2026");
  });
});

describe("formatTime", () => {
  it("corta `HH:mm:ss` para `HH:mm`", () => {
    expect(formatTime("23:30:00")).toBe("23:30");
  });
});

describe("formatDateTimeInstant", () => {
  it("converte um instante ISO (UTC) para o fuso do condomínio (RNF-10)", () => {
    // 2026-11-10T02:30:00Z em America/Sao_Paulo (UTC-3) é 09/11/2026 23:30.
    expect(formatDateTimeInstant("2026-11-10T02:30:00Z", "America/Sao_Paulo")).toBe(
      "09/11/2026 23:30",
    );
  });

  it("usa America/Sao_Paulo por default quando o fuso do condomínio ainda não carregou", () => {
    expect(formatDateTimeInstant("2026-11-10T02:30:00Z")).toBe("09/11/2026 23:30");
  });

  it("aceita outro fuso (ex.: condomínio fora de America/Sao_Paulo)", () => {
    expect(formatDateTimeInstant("2026-11-10T02:30:00Z", "America/Manaus")).toBe(
      "09/11/2026 22:30",
    );
  });
});

describe("formatCurrency", () => {
  it("formata número como moeda pt-BR", () => {
    expect(formatCurrency(150)).toMatch(/^R\$\s?150,00$/);
  });
});

describe("formatPhone", () => {
  it("formata telefone só dígitos com DDI", () => {
    expect(formatPhone("5562999998888")).toBe("+55 (62) 99999-8888");
  });
});

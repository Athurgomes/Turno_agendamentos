import { describe, expect, it } from "vitest";
import { isValidCpf, maskCpf } from "./cpf";

describe("isValidCpf", () => {
  it("aceita CPF válido (dígitos verificadores corretos), com ou sem máscara", () => {
    expect(isValidCpf("52998224725")).toBe(true);
    expect(isValidCpf("529.982.247-25")).toBe(true);
  });

  it("rejeita CPF com dígito verificador errado", () => {
    expect(isValidCpf("52998224700")).toBe(false);
  });

  it("rejeita CPF com todos os dígitos iguais", () => {
    expect(isValidCpf("11111111111")).toBe(false);
  });

  it("rejeita entrada com quantidade de dígitos diferente de 11", () => {
    expect(isValidCpf("123456789")).toBe(false);
    expect(isValidCpf("")).toBe(false);
  });
});

describe("maskCpf", () => {
  it("formata 11 dígitos como 000.000.000-00", () => {
    expect(maskCpf("52998224725")).toBe("529.982.247-25");
  });

  it("devolve a entrada original quando não tem 11 dígitos", () => {
    expect(maskCpf("123")).toBe("123");
  });
});

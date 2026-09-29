import { describe, expect, it } from "vitest";
import { isFinalReportStatus, nextReportStatusOptions } from "./reportRules";

describe("reportRules (RN-36)", () => {
  it("de OPEN oferece todos os status seguintes mais DISMISSED", () => {
    expect(nextReportStatusOptions("OPEN")).toEqual([
      "IN_REVIEW",
      "IN_MAINTENANCE",
      "RESOLVED",
      "DISMISSED",
    ]);
  });

  it("permite pular etapas para frente (IN_REVIEW → RESOLVED)", () => {
    expect(nextReportStatusOptions("IN_REVIEW")).toEqual(["IN_MAINTENANCE", "RESOLVED", "DISMISSED"]);
  });

  it("de IN_MAINTENANCE só oferece RESOLVED e DISMISSED", () => {
    expect(nextReportStatusOptions("IN_MAINTENANCE")).toEqual(["RESOLVED", "DISMISSED"]);
  });

  it("RESOLVED é final: nenhuma opção de transição", () => {
    expect(nextReportStatusOptions("RESOLVED")).toEqual([]);
    expect(isFinalReportStatus("RESOLVED")).toBe(true);
  });

  it("DISMISSED é final: nenhuma opção de transição", () => {
    expect(nextReportStatusOptions("DISMISSED")).toEqual([]);
    expect(isFinalReportStatus("DISMISSED")).toBe(true);
  });

  it("nunca oferece voltar para um status anterior", () => {
    expect(nextReportStatusOptions("IN_MAINTENANCE")).not.toContain("OPEN");
    expect(nextReportStatusOptions("IN_MAINTENANCE")).not.toContain("IN_REVIEW");
  });
});

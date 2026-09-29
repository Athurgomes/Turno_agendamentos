import { describe, expect, it } from "vitest";
import { blockIndexAt, buildTimeBlocks } from "./timeUtils";

describe("buildTimeBlocks (RN-19)", () => {
  it("gera blocos de 30 min entre openTime e closeTime", () => {
    const blocks = buildTimeBlocks("08:00", "09:00", 30, []);
    expect(blocks).toEqual([
      { startTime: "08:00", endTime: "08:30", busy: false },
      { startTime: "08:30", endTime: "09:00", busy: false },
    ]);
  });

  it("marca como ocupado qualquer bloco que cruze um intervalo busy", () => {
    const blocks = buildTimeBlocks("08:00", "10:00", 30, [
      { startTime: "08:30", endTime: "09:00", kind: "BOOKING" },
    ]);
    expect(blocks.map((b) => b.busy)).toEqual([false, true, false, false]);
  });

  it("intervalo [início, fim) — 09:00 não conflita com busy 08:00–09:00 (RN-24)", () => {
    const blocks = buildTimeBlocks("08:00", "10:00", 30, [
      { startTime: "08:00", endTime: "09:00", kind: "BOOKING" },
    ]);
    const nineOClock = blockIndexAt(blocks, "09:00");
    expect(blocks[nineOClock].busy).toBe(false);
  });

  it("blockIndexAt encontra o índice pelo horário de início", () => {
    const blocks = buildTimeBlocks("08:00", "09:00", 30, []);
    expect(blockIndexAt(blocks, "08:30")).toBe(1);
    expect(blockIndexAt(blocks, "23:00")).toBe(-1);
  });
});

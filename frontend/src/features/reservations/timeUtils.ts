/** Grade de horários de 30 em 30 min (RN-19) a partir de `openTime`/`closeTime` e dos intervalos ocupados. */
import type { AvailabilityBusySlot } from "../../shared/api/types";

export interface TimeBlock {
  startTime: string;
  endTime: string;
  busy: boolean;
}

function toMinutes(hhmm: string): number {
  const [hours, minutes] = hhmm.split(":").map(Number);
  return hours * 60 + minutes;
}

function toHHMM(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
}

/** Um bloco por `slotMinutes` entre `openTime` (inclusive) e `closeTime` (exclusive); `busy` quando cruza algum intervalo ocupado. */
export function buildTimeBlocks(
  openTime: string,
  closeTime: string,
  slotMinutes: number,
  busy: AvailabilityBusySlot[],
): TimeBlock[] {
  const busyRanges = busy.map((slot) => ({
    start: toMinutes(slot.startTime),
    end: toMinutes(slot.endTime),
  }));
  const blocks: TimeBlock[] = [];
  for (let start = toMinutes(openTime); start < toMinutes(closeTime); start += slotMinutes) {
    const end = start + slotMinutes;
    const isBusy = busyRanges.some((range) => start < range.end && end > range.start);
    blocks.push({ startTime: toHHMM(start), endTime: toHHMM(end), busy: isBusy });
  }
  return blocks;
}

/** Índice do bloco cujo `startTime` é `time`, ou -1. */
export function blockIndexAt(blocks: TimeBlock[], time: string): number {
  return blocks.findIndex((block) => block.startTime === time);
}

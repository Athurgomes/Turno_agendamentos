/**
 * Passo 2 — grade de horários de 30 min (RN-19, RNF-05). Primeiro toque num
 * bloco livre escolhe início e fim (um bloco); um segundo toque num bloco
 * livre mais adiante, sem nenhum bloco ocupado entre os dois, estende o fim.
 * Blocos ocupados (`busy`) não são selecionáveis.
 */
import { useState } from "react";
import { blockIndexAt, buildTimeBlocks } from "./timeUtils";
import type { AvailabilityDay } from "../../shared/api/types";

export interface TimeSlotPickerProps {
  day: AvailabilityDay;
  slotMinutes: number;
  startTime: string | null;
  endTime: string | null;
  onSelect: (startTime: string, endTime: string) => void;
}

export function TimeSlotPicker({ day, slotMinutes, startTime, endTime, onSelect }: TimeSlotPickerProps) {
  const [anchor, setAnchor] = useState<string | null>(startTime);
  const blocks = buildTimeBlocks(day.openTime ?? "00:00", day.closeTime ?? "00:00", slotMinutes, day.busy);

  if (blocks.length === 0) {
    return <p className="text-sm text-neutral-600">Esta área não abre neste dia.</p>;
  }

  function handleClick(blockStart: string) {
    const clickedIndex = blockIndexAt(blocks, blockStart);
    const anchorIndex = anchor ? blockIndexAt(blocks, anchor) : -1;

    if (anchorIndex >= 0 && clickedIndex > anchorIndex) {
      const rangeHasBusy = blocks.slice(anchorIndex, clickedIndex + 1).some((block) => block.busy);
      if (!rangeHasBusy) {
        onSelect(blocks[anchorIndex].startTime, blocks[clickedIndex].endTime);
        return;
      }
    }

    // Novo início (primeiro toque, toque num bloco anterior ao início atual, ou faixa com bloco ocupado no meio).
    setAnchor(blockStart);
    onSelect(blocks[clickedIndex].startTime, blocks[clickedIndex].endTime);
  }

  const anchorIndex = anchor ? blockIndexAt(blocks, anchor) : -1;
  const endIndex = endTime ? blocks.findIndex((block) => block.endTime === endTime) : -1;

  return (
    <div>
      <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
        {blocks.map((block, index) => {
          const selected = anchorIndex >= 0 && endIndex >= 0 && index >= anchorIndex && index <= endIndex;
          return (
            <button
              key={block.startTime}
              type="button"
              disabled={block.busy}
              aria-pressed={selected}
              onClick={() => handleClick(block.startTime)}
              className={[
                "min-h-11 rounded-sm border px-2 py-2 text-sm",
                block.busy
                  ? "cursor-not-allowed border-neutral-200 bg-neutral-100 text-neutral-400"
                  : selected
                    ? "border-primary-600 bg-primary-600 font-semibold text-white"
                    : "border-neutral-300 bg-neutral-0 text-neutral-900 hover:bg-primary-100",
              ].join(" ")}
            >
              {block.startTime}
              {block.busy && <span className="block text-[10px] leading-none">Indisponível</span>}
            </button>
          );
        })}
      </div>
      {startTime && endTime && (
        <p className="mt-3 text-sm text-neutral-700">
          Horário escolhido: <strong>{startTime}</strong> às <strong>{endTime}</strong>
        </p>
      )}
    </div>
  );
}

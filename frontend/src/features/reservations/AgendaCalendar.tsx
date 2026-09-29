/**
 * Agenda em calendário (RF-SIN-02, RNF-05): grade de semana ou mês. Cada dia
 * mostra quantas reservas e bloqueios têm início nele; tocar um dia filtra a
 * lista abaixo para aquele dia. Reaproveita a aritmética de `dateUtils`
 * (mesma usada no calendário de disponibilidade do morador).
 */
import { addDays, daysInMonth, isoWeekday, monthKey } from "./dateUtils";
import { weekdayShortLabels } from "../../shared/utils/labels";
import type { AdminReservationDto } from "../../shared/api/types";

const MONTH_LABEL_FORMATTER = new Intl.DateTimeFormat("pt-BR", {
  month: "long",
  year: "numeric",
  timeZone: "UTC",
});

export type CalendarRangeMode = "week" | "month";

export interface AgendaCalendarProps {
  rangeMode: CalendarRangeMode;
  anchorDate: string;
  onAnchorChange: (date: string) => void;
  today: string;
  items: AdminReservationDto[];
  selectedDate: string | null;
  onSelectDate: (date: string) => void;
}

function weekDates(anchorDate: string): string[] {
  const mondayOffset = isoWeekday(anchorDate) - 1;
  const monday = addDays(anchorDate, -mondayOffset);
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i));
}

export function AgendaCalendar({
  rangeMode,
  anchorDate,
  onAnchorChange,
  today,
  items,
  selectedDate,
  onSelectDate,
}: AgendaCalendarProps) {
  const dates = rangeMode === "week" ? weekDates(anchorDate) : daysInMonth(anchorDate);
  const leadingBlanks = rangeMode === "month" ? isoWeekday(dates[0]) - 1 : 0;

  const byDate = new Map<string, { bookings: number; blocks: number }>();
  for (const item of items) {
    const entry = byDate.get(item.date) ?? { bookings: 0, blocks: 0 };
    if (item.kind === "BLOCK") entry.blocks += 1;
    else entry.bookings += 1;
    byDate.set(item.date, entry);
  }

  function shiftMonth(date: string, months: number): string {
    const d = new Date(`${date}T00:00:00Z`);
    d.setUTCMonth(d.getUTCMonth() + months, 1);
    return d.toISOString().slice(0, 10);
  }

  function navigate(step: number) {
    onAnchorChange(rangeMode === "week" ? addDays(anchorDate, 7 * step) : shiftMonth(anchorDate, step));
  }

  const label =
    rangeMode === "month"
      ? MONTH_LABEL_FORMATTER.format(new Date(`${monthKey(anchorDate)}-01T00:00:00Z`))
      : `${dates[0]} a ${dates[6]}`;

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="h-11 rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
        >
          {rangeMode === "week" ? "Semana anterior" : "Mês anterior"}
        </button>
        <p className="text-base font-semibold capitalize text-neutral-900">{label}</p>
        <button
          type="button"
          onClick={() => navigate(1)}
          className="h-11 rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
        >
          {rangeMode === "week" ? "Próxima semana" : "Próximo mês"}
        </button>
      </div>

      <div className="grid grid-cols-7 gap-1 text-center text-xs font-medium text-neutral-500">
        {([1, 2, 3, 4, 5, 6, 7] as const).map((weekday) => (
          <span key={weekday}>{weekdayShortLabels[weekday]}</span>
        ))}
      </div>

      <div className="mt-1 grid grid-cols-7 gap-0.5">
        {Array.from({ length: leadingBlanks }, (_, i) => (
          <span key={`blank-${i}`} aria-hidden="true" />
        ))}
        {dates.map((date) => {
          const counts = byDate.get(date);
          const dayNumber = Number(date.slice(8, 10));
          const selected = selectedDate === date;
          const isToday = date === today;

          return (
            <button
              key={date}
              type="button"
              aria-pressed={selected}
              aria-label={`${dayNumber}${counts ? `, ${counts.bookings} reservas, ${counts.blocks} bloqueios` : ""}`}
              onClick={() => onSelectDate(date)}
              className={[
                "flex min-h-14 flex-col items-center justify-center gap-0.5 rounded-sm text-sm",
                selected
                  ? "bg-primary-600 font-semibold text-white"
                  : isToday
                    ? "bg-primary-100 text-neutral-900"
                    : "bg-neutral-0 text-neutral-900 hover:bg-neutral-100",
              ].join(" ")}
            >
              <span>{dayNumber}</span>
              {counts && (
                <span className="flex gap-1 text-[10px] leading-none">
                  {counts.bookings > 0 && (
                    <span className={selected ? "text-white" : "text-primary-700"}>
                      {counts.bookings}r
                    </span>
                  )}
                  {counts.blocks > 0 && (
                    <span className={selected ? "text-white" : "text-neutral-600"}>{counts.blocks}b</span>
                  )}
                </span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}

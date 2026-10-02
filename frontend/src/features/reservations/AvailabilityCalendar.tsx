/**
 * Passo 1 — calendário mensal de disponibilidade (RF-RES-01, RNF-05).
 * Grade própria em CSS grid (sem dependência nova). Dias com `bookable = false`
 * ficam desabilitados para seleção mas continuam tocáveis: o toque mostra o
 * motivo (RN-18, RN-20, RN-21) traduzido por `notBookableReasonText`. Dias
 * fechados (`open = false`) mostram "Fechado". Dias antes de hoje (`serverNow()`,
 * D-32) não são selecionáveis.
 */
import { useState } from "react";
import { addMonths, daysInMonth, isoWeekday, monthKey } from "./dateUtils";
import { notBookableReasonText } from "./reservationRules";
import { weekdayShortLabels } from "../../shared/utils/labels";
import type { AvailabilityDay, PublicSettingsDto } from "../../shared/api/types";

const MONTH_LABEL_FORMATTER = new Intl.DateTimeFormat("pt-BR", {
  month: "long",
  year: "numeric",
  timeZone: "UTC",
});

export interface AvailabilityCalendarProps {
  monthAnchor: string;
  onMonthChange: (monthAnchor: string) => void;
  today: string;
  days: AvailabilityDay[];
  isLoading: boolean;
  selectedDate: string | null;
  onSelectDate: (date: string) => void;
  settings: PublicSettingsDto | undefined;
}

export function AvailabilityCalendar({
  monthAnchor,
  onMonthChange,
  today,
  days,
  isLoading,
  selectedDate,
  onSelectDate,
  settings,
}: AvailabilityCalendarProps) {
  const [reasonDate, setReasonDate] = useState<string | null>(null);

  const dayByDate = new Map(days.map((day) => [day.date, day]));
  const allDates = daysInMonth(monthAnchor);
  const leadingBlanks = isoWeekday(allDates[0]) - 1;
  const canGoPrev = monthKey(monthAnchor) > monthKey(today);
  const reasonDay = reasonDate ? dayByDate.get(reasonDate) : undefined;

  return (
    <div>
      <div className="mb-3 flex items-center justify-between">
        <button
          type="button"
          onClick={() => onMonthChange(addMonths(monthAnchor, -1))}
          disabled={!canGoPrev}
          className="h-11 rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Mês anterior
        </button>
        <p className="text-base font-semibold capitalize text-neutral-900">
          {MONTH_LABEL_FORMATTER.format(new Date(`${monthAnchor}T00:00:00Z`))}
        </p>
        <button
          type="button"
          onClick={() => onMonthChange(addMonths(monthAnchor, 1))}
          className="h-11 rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
        >
          Próximo mês
        </button>
      </div>

      <div className="grid grid-cols-7 gap-1 text-center text-xs font-medium text-neutral-500">
        {([1, 2, 3, 4, 5, 6, 7] as const).map((weekday) => (
          <span key={weekday}>{weekdayShortLabels[weekday]}</span>
        ))}
      </div>

      {isLoading ? (
        <p className="mt-3 text-sm text-neutral-600">Carregando disponibilidade…</p>
      ) : (
        <div className="mt-1 grid grid-cols-7 gap-0.5">
          {Array.from({ length: leadingBlanks }, (_, i) => (
            <span key={`blank-${i}`} aria-hidden="true" />
          ))}
          {allDates.map((date) => {
            const day = dayByDate.get(date);
            const isPast = date < today;
            const dayNumber = Number(date.slice(8, 10));
            const closed = day ? !day.open : false;
            const bookable = !isPast && !!day?.open && day.bookable;
            const selected = selectedDate === date;
            // Dia com motivo (RN-18/20/21): continua focável e ativável por
            // teclado — o toque/Enter mostra o motivo (transparência da regra,
            // D-49), então não pode carregar aria-disabled (leitor de tela
            // trataria como não interativo). Passado/fechado não têm ação
            // nenhuma ao ativar, então esses seguem aria-disabled de verdade.
            const notBookableReason =
              !isPast && day && day.open && !day.bookable
                ? notBookableReasonText(day.notBookableReason, settings)
                : null;

            return (
              <button
                key={date}
                type="button"
                aria-pressed={selected}
                aria-disabled={bookable || notBookableReason ? undefined : true}
                aria-label={
                  closed
                    ? `${dayNumber}, fechado`
                    : notBookableReason
                      ? `${dayNumber}, não disponível: ${notBookableReason}`
                      : String(dayNumber)
                }
                onClick={() => {
                  if (isPast) return;
                  if (bookable) {
                    setReasonDate(null);
                    onSelectDate(date);
                  } else if (notBookableReason) {
                    setReasonDate(date);
                  }
                }}
                className={[
                  "flex aspect-square min-h-11 flex-col items-center justify-center rounded-sm text-sm",
                  selected
                    ? "bg-primary-600 font-semibold text-white"
                    : bookable
                      ? "bg-neutral-0 text-neutral-900 hover:bg-primary-100"
                      : "bg-neutral-100 text-neutral-400",
                ].join(" ")}
              >
                <span>{dayNumber}</span>
                {closed && <span className="text-[10px] leading-none">Fechado</span>}
              </button>
            );
          })}
        </div>
      )}

      <p role="status" aria-live="polite" className="mt-3 min-h-5 text-sm text-neutral-700">
        {reasonDay && !reasonDay.bookable
          ? notBookableReasonText(reasonDay.notBookableReason, settings)
          : ""}
      </p>
    </div>
  );
}

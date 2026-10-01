/**
 * Atalhos de período do dashboard, sobre a aritmética de `features/reservations/dateUtils`
 * (não duplicar). "Hoje" sempre vem de `serverNow()` — nunca `new Date()` (D-32, RNF-10).
 */
import { addMonths, daysInMonth } from "../reservations/dateUtils";
import type { Period } from "./api";

/** Primeiro e último dia do mês de `dateKey` (qualquer dia dentro do mês). */
export function monthRange(dateKey: string): Period {
  const days = daysInMonth(dateKey);
  return { from: days[0], to: days[days.length - 1] };
}

/**
 * Mês corrente, mês anterior, últimos 3 meses e últimos 6 meses (todos inclusive),
 * a partir de `today` (`serverNow()`). "Últimos N meses" = do 1º dia do mês (N-1) meses
 * atrás até o último dia do mês corrente.
 */
export function periodPresets(today: string): { label: string; period: Period }[] {
  const currentMonth = monthRange(today);
  const previousMonth = monthRange(addMonths(today, -1));
  const last3Months = { from: monthRange(addMonths(today, -2)).from, to: currentMonth.to };
  const last6Months = { from: monthRange(addMonths(today, -5)).from, to: currentMonth.to };
  return [
    { label: "Mês atual", period: currentMonth },
    { label: "Mês anterior", period: previousMonth },
    { label: "Últimos 3 meses", period: last3Months },
    { label: "Últimos 6 meses", period: last6Months },
  ];
}

/**
 * Aritmética de datas de calendário (`YYYY-MM-DD`) no fuso do condomínio, sem
 * dependência nova (CLAUDE.md §regras) — `Intl` e aritmética simples de string.
 * "Hoje" nunca vem de `new Date()` puro: quem chama passa o `Date` de
 * `serverNow()` (D-32).
 */

/** `Date` (instante real, de `serverNow()`) → `YYYY-MM-DD` no fuso informado. */
export function dateKeyInTimezone(instant: Date, timezone: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(instant);
}

export function addDays(dateKey: string, days: number): string {
  const date = new Date(`${dateKey}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** Primeiro dia do mês, `months` meses a partir de `dateKey`. */
export function addMonths(dateKey: string, months: number): string {
  const date = new Date(`${dateKey}T00:00:00Z`);
  date.setUTCMonth(date.getUTCMonth() + months, 1);
  return date.toISOString().slice(0, 10);
}

/** Todas as datas do mês de `dateKey` (qualquer dia dentro do mês), em ordem. */
export function daysInMonth(dateKey: string): string[] {
  const date = new Date(`${dateKey}T00:00:00Z`);
  const year = date.getUTCFullYear();
  const month = date.getUTCMonth();
  const count = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  return Array.from(
    { length: count },
    (_, i) => `${year}-${String(month + 1).padStart(2, "0")}-${String(i + 1).padStart(2, "0")}`,
  );
}

/** Dia da semana ISO-8601 (1 = segunda … 7 = domingo) de uma data de calendário. */
export function isoWeekday(dateKey: string): 1 | 2 | 3 | 4 | 5 | 6 | 7 {
  const jsDay = new Date(`${dateKey}T00:00:00Z`).getUTCDay(); // 0 = domingo
  return (jsDay === 0 ? 7 : jsDay) as 1 | 2 | 3 | 4 | 5 | 6 | 7;
}

/** `YYYY-MM` estável para comparar/chavear meses. */
export function monthKey(dateKey: string): string {
  return dateKey.slice(0, 7);
}

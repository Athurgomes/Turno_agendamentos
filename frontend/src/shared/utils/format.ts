/** Utilitários de formatação pt-BR (`docs/03-api.md` "Convenções transversais"). */

const dateFormatter = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  timeZone: "UTC",
});

const currencyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

/** `Date` ou `"YYYY-MM-DD"` → `dd/MM/yyyy`. Datas de agenda (`date`) são interpretadas em UTC, sem deslocar de fuso. */
export function formatDate(value: string | Date): string {
  const date = typeof value === "string" ? new Date(`${value}T00:00:00Z`) : value;
  return dateFormatter.format(date);
}

/** `"HH:mm"` (já vem pronto da API) ou `Date` → `HH:mm`. */
export function formatTime(value: string | Date): string {
  if (typeof value === "string") return value.slice(0, 5);
  return value.toISOString().slice(11, 16);
}

/**
 * Instante (ISO com offset ou `Date`) → `dd/MM/yyyy HH:mm` no fuso do
 * condomínio (CLAUDE.md §5 regra 5, RNF-10) — diferente de `formatDate`, que
 * é só para datas de agenda (`YYYY-MM-DD`, sem hora). Use para `createdAt`,
 * `occurredAt`, `resolvedAt`, `cancelledAt`, `paymentConfirmedAt` etc. O fuso
 * vem de `GET /system/clock` (`useServerClock`) ou `/settings/public`; até
 * carregar, cai para o default do MVP (`America/Sao_Paulo`, D-15).
 */
export function formatDateTimeInstant(
  value: string | Date,
  timezone: string = "America/Sao_Paulo",
): string {
  const date = typeof value === "string" ? new Date(value) : value;
  const parts = new Intl.DateTimeFormat("pt-BR", {
    timeZone: timezone,
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return `${get("day")}/${get("month")}/${get("year")} ${get("hour")}:${get("minute")}`;
}

/** `BigDecimal`/número → `R$ 150,00`. */
export function formatCurrency(value: number): string {
  return currencyFormatter.format(value);
}

const percentFormatter = new Intl.NumberFormat("pt-BR", {
  style: "percent",
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

/** Fração 0–1 (ex. `occupancyRate`, `residentRate`) → `12,3%`. */
export function formatPercent(value: number): string {
  return percentFormatter.format(value);
}

const hoursFormatter = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });

/** Número de horas (ex. `reservedHours`, `averageResolutionHours`) → `3,5 h`. */
export function formatHours(value: number): string {
  return `${hoursFormatter.format(value)} h`;
}

/** Telefone só dígitos com DDI (`5562999998888`) → `+55 (62) 99999-8888`. */
export function formatPhone(digitsWithDdi: string): string {
  const digits = digitsWithDdi.replace(/\D/g, "");
  const match = /^(\d{2})(\d{2})(\d{4,5})(\d{4})$/.exec(digits);
  if (!match) return digitsWithDdi;
  const [, ddi, ddd, prefix, suffix] = match;
  return `+${ddi} (${ddd}) ${prefix}-${suffix}`;
}

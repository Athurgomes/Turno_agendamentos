/**
 * Regras de exibição do fluxo de reserva (RF-RES-01..03, RN-18, RN-20, RN-21).
 * `notBookableReason` chega da API como o `code` de negócio que reprovaria o
 * dia (`docs/03-api.md` "Reservas e bloqueios", D-49); aqui ele vira texto
 * pt-BR usando os parâmetros de `/settings/public`. Mensagens de erro do
 * `POST /reservations` já chegam prontas em `ApiError.detail` (RFC 9457) —
 * não precisam de tradução, só saber para qual passo voltar.
 */
import type { PublicSettingsDto } from "../../shared/api/types";

export function notBookableReasonText(
  code: string | null,
  settings: PublicSettingsDto | undefined,
): string {
  switch (code) {
    case "AREA_NOT_ACTIVE":
      return "Esta área não está disponível para reservas no momento.";
    case "SAME_DAY_NOT_ALLOWED":
      return "Não é possível reservar para o mesmo dia.";
    case "NEXT_DAY_WINDOW_CLOSED": {
      const start = settings?.nextDayWindowStart ?? "06:00";
      const end = settings?.nextDayWindowEnd ?? "16:00";
      return `Reservas para amanhã só podem ser feitas entre ${start} e ${end}.`;
    }
    case "TOO_FAR_AHEAD": {
      const days = settings?.maxAdvanceDays ?? 60;
      return `Reservas podem ser feitas com até ${days} dias de antecedência.`;
    }
    default:
      return "Este dia não está disponível para reserva.";
  }
}

export type WizardStep = "date" | "time" | "details" | "review";

const STEP_BY_ERROR_CODE: Record<string, WizardStep> = {
  AREA_NOT_ACTIVE: "date",
  SAME_DAY_NOT_ALLOWED: "date",
  NEXT_DAY_WINDOW_CLOSED: "date",
  TOO_FAR_AHEAD: "date",
  OUTSIDE_OPENING_HOURS: "time",
  INVALID_SLOT: "time",
  RESERVATION_OVERLAP: "time",
  CAPACITY_EXCEEDED: "details",
};

/** Para qual passo do formulário voltar quando `POST /reservations` recusa (RF-RES-03). */
export function stepForErrorCode(code: string): WizardStep {
  return STEP_BY_ERROR_CODE[code] ?? "review";
}

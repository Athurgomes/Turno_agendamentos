/**
 * Regras de transição de status do report (RN-36) — só para UX (mostrar as
 * opções válidas no menu do SÍNDICO/ADMIN); o backend revalida a transição.
 */
import type { ReportStatus } from "../../shared/api/types";

const FORWARD_ORDER: ReportStatus[] = ["OPEN", "IN_REVIEW", "IN_MAINTENANCE", "RESOLVED"];

/** `RESOLVED` e `DISMISSED` são finais — sem novas transições (RN-36). */
export function isFinalReportStatus(status: ReportStatus): boolean {
  return status === "RESOLVED" || status === "DISMISSED";
}

/**
 * Status seguintes que o SÍNDICO/ADMIN pode escolher a partir do atual: só
 * avança na ordem `OPEN → IN_REVIEW → IN_MAINTENANCE → RESOLVED` (pode pular
 * etapas para frente, nunca voltar), mais `DISMISSED` a partir de qualquer
 * status não final. Vazio quando o status atual já é final.
 */
export function nextReportStatusOptions(status: ReportStatus): ReportStatus[] {
  if (isFinalReportStatus(status)) return [];
  const forward = FORWARD_ORDER.slice(FORWARD_ORDER.indexOf(status) + 1);
  return [...forward, "DISMISSED"];
}

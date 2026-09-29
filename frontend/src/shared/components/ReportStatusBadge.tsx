/**
 * Pílula de status de report (`DESIGN.md` "Badges de status" — RN-36): o
 * rótulo de texto sempre acompanha a cor (WCAG 1.4.1).
 */
import { reportStatusLabels } from "../utils/labels";
import { StatusBadge } from "./StatusBadge";
import type { ReportStatus } from "../api/types";

const REPORT_STATUS_TONE: Record<ReportStatus, string> = {
  OPEN: "bg-status-open-bg text-status-open-text",
  IN_REVIEW: "bg-status-review-bg text-status-review-text",
  IN_MAINTENANCE: "bg-status-in-maintenance-bg text-status-in-maintenance-text",
  RESOLVED: "bg-status-resolved-bg text-status-resolved-text",
  DISMISSED: "bg-status-dismissed-bg text-status-dismissed-text",
};

export function ReportStatusBadge({ status }: { status: ReportStatus }) {
  return <StatusBadge label={reportStatusLabels[status]} toneClassName={REPORT_STATUS_TONE[status]} />;
}

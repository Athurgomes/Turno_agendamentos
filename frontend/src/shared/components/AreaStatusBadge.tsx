/**
 * Badge de status de área (RN-14, `DESIGN.md` "Badges de status — cor com significado"):
 * usado no catálogo e no detalhe da área, para que os dois lugares nunca divirjam de cor.
 */
import { areaStatusLabels } from "../utils/labels";
import { StatusBadge } from "./StatusBadge";
import type { AreaStatus } from "../api/types";

const AREA_STATUS_TONE: Record<AreaStatus, string> = {
  ACTIVE: "bg-status-active-bg text-status-active-text",
  MAINTENANCE: "bg-status-maintenance-bg text-status-maintenance-text",
  RENOVATION: "bg-status-renovation-bg text-status-renovation-text",
  INTERDICTED: "bg-status-interdicted-bg text-status-interdicted-text",
  INACTIVE: "bg-status-inactive-bg text-status-inactive-text",
};

export function AreaStatusBadge({ status }: { status: AreaStatus }) {
  return <StatusBadge label={areaStatusLabels[status]} toneClassName={AREA_STATUS_TONE[status]} />;
}

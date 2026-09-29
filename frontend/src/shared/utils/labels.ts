/**
 * Rótulos pt-BR de todos os enums de `docs/02-modelo-de-dados.md` §2. Único
 * lugar de tradução (não duplicar em telas); ver `docs/02` §2 e RN citadas.
 */
import type {
  AreaCategory,
  AreaStatus,
  CancelledBy,
  InspectionCondition,
  IsoWeekday,
  ReportCategory,
  ReportStatus,
  ReservationEventType,
  ReservationKind,
  ReservationStatus,
  Role,
} from "../api/types";

export const roleLabels: Record<Role, string> = {
  ADMIN: "Administração",
  SYNDIC: "Síndico",
  UNIT: "Morador",
};

// RN-13
export const areaCategoryLabels: Record<AreaCategory, string> = {
  PARTY_ROOM: "Salão de festas",
  BARBECUE: "Churrasqueira",
  GOURMET_SPACE: "Espaço gourmet",
  POOL: "Piscina",
  SPORTS_COURT: "Quadra poliesportiva",
  TENNIS_COURT: "Quadra de tênis",
  GYM: "Academia",
  GAME_ROOM: "Salão de jogos",
  TOY_ROOM: "Brinquedoteca",
  PLAYGROUND: "Playground",
  SAUNA: "Sauna",
  CINEMA: "Cinema/Home theater",
  COWORKING: "Coworking/Sala de estudos",
  PET_PLACE: "Pet place",
  OTHER: "Outro",
};

// RN-14
export const areaStatusLabels: Record<AreaStatus, string> = {
  ACTIVE: "Disponível",
  MAINTENANCE: "Em manutenção",
  RENOVATION: "Em reforma",
  INTERDICTED: "Interditada",
  INACTIVE: "Desativada",
};

export const reservationKindLabels: Record<ReservationKind, string> = {
  BOOKING: "Reserva",
  BLOCK: "Bloqueio",
};

// RN-29, RN-32: "Realizada" é derivado (CONFIRMED cuja data já passou), sem enum próprio.
export const reservationStatusLabels: Record<ReservationStatus, string> = {
  PENDING_PAYMENT: "Pendente",
  CONFIRMED: "Confirmada",
  CANCELLED: "Cancelada",
};

export const RESERVATION_DONE_LABEL = "Realizada";

/** Rótulo de status de reserva já considerando o estado derivado "Realizada" (RN-32). */
export function reservationStatusLabel(
  status: ReservationStatus,
  endAt: Date,
  now: Date,
): string {
  if (status === "CONFIRMED" && endAt.getTime() < now.getTime()) {
    return RESERVATION_DONE_LABEL;
  }
  return reservationStatusLabels[status];
}

// Histórico da reserva/bloqueio (`GET /reservations/{id}/events`, D-49)
export const reservationEventTypeLabels: Record<ReservationEventType, string> = {
  CREATED: "Criada",
  UPDATED: "Alterada",
  CANCELLED: "Cancelada",
  PAYMENT_CONFIRMED: "Pagamento confirmado",
  // RN-31: reserva PENDING_PAYMENT vencida sem confirmação (ator sempre null).
  EXPIRED: "Expirada",
};

export const cancelledByLabels: Record<CancelledBy, string> = {
  RESIDENT: "Morador",
  ADMIN: "Administração",
  SYSTEM: "Sistema",
};

// RN-35
export const reportCategoryLabels: Record<ReportCategory, string> = {
  DAMAGE: "Dano/algo quebrado",
  MALFUNCTION: "Não funcionando",
  CLEANLINESS: "Limpeza",
  SAFETY: "Segurança",
  MISSING_ITEM: "Item faltando",
  OTHER: "Outro",
};

// RN-36
export const reportStatusLabels: Record<ReportStatus, string> = {
  OPEN: "Aberto",
  IN_REVIEW: "Em análise",
  IN_MAINTENANCE: "Em manutenção",
  RESOLVED: "Resolvido",
  DISMISSED: "Descartado",
};

export const inspectionConditionLabels: Record<InspectionCondition, string> = {
  GOOD: "Bom",
  FAIR: "Regular",
  POOR: "Ruim",
};

// ISO-8601: 1 = segunda … 7 = domingo
export const weekdayLabels: Record<IsoWeekday, string> = {
  1: "Segunda-feira",
  2: "Terça-feira",
  3: "Quarta-feira",
  4: "Quinta-feira",
  5: "Sexta-feira",
  6: "Sábado",
  7: "Domingo",
};

export const weekdayShortLabels: Record<IsoWeekday, string> = {
  1: "Seg",
  2: "Ter",
  3: "Qua",
  4: "Qui",
  5: "Sex",
  6: "Sáb",
  7: "Dom",
};

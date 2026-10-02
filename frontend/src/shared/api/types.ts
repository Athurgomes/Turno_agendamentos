/**
 * Tipos compartilhados do contrato da API (`docs/03-api.md`) e enums de domínio
 * (`docs/02-modelo-de-dados.md` §2). Enums em inglês (código); rótulos pt-BR ficam
 * em `shared/utils/labels.ts`.
 */

export type Role = "ADMIN" | "SYNDIC" | "UNIT";

export type AreaCategory =
  | "PARTY_ROOM"
  | "BARBECUE"
  | "GOURMET_SPACE"
  | "POOL"
  | "SPORTS_COURT"
  | "TENNIS_COURT"
  | "GYM"
  | "GAME_ROOM"
  | "TOY_ROOM"
  | "PLAYGROUND"
  | "SAUNA"
  | "CINEMA"
  | "COWORKING"
  | "PET_PLACE"
  | "OTHER";

export type AreaStatus =
  | "ACTIVE"
  | "MAINTENANCE"
  | "RENOVATION"
  | "INTERDICTED"
  | "INACTIVE";

export type ReservationKind = "BOOKING" | "BLOCK";

export type ReservationStatus = "PENDING_PAYMENT" | "CONFIRMED" | "CANCELLED";

export type CancelledBy = "RESIDENT" | "ADMIN" | "SYSTEM";

export type ReportCategory =
  | "DAMAGE"
  | "MALFUNCTION"
  | "CLEANLINESS"
  | "SAFETY"
  | "MISSING_ITEM"
  | "OTHER";

export type ReportStatus =
  | "OPEN"
  | "IN_REVIEW"
  | "IN_MAINTENANCE"
  | "RESOLVED"
  | "DISMISSED";

export type InspectionCondition = "GOOD" | "FAIR" | "POOR";

/** Dia da semana ISO-8601: 1 = segunda … 7 = domingo. */
export type IsoWeekday = 1 | 2 | 3 | 4 | 5 | 6 | 7;

/** `user` retornado por login/refresh/me (`docs/03-api.md` "Auth", D-42). */
export interface SessionUser {
  id: string;
  role: Role;
  name: string;
  unitId: string | null;
  unitIdentifier: string | null;
  tempPassword: boolean;
}

export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  user: SessionUser;
}

/** Campo de erro de validação (`VALIDATION_ERROR`). */
export interface ApiFieldError {
  field: string;
  message: string;
}

/** Corpo `application/problem+json` (RFC 9457) devolvido pela API. */
export interface ProblemDetailBody {
  type?: string;
  title?: string;
  status: number;
  detail: string;
  code: string;
  errors?: ApiFieldError[];
  [key: string]: unknown;
}

/** Resposta paginada padrão (`docs/03-api.md` "Convenções transversais"). */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Moradores e unidades (`docs/03-api.md` "Unidades e moradores", D-43). `cpf`
 * vem completo só para ADMIN; mascarado (`***.456.789-**`) ou `null` em `/me/unit`.
 */
export interface ResidentDto {
  id: string;
  name: string;
  phone: string;
  email: string | null;
  cpf: string | null;
  primary: boolean;
}

/** Item de `GET /units` (lista). */
export interface UnitSummary {
  id: string;
  block: string | null;
  number: string;
  identifier: string;
  active: boolean;
  primaryResident: { name: string; phone: string };
  residentsCount: number;
}

/** `GET /units/{id}` — detalhe com todos os moradores ativos. */
export interface UnitDetail {
  id: string;
  block: string | null;
  number: string;
  identifier: string;
  username: string;
  active: boolean;
  residents: ResidentDto[];
}

/** Credenciais geradas por cadastro, reset de senha ou troca de titularidade (RN-02, RN-03). */
export interface Credentials {
  username: string;
  tempPassword: string;
}

/**
 * Reserva/bloqueio resumido, usado em listas de "reservas afetadas"
 * (troca de titularidade RN-10, desativação P-11, mudança de status de área RN-16).
 */
export interface ReservationSummary {
  id: string;
  code: string;
  kind: ReservationKind;
  areaId: string;
  areaName: string;
  unitIdentifier: string;
  residentName: string;
  date: string;
  startTime: string;
  endTime: string;
  status: ReservationStatus;
}

/** Item de `openReports.items` em `GET /dashboard/home` (F7-1, RF-SIN-01). */
export interface OpenReportItemDto {
  id: string;
  code: string;
  areaName: string;
  unitIdentifier: string;
  category: ReportCategory;
  status: ReportStatus;
  createdAt: string;
}

/** Item de `overdueInspections` em `GET /dashboard/home` (F7-1, RF-SIN-01). */
export interface OverdueInspectionDto {
  areaId: string;
  areaName: string;
  status: AreaStatus;
  lastInspectionAt: string | null;
  daysSinceInspection: number | null;
}

/**
 * `GET /dashboard/home` (F7-1, RF-SIN-01): página inicial do síndico/administração —
 * reservas/bloqueios de hoje e dos próximos 7 dias, reports abertos e vistorias atrasadas.
 */
export interface DashboardHomeDto {
  today: ReservationSummary[];
  next7Days: ReservationSummary[];
  openReports: { count: number; items: OpenReportItemDto[] };
  overdueInspections: OverdueInspectionDto[];
}

/**
 * `GET /dashboard/summary` (RF-DAS-01/02, F8-3): cards de indicador do
 * período. Só formatação no front — nenhum cálculo (CLAUDE.md §5 regra 3).
 */
export interface DashboardSummaryDto {
  from: string;
  to: string;
  activeUnits: number;
  activeResidents: number;
  reservations: {
    total: number;
    pendingPayment: number;
    confirmed: number;
    cancelled: number;
  };
  cancellations: {
    byResident: number;
    byAdmin: number;
    bySystem: number;
    residentRate: number;
    adminRate: number;
  };
  amounts: { confirmed: number; pending: number };
  reports: {
    opened: number;
    resolved: number;
    open: number;
    averageResolutionHours: number | null;
    byCategory: { category: ReportCategory; count: number }[];
  };
  maintenanceCost: number;
}

/** Item de `GET /dashboard/reservations-by-month` (RF-DAS-02): série de 12 meses. */
export interface MonthlyReservationsDto {
  month: string;
  total: number;
  confirmed: number;
  pendingPayment: number;
  cancelled: number;
}

/** Item de `GET /dashboard/areas` (RF-DAS-02): reservas, ocupação, reports e custo por área. */
export interface AreaMetricDto {
  areaId: string;
  areaName: string;
  category: AreaCategory;
  status: AreaStatus;
  reservations: number;
  reservedHours: number;
  availableHours: number;
  occupancyRate: number;
  reports: number;
  maintenanceCost: number;
}

/** Célula de `GET /dashboard/demand-heatmap` (RF-DAS-02): 1 = segunda … 7 = domingo. */
export interface HeatmapCellDto {
  dayOfWeek: IsoWeekday;
  hour: number;
  count: number;
}

/** Item de `GET /dashboard/top-units` (RF-DAS-02): até 10, ordenado por reservas desc. */
export interface TopUnitDto {
  unitId: string;
  unitIdentifier: string;
  reservations: number;
  reservedHours: number;
}

/** `type` de `GET /exports/{type}` (docs/03-api.md "Dashboard e exportação"). */
export type ExportType = "reservations" | "areas" | "reports" | "payments" | "units";

/** `format` de `GET /exports/{type}`. */
export type ExportFormat = "csv" | "xlsx";

/** Conta de síndico (`docs/03-api.md` "Contas de síndico e configurações", RF-UNI-07). */
export interface SyndicDto {
  id: string;
  name: string;
  email: string;
  phone: string;
  active: boolean;
}

/** `GET /me/unit` (RF-UNI-05, RF-UNI-06, D-43): a própria unidade da conta UNIT. */
export interface MyUnitDto {
  id: string;
  block: string | null;
  number: string;
  identifier: string;
  username: string;
  active: boolean;
  residents: ResidentDto[];
}

/**
 * Parâmetros de regra do condomínio (`docs/03-api.md` "Contas de síndico e
 * configurações", RN-20/21/22/30/34). `timezone` e `slotMinutes` são só
 * leitura no MVP (D-15).
 */
export interface SettingsDto {
  condominiumName: string;
  timezone: string;
  /** Opcional no backend (`UpdateSettingsRequest` sem `@NotBlank`); `null` quando `APP_DEFAULT_PAYMENT_WHATSAPP` não foi definida no bootstrap. */
  defaultPaymentWhatsapp: string | null;
  minAdvanceDays: number;
  nextDayWindowStart: string;
  nextDayWindowEnd: string;
  maxAdvanceDays: number;
  maxActiveBookingsPerUnit: number;
  residentCancelDeadlineHours: number;
  slotMinutes: number;
  reportWindowDays: number;
}

/**
 * Áreas comuns (`docs/03-api.md` "Áreas", D-44). Fotos e vistorias compõem o
 * histórico de conservação (RF-ARE-06 a 08, RN-17).
 */
export interface AreaCategoryTemplateDto {
  code: AreaCategory;
  label: string;
  rulesTemplate: string;
  conductTemplate: string;
}

/**
 * `PhotoDto` (D-44): `url` é pré-assinada (D-41), usada direto em `<img>`.
 * `uploadedBy` vem `null` para conta `UNIT` (minimização de dados, RNF-01 — D-52).
 */
export interface PhotoDto {
  id: string;
  url: string;
  caption: string | null;
  featured: boolean;
  archived: boolean;
  takenAt: string;
  createdAt: string;
  uploadedBy: { id: string; name: string } | null;
  inspectionId: string | null;
}

export interface OpeningHoursDto {
  dayOfWeek: IsoWeekday;
  openTime: string;
  closeTime: string;
}

/** Item do catálogo (`GET /areas`). Áreas não ativas aparecem com o status, sem ação de reservar. */
export interface AreaSummary {
  id: string;
  name: string;
  category: AreaCategory;
  status: AreaStatus;
  capacity: number;
  requiresPayment: boolean;
  price: number | null;
  coverPhotoUrl: string | null;
}

/** `GET /areas/{id}` — detalhe completo, incluindo as fotos de vitrine (não o histórico). */
export interface AreaDetail {
  id: string;
  name: string;
  category: AreaCategory;
  status: AreaStatus;
  description: string;
  rules: string;
  conductGuidelines: string;
  capacity: number;
  requiresPayment: boolean;
  price: number | null;
  paymentWhatsapp: string | null;
  openingHours: OpeningHoursDto[];
  photos: PhotoDto[];
  version: number;
}

export interface InspectionDto {
  id: string;
  inspectedAt: string;
  overallCondition: InspectionCondition;
  notes: string | null;
  author: { id: string; name: string };
  photos: PhotoDto[];
}

/** `GET /settings/public` (todos os perfis) — mesmos campos de `SettingsDto` menos `defaultPaymentWhatsapp` (D-44). */
export type PublicSettingsDto = Omit<SettingsDto, "defaultPaymentWhatsapp">;

/**
 * Reservas e bloqueios (`docs/03-api.md` "Reservas e bloqueios", D-49).
 * `ReservationDto` é a visão da própria unidade (`POST /reservations`,
 * `GET /me/reservations`, `POST /me/reservations/{id}/cancel`).
 */
export interface ReservationDto {
  id: string;
  code: string;
  kind: ReservationKind;
  areaId: string;
  areaName: string;
  date: string;
  startTime: string;
  endTime: string;
  residentId: string;
  residentName: string;
  guests: number;
  notes: string | null;
  status: ReservationStatus;
  /** `true` para `CONFIRMED` com fim no passado ("Realizada" na UI — RN-32). */
  completed: boolean;
  /** Texto pronto para exibir ao morador (RN-29). */
  statusReason: string | null;
  cancelledBy: CancelledBy | null;
  requiresPayment: boolean;
  price: number | null;
  createdAt: string;
  canCancel: boolean;
  /** RN-34: `CONFIRMED`, da própria unidade, e dentro da janela de report. */
  canReport: boolean;
  /** Só quando `status = PENDING_PAYMENT` (RN-26). */
  whatsappPaymentUrl: string | null;
}

/**
 * `AdminReservationDto` (S/A) — visão de agenda (`docs/03-api.md` "Reservas e
 * bloqueios", D-49): `ReservationDto` + dados de contato da unidade.
 * `whatsappContactUrl = https://wa.me/{residentPhone}`. Em bloqueios
 * (`kind = BLOCK`) os campos de unidade/morador vêm `null` e `notes` é o motivo.
 */
export interface AdminReservationDto extends ReservationDto {
  unitId: string | null;
  unitIdentifier: string | null;
  residentPhone: string | null;
  whatsappContactUrl: string | null;
  cancelledAt: string | null;
  paymentConfirmedAt: string | null;
}

/**
 * Item de `GET /payments/pending` (ADMIN, RF-PAG-02, D-49): `AdminReservationDto`
 * de uma reserva `PENDING_PAYMENT` + `within48h` (destaque para início em até 48h).
 */
export interface PendingPaymentDto extends AdminReservationDto {
  within48h: boolean;
}

export type ReservationEventType =
  | "CREATED"
  | "UPDATED"
  | "CANCELLED"
  | "PAYMENT_CONFIRMED"
  | "EXPIRED";

/** `GET /reservations/{id}/events` (S/A) — histórico em ordem cronológica (D-49). */
export interface ReservationEventDto {
  type: ReservationEventType;
  occurredAt: string;
  actor: { id: string; name: string; role: Role } | null;
  justification: string | null;
  changes: Record<string, { from: unknown; to: unknown }> | null;
}

/** Item de `busy` em `AvailabilityDay`. Para S/A traz também `reservationId`, `code`, `unitIdentifier`, `status`. */
export interface AvailabilityBusySlot {
  startTime: string;
  endTime: string;
  kind: ReservationKind;
  reservationId?: string;
  code?: string;
  unitIdentifier?: string;
  status?: ReservationStatus;
}

/**
 * `GET /areas/{id}/availability` (D-49): um item por dia do intervalo.
 * `notBookableReason` é o `code` de negócio (ex. `NEXT_DAY_WINDOW_CLOSED`) que
 * aplica RN-18/20/21 ao dia; o front traduz para pt-BR com os parâmetros de
 * `/settings/public` (`features/reservations/reservationRules.ts`).
 */
export interface AvailabilityDay {
  date: string;
  open: boolean;
  openTime: string | null;
  closeTime: string | null;
  bookable: boolean;
  notBookableReason: string | null;
  busy: AvailabilityBusySlot[];
}

/** Estágio da foto de report (`docs/02-modelo-de-dados.md` "report_photo"): a
 * foto enviada com o report (RF-REP-01) ou a foto do reparo (RF-REP-04). */
export type ReportPhotoStage = "REPORTED" | "REPAIR";

export interface ReportPhotoDto {
  id: string;
  url: string;
  stage: ReportPhotoStage;
  createdAt: string;
}

/** A unidade só recebe comentários com `visibleToResident = true` (D-50). */
export interface ReportCommentDto {
  id: string;
  text: string;
  authorName: string;
  createdAt: string;
  visibleToResident: boolean;
}

/**
 * Report de ocorrência (`docs/03-api.md` "Reports", D-50) — visão da própria
 * unidade (`GET /me/reports`, `POST /me/reservations/{id}/reports`).
 */
export interface ReportDto {
  id: string;
  code: string;
  reservationId: string;
  reservationCode: string;
  areaId: string;
  areaName: string;
  reservationDate: string;
  category: ReportCategory;
  description: string;
  residentName: string;
  status: ReportStatus;
  statusReason: string | null;
  createdAt: string;
  resolvedAt: string | null;
  photos: ReportPhotoDto[];
  comments: ReportCommentDto[];
}

/** `AdminReportDto` (S/A) — `ReportDto` + dados de contato e custo (RN-37, D-50). */
export interface AdminReportDto extends ReportDto {
  unitId: string;
  unitIdentifier: string;
  residentPhone: string;
  whatsappContactUrl: string;
  maintenanceCost: number | null;
}

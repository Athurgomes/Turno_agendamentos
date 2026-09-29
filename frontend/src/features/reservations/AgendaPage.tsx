/**
 * Agenda de SÍNDICO/ADMIN (`/agenda`, RF-RES-07/08/09/10, RF-SIN-02/05, D-16,
 * D-49): calendário (semana/mês) ou lista com filtros, detalhe com histórico
 * e ações de ADMIN, e bloqueios. SÍNDICO só vê e contata por WhatsApp — nunca
 * altera nem cancela reserva de morador; síndico cria/remove bloqueio (D-16).
 */
import { useState } from "react";
import { useSession } from "../auth/useSession";
import { useAreasQuery } from "../areas/hooks";
import { usePublicSettingsQuery } from "./hooks";
import { serverNow, useServerClock } from "../../shared/hooks/useServerClock";
import { dateKeyInTimezone, monthKey } from "./dateUtils";
import { useAdminReservationsQuery } from "./hooks";
import { AgendaCalendar, type CalendarRangeMode } from "./AgendaCalendar";
import { AgendaListItem } from "./AgendaListItem";
import { BlockFormDialog } from "./BlockFormDialog";
import { ReservationDetailPanel } from "./ReservationDetailPanel";
import { ConfirmDialog } from "../../shared/components/ConfirmDialog";
import { useDeleteBlock } from "./hooks";
import { ApiError } from "../../shared/api/client";
import { fieldClassName } from "../../shared/components/formStyles";
import { reservationKindLabels, reservationStatusLabels } from "../../shared/utils/labels";
import type { AdminReservationDto, ReservationKind, ReservationStatus } from "../../shared/api/types";

type ViewMode = "list" | "calendar";

function monthRange(anchor: string): { from: string; to: string } {
  const key = monthKey(anchor);
  const [year, month] = key.split("-").map(Number);
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return { from: `${key}-01`, to: `${key}-${String(lastDay).padStart(2, "0")}` };
}

function weekRange(anchor: string): { from: string; to: string } {
  const jsDay = new Date(`${anchor}T00:00:00Z`).getUTCDay();
  const mondayOffset = jsDay === 0 ? 6 : jsDay - 1;
  const monday = new Date(`${anchor}T00:00:00Z`);
  monday.setUTCDate(monday.getUTCDate() - mondayOffset);
  const sunday = new Date(monday);
  sunday.setUTCDate(sunday.getUTCDate() + 6);
  return { from: monday.toISOString().slice(0, 10), to: sunday.toISOString().slice(0, 10) };
}

export function AgendaPage() {
  const { user } = useSession();
  const { data: settings } = usePublicSettingsQuery();
  useServerClock();
  const timezone = settings?.timezone ?? "America/Sao_Paulo";
  const today = dateKeyInTimezone(serverNow(), timezone);

  const [viewMode, setViewMode] = useState<ViewMode>("list");
  const [rangeMode, setRangeMode] = useState<CalendarRangeMode>("month");
  const [anchorDate, setAnchorDate] = useState(today);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [areaId, setAreaId] = useState("");
  const [status, setStatus] = useState<ReservationStatus | "">("");
  const [kind, setKind] = useState<ReservationKind | "">("");
  const [unitIdentifier, setUnitIdentifier] = useState("");
  const [fromInput, setFromInput] = useState("");
  const [toInput, setToInput] = useState("");
  const [page, setPage] = useState(0);

  const [showBlockForm, setShowBlockForm] = useState(false);
  const [blockToRemove, setBlockToRemove] = useState<AdminReservationDto | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);
  const deleteBlock = useDeleteBlock();

  const { data: areas } = useAreasQuery({});

  const range = viewMode === "calendar" ? (rangeMode === "week" ? weekRange(anchorDate) : monthRange(anchorDate)) : null;

  const filters = {
    areaId: areaId || undefined,
    status: status || undefined,
    kind: kind || undefined,
    unitIdentifier: unitIdentifier.trim() || undefined,
    from: range ? range.from : fromInput || undefined,
    to: range ? range.to : toInput || undefined,
    page: viewMode === "list" ? page : 0,
  };

  const { data, isLoading } = useAdminReservationsQuery(filters);
  const items = data?.content ?? [];

  const dayItems = selectedDate ? items.filter((item) => item.date === selectedDate) : [];

  async function handleRemoveBlock() {
    if (!blockToRemove) return;
    setRemoveError(null);
    try {
      await deleteBlock.mutateAsync(blockToRemove.id);
      setBlockToRemove(null);
    } catch (err) {
      setRemoveError(
        err instanceof ApiError ? err.detail : "Não foi possível remover este bloqueio. Tente novamente.",
      );
    }
  }

  const showingDetail = !!selectedId;

  return (
    <div>
      <div className="mb-4 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">Agenda</h1>
          <p className="text-sm text-neutral-600">Reservas e bloqueios de todas as áreas comuns.</p>
        </div>
        <button
          type="button"
          onClick={() => setShowBlockForm(true)}
          className="flex h-11 items-center justify-center rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700"
        >
          + Novo bloqueio
        </button>
      </div>

      <div className="md:flex md:items-start md:gap-6">
      <div className={[showingDetail ? "hidden md:block" : "block", "md:min-w-0 md:flex-1"].join(" ")}>
        <div role="tablist" className="mb-4 flex gap-2 border-b border-neutral-200">
          <button
            type="button"
            role="tab"
            aria-selected={viewMode === "list"}
            onClick={() => setViewMode("list")}
            className={[
              "h-11 px-4 text-sm font-medium",
              viewMode === "list"
                ? "border-b-2 border-primary-600 text-primary-700"
                : "text-neutral-600 hover:text-neutral-900",
            ].join(" ")}
          >
            Lista
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={viewMode === "calendar"}
            onClick={() => setViewMode("calendar")}
            className={[
              "h-11 px-4 text-sm font-medium",
              viewMode === "calendar"
                ? "border-b-2 border-primary-600 text-primary-700"
                : "text-neutral-600 hover:text-neutral-900",
            ].join(" ")}
          >
            Calendário
          </button>
        </div>

        {viewMode === "calendar" && (
          <div className="mb-4 flex gap-2">
            <button
              type="button"
              onClick={() => setRangeMode("week")}
              className={[
                "h-9 rounded-md border px-3 text-sm font-medium",
                rangeMode === "week"
                  ? "border-primary-600 bg-primary-100 text-primary-700"
                  : "border-neutral-300 text-neutral-700 hover:bg-neutral-100",
              ].join(" ")}
            >
              Semana
            </button>
            <button
              type="button"
              onClick={() => setRangeMode("month")}
              className={[
                "h-9 rounded-md border px-3 text-sm font-medium",
                rangeMode === "month"
                  ? "border-primary-600 bg-primary-100 text-primary-700"
                  : "border-neutral-300 text-neutral-700 hover:bg-neutral-100",
              ].join(" ")}
            >
              Mês
            </button>
          </div>
        )}

        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:flex-wrap">
          <div className="flex flex-col gap-1 sm:w-48">
            <label htmlFor="filter-area" className="text-sm font-medium text-neutral-700">
              Área
            </label>
            <select
              id="filter-area"
              value={areaId}
              onChange={(event) => {
                setAreaId(event.target.value);
                setPage(0);
              }}
              className={fieldClassName(false)}
            >
              <option value="">Todas</option>
              {(areas ?? []).map((area) => (
                <option key={area.id} value={area.id}>
                  {area.name}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1 sm:w-44">
            <label htmlFor="filter-status" className="text-sm font-medium text-neutral-700">
              Status
            </label>
            <select
              id="filter-status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as ReservationStatus | "");
                setPage(0);
              }}
              className={fieldClassName(false)}
            >
              <option value="">Todos</option>
              {(Object.keys(reservationStatusLabels) as ReservationStatus[]).map((option) => (
                <option key={option} value={option}>
                  {reservationStatusLabels[option]}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1 sm:w-40">
            <label htmlFor="filter-kind" className="text-sm font-medium text-neutral-700">
              Tipo
            </label>
            <select
              id="filter-kind"
              value={kind}
              onChange={(event) => {
                setKind(event.target.value as ReservationKind | "");
                setPage(0);
              }}
              className={fieldClassName(false)}
            >
              <option value="">Todos</option>
              {(Object.keys(reservationKindLabels) as ReservationKind[]).map((option) => (
                <option key={option} value={option}>
                  {reservationKindLabels[option]}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1 sm:w-44">
            <label htmlFor="filter-unit" className="text-sm font-medium text-neutral-700">
              Unidade
            </label>
            <input
              id="filter-unit"
              placeholder="a-1203"
              value={unitIdentifier}
              onChange={(event) => {
                setUnitIdentifier(event.target.value);
                setPage(0);
              }}
              className={fieldClassName(false)}
            />
          </div>

          {viewMode === "list" && (
            <>
              <div className="flex flex-col gap-1">
                <label htmlFor="filter-from" className="text-sm font-medium text-neutral-700">
                  De
                </label>
                <input
                  id="filter-from"
                  type="date"
                  value={fromInput}
                  onChange={(event) => {
                    setFromInput(event.target.value);
                    setPage(0);
                  }}
                  className={fieldClassName(false)}
                />
              </div>
              <div className="flex flex-col gap-1">
                <label htmlFor="filter-to" className="text-sm font-medium text-neutral-700">
                  Até
                </label>
                <input
                  id="filter-to"
                  type="date"
                  value={toInput}
                  onChange={(event) => {
                    setToInput(event.target.value);
                    setPage(0);
                  }}
                  className={fieldClassName(false)}
                />
              </div>
            </>
          )}
        </div>

        {isLoading && <p className="text-sm text-neutral-600">Carregando agenda…</p>}

        {viewMode === "calendar" ? (
          <>
            <AgendaCalendar
              rangeMode={rangeMode}
              anchorDate={anchorDate}
              onAnchorChange={setAnchorDate}
              today={today}
              items={items}
              selectedDate={selectedDate}
              onSelectDate={setSelectedDate}
            />
            <div className="mt-4">
              {!selectedDate && (
                <p className="text-sm text-neutral-600">Toque em um dia para ver os detalhes.</p>
              )}
              {selectedDate && dayItems.length === 0 && (
                <p className="text-sm text-neutral-600">Nenhuma reserva ou bloqueio neste dia.</p>
              )}
              {selectedDate && dayItems.length > 0 && (
                <ul className="flex flex-col gap-3">
                  {dayItems.map((item) => (
                    <AgendaListItem
                      key={item.id}
                      item={item}
                      onOpen={(reservation) => setSelectedId(reservation.id)}
                      onRemoveBlock={(block) => setBlockToRemove(block)}
                    />
                  ))}
                </ul>
              )}
            </div>
          </>
        ) : (
          <>
            {!isLoading && items.length === 0 && (
              <p className="text-sm text-neutral-600">
                Nenhuma reserva ou bloqueio encontrado com estes filtros.
              </p>
            )}
            <ul className="flex flex-col gap-3">
              {items.map((item) => (
                <AgendaListItem
                  key={item.id}
                  item={item}
                  onOpen={(reservation) => setSelectedId(reservation.id)}
                  onRemoveBlock={(block) => setBlockToRemove(block)}
                />
              ))}
            </ul>

            {data && data.totalPages > 1 && (
              <div className="mt-4 flex items-center justify-between text-sm text-neutral-700">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                  className="rounded-md border border-neutral-300 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Anterior
                </button>
                <span>
                  Página {page + 1} de {data.totalPages}
                </span>
                <button
                  type="button"
                  disabled={page + 1 >= data.totalPages}
                  onClick={() => setPage((current) => current + 1)}
                  className="rounded-md border border-neutral-300 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  Próxima
                </button>
              </div>
            )}
          </>
        )}
      </div>

      {showingDetail && user && (
        <div className="mt-4 md:mt-0 md:w-96 md:shrink-0 md:sticky md:top-4">
          <ReservationDetailPanel id={selectedId} role={user.role} onClose={() => setSelectedId(null)} />
        </div>
      )}
      </div>

      <BlockFormDialog
        open={showBlockForm}
        today={today}
        onClose={() => setShowBlockForm(false)}
        onCreated={() => setShowBlockForm(false)}
      />

      <ConfirmDialog
        open={!!blockToRemove}
        title="Remover bloqueio"
        message={
          removeError ??
          "Tem certeza que deseja remover este bloqueio? A área volta a ficar disponível nesse horário."
        }
        confirmLabel="Remover bloqueio"
        isLoading={deleteBlock.isPending}
        onConfirm={() => void handleRemoveBlock()}
        onCancel={() => {
          setBlockToRemove(null);
          setRemoveError(null);
        }}
      />
    </div>
  );
}

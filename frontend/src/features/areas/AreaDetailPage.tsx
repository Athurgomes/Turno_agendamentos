/**
 * Detalhe da área (RF-ARE-04): fotos de vitrine, descrição, regras, conduta,
 * capacidade, horário por dia da semana, valor/WhatsApp. UNIT com área
 * `ACTIVE` vê "Reservar" (RN-14). SYNDIC/ADMIN veem os links de gestão;
 * só ADMIN altera status e exclui (RN-15, RN-16).
 */
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useSession } from "../auth/useSession";
import { AreaLifecycleDialog } from "./AreaLifecycleDialog";
import { useAreaQuery, useDeleteArea, useUpdateAreaStatus } from "./hooks";
import { WEEKDAYS } from "./areaFormSchema";
import { areaCategoryLabels, weekdayLabels } from "../../shared/utils/labels";
import { formatCurrency, formatTime } from "../../shared/utils/format";
import { formatPhone } from "../../shared/utils/format";
import { AreaStatusBadge } from "../../shared/components/AreaStatusBadge";
import type { AreaStatus } from "../../shared/api/types";

const STATUS_OPTIONS: AreaStatus[] = ["ACTIVE", "MAINTENANCE", "RENOVATION", "INTERDICTED", "INACTIVE"];

export function AreaDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useSession();
  const { data: area, isLoading } = useAreaQuery(id);
  const updateStatus = useUpdateAreaStatus(id ?? "");
  const deleteArea = useDeleteArea(id ?? "");
  const [statusModalOpen, setStatusModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  if (isLoading || !area) {
    return <p className="text-sm text-neutral-600">Carregando área…</p>;
  }

  const canReserve = user?.role === "UNIT" && area.status === "ACTIVE";
  const canManage = user?.role === "SYNDIC" || user?.role === "ADMIN";
  const isAdmin = user?.role === "ADMIN";
  const otherStatuses = STATUS_OPTIONS.filter((option) => option !== area.status);

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-6 flex flex-col gap-2">
        <div className="flex items-start justify-between gap-2">
          <div>
            <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">{area.name}</h1>
            <p className="text-sm text-neutral-600">{areaCategoryLabels[area.category]}</p>
          </div>
          <AreaStatusBadge status={area.status} />
        </div>

        <div className="flex flex-wrap gap-2">
          {canReserve && (
            <Link
              to={`/areas/${area.id}/reservar`}
              className="flex h-11 items-center justify-center rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700"
            >
              Reservar
            </Link>
          )}
          {canManage && (
            <Link
              to={`/areas/${area.id}/editar`}
              className="flex h-11 items-center justify-center rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
            >
              Editar
            </Link>
          )}
          {canManage && (
            <Link
              to={`/areas/${area.id}/fotos`}
              className="flex h-11 items-center justify-center rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
            >
              Fotos
            </Link>
          )}
          {canManage && (
            <Link
              to={`/areas/${area.id}/vistorias`}
              className="flex h-11 items-center justify-center rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
            >
              Vistorias
            </Link>
          )}
          {canManage && (
            <Link
              to={`/areas/${area.id}/comparar`}
              className="flex h-11 items-center justify-center rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
            >
              Comparar
            </Link>
          )}
          {isAdmin && (
            <button
              type="button"
              onClick={() => setStatusModalOpen(true)}
              className="flex h-11 items-center justify-center rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
            >
              Alterar status
            </button>
          )}
          {isAdmin && (
            <button
              type="button"
              onClick={() => setDeleteModalOpen(true)}
              className="flex h-11 items-center justify-center rounded-md border border-danger-700 px-4 text-sm font-medium text-danger-700 hover:bg-danger-50"
            >
              Excluir
            </button>
          )}
        </div>
      </div>

      {area.photos.length > 0 ? (
        <ul className="mb-6 flex gap-3 overflow-x-auto">
          {area.photos.map((photo) => (
            <li key={photo.id} className="shrink-0">
              <img
                src={photo.url}
                alt={photo.caption ?? `Foto de ${area.name}`}
                className="h-40 w-56 rounded-md object-cover"
              />
            </li>
          ))}
        </ul>
      ) : (
        <p className="mb-6 text-sm text-neutral-600">Esta área ainda não tem fotos de vitrine.</p>
      )}

      <section className="mb-6 flex flex-col gap-2">
        <h2 className="text-lg font-semibold text-neutral-900">Descrição</h2>
        <p className="max-w-[70ch] text-sm text-neutral-700">{area.description}</p>
      </section>

      <section className="mb-6 flex flex-col gap-2">
        <h2 className="text-lg font-semibold text-neutral-900">Regras de uso</h2>
        <p className="max-w-[70ch] whitespace-pre-line text-sm text-neutral-700">{area.rules}</p>
      </section>

      <section className="mb-6 flex flex-col gap-2">
        <h2 className="text-lg font-semibold text-neutral-900">Sugestões de conduta</h2>
        <p className="max-w-[70ch] whitespace-pre-line text-sm text-neutral-700">
          {area.conductGuidelines}
        </p>
      </section>

      <section className="mb-6 grid gap-3 sm:grid-cols-2">
        <div>
          <h2 className="text-lg font-semibold text-neutral-900">Capacidade</h2>
          <p className="text-sm text-neutral-700">{area.capacity} pessoas</p>
        </div>
        <div>
          <h2 className="text-lg font-semibold text-neutral-900">Valor</h2>
          <p className="text-sm text-neutral-700">
            {area.requiresPayment && area.price ? formatCurrency(area.price) : "Gratuita"}
          </p>
          {area.requiresPayment && area.paymentWhatsapp && (
            <p className="text-sm text-neutral-600">
              WhatsApp da administração: {formatPhone(area.paymentWhatsapp)}
            </p>
          )}
        </div>
      </section>

      <section className="mb-6 flex flex-col gap-2">
        <h2 className="text-lg font-semibold text-neutral-900">Horário de funcionamento</h2>
        <ul className="divide-y divide-neutral-200 rounded-md border border-neutral-200">
          {WEEKDAYS.map((day) => {
            const hours = area.openingHours.find((hour) => hour.dayOfWeek === day);
            return (
              <li key={day} className="flex items-center justify-between px-3 py-2 text-sm">
                <span className="font-medium text-neutral-700">{weekdayLabels[day]}</span>
                <span className="text-neutral-700">
                  {hours ? `${formatTime(hours.openTime)} – ${formatTime(hours.closeTime)}` : "Fechado"}
                </span>
              </li>
            );
          })}
        </ul>
      </section>

      <AreaLifecycleDialog
        open={statusModalOpen}
        title="Alterar status da área"
        description="Áreas com status diferente de Disponível não aceitam novas reservas (RN-14)."
        confirmLabel="Alterar status"
        statusOptions={otherStatuses}
        onCancel={() => setStatusModalOpen(false)}
        onSubmit={async (payload) => {
          const result = await updateStatus.mutateAsync({
            status: payload.status as AreaStatus,
            justification: payload.justification,
            confirmCancelAffected: payload.confirmCancelAffected,
          });
          setStatusModalOpen(false);
          return result;
        }}
      />

      <AreaLifecycleDialog
        open={deleteModalOpen}
        title="Excluir área"
        description={`Tem certeza que deseja excluir "${area.name}"? A área deixa de aparecer no catálogo.`}
        confirmLabel="Excluir"
        onCancel={() => setDeleteModalOpen(false)}
        onSubmit={async (payload) => {
          await deleteArea.mutateAsync({
            justification: payload.justification,
            confirmCancelAffected: payload.confirmCancelAffected,
          });
          setDeleteModalOpen(false);
          navigate("/areas");
        }}
      />
    </div>
  );
}

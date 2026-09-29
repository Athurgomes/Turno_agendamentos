/**
 * Lista/busca de unidades (RF-UNI-03): busca por número, bloco ou nome de
 * morador (debounce de 300ms), filtro por bloco, tabela em telas >= 768px e
 * cards empilhados no celular (DESIGN.md "Tabela/lista responsiva").
 */
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ConfirmDialog } from "../../shared/components/ConfirmDialog";
import { CredentialsModal } from "./CredentialsModal";
import {
  useDeactivateUnit,
  useResetUnitPassword,
  useUnitsQuery,
} from "./hooks";
import { formatPhone } from "../../shared/utils/format";
import { ApiError } from "../../shared/api/client";
import type { Credentials, ReservationSummary, UnitSummary } from "../../shared/api/types";

function statusBadge(active: boolean) {
  return active ? (
    <span className="rounded-pill bg-status-active-bg px-3 py-1 text-sm text-status-active-text">
      Ativa
    </span>
  ) : (
    <span className="rounded-pill bg-status-inactive-bg px-3 py-1 text-sm text-status-inactive-text">
      Desativada
    </span>
  );
}

export function UnitsListPage() {
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [block, setBlock] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timeout = setTimeout(() => {
      setSearch(searchInput.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timeout);
  }, [searchInput]);

  const { data, isLoading } = useUnitsQuery({ search: search || undefined, block: block || undefined, page });

  const deactivateUnit = useDeactivateUnit();
  const resetPassword = useResetUnitPassword();

  const [unitToDeactivate, setUnitToDeactivate] = useState<UnitSummary | null>(null);
  const [deactivateError, setDeactivateError] = useState<string | null>(null);
  const [affectedReservations, setAffectedReservations] = useState<ReservationSummary[]>([]);

  const [credentialsFor, setCredentialsFor] = useState<UnitSummary | null>(null);
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [resetError, setResetError] = useState<string | null>(null);

  async function handleConfirmDeactivate() {
    if (!unitToDeactivate) return;
    setDeactivateError(null);
    try {
      await deactivateUnit.mutateAsync(unitToDeactivate.id);
      setUnitToDeactivate(null);
    } catch (err) {
      if (err instanceof ApiError && err.code === "UNIT_HAS_FUTURE_RESERVATIONS") {
        setAffectedReservations(
          (err.problem.affectedReservations as ReservationSummary[] | undefined) ?? [],
        );
        setDeactivateError(err.detail);
      } else {
        setDeactivateError(
          err instanceof ApiError ? err.detail : "Não foi possível desativar a unidade. Tente novamente.",
        );
      }
    }
  }

  async function handleResetPassword(unit: UnitSummary) {
    setResetError(null);
    setCredentialsFor(unit);
    try {
      const result = await resetPassword.mutateAsync(unit.id);
      setCredentials(result);
    } catch (err) {
      setCredentialsFor(null);
      setResetError(
        err instanceof ApiError ? err.detail : "Não foi possível gerar uma nova senha. Tente novamente.",
      );
    }
  }

  const units = data?.content ?? [];

  return (
    <div>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">Unidades</h1>
          <p className="text-sm text-neutral-600">
            Cadastro, busca e gestão de acesso das unidades do condomínio.
          </p>
        </div>
        <Link
          to="/admin/unidades/nova"
          className="flex h-11 items-center justify-center rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700"
        >
          + Nova unidade
        </Link>
      </div>

      <div className="mb-4 flex flex-col gap-3 sm:flex-row">
        <div className="flex flex-1 flex-col gap-1">
          <label htmlFor="search" className="text-sm font-medium text-neutral-700">
            Buscar
          </label>
          <input
            id="search"
            placeholder="Número, identificador ou nome de morador"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
          />
        </div>
        <div className="flex flex-col gap-1 sm:w-48">
          <label htmlFor="block" className="text-sm font-medium text-neutral-700">
            Bloco
          </label>
          <input
            id="block"
            placeholder="Ex.: A"
            value={block}
            onChange={(event) => {
              setBlock(event.target.value);
              setPage(0);
            }}
            className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
          />
        </div>
      </div>

      {resetError && (
        <p role="alert" className="mb-4 rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700">
          {resetError}
        </p>
      )}

      {isLoading && <p className="text-sm text-neutral-600">Carregando unidades…</p>}

      {!isLoading && units.length === 0 && (
        <p className="text-sm text-neutral-600">
          Nenhuma unidade encontrada. Ajuste a busca ou cadastre uma nova unidade.
        </p>
      )}

      {/* Tabela — telas >= 768px */}
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-neutral-200 text-neutral-600">
              <th className="py-2 pr-4">Identificador</th>
              <th className="py-2 pr-4">Morador principal</th>
              <th className="py-2 pr-4">Telefone</th>
              <th className="py-2 pr-4">Moradores</th>
              <th className="py-2 pr-4">Status</th>
              <th className="py-2 pr-4">Ações</th>
            </tr>
          </thead>
          <tbody>
            {units.map((unit) => (
              <tr key={unit.id} className="border-b border-neutral-200">
                <td className="py-3 pr-4 font-medium text-neutral-900">{unit.identifier}</td>
                <td className="py-3 pr-4">{unit.primaryResident.name}</td>
                <td className="py-3 pr-4">{formatPhone(unit.primaryResident.phone)}</td>
                <td className="py-3 pr-4">{unit.residentsCount}</td>
                <td className="py-3 pr-4">{statusBadge(unit.active)}</td>
                <td className="py-3 pr-4">
                  <UnitActions
                    unit={unit}
                    onResetPassword={() => void handleResetPassword(unit)}
                    onDeactivate={() => setUnitToDeactivate(unit)}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Cards — celular */}
      <ul className="flex flex-col gap-3 md:hidden">
        {units.map((unit) => (
          <li key={unit.id} className="rounded-md border border-neutral-200 bg-neutral-0 p-4">
            <div className="flex items-center justify-between">
              <span className="font-medium text-neutral-900">{unit.identifier}</span>
              {statusBadge(unit.active)}
            </div>
            <dl className="mt-2 space-y-1 text-sm text-neutral-700">
              <div>
                <dt className="inline font-medium">Morador principal: </dt>
                <dd className="inline">{unit.primaryResident.name}</dd>
              </div>
              <div>
                <dt className="inline font-medium">Telefone: </dt>
                <dd className="inline">{formatPhone(unit.primaryResident.phone)}</dd>
              </div>
              <div>
                <dt className="inline font-medium">Moradores: </dt>
                <dd className="inline">{unit.residentsCount}</dd>
              </div>
            </dl>
            <div className="mt-3">
              <UnitActions
                unit={unit}
                onResetPassword={() => void handleResetPassword(unit)}
                onDeactivate={() => setUnitToDeactivate(unit)}
              />
            </div>
          </li>
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

      <ConfirmDialog
        open={!!unitToDeactivate}
        title="Desativar unidade"
        message={
          deactivateError ??
          `Tem certeza que deseja desativar a unidade ${unitToDeactivate?.identifier ?? ""}? O acesso da unidade é revogado imediatamente.`
        }
        confirmLabel="Desativar"
        isLoading={deactivateUnit.isPending}
        onConfirm={() => void handleConfirmDeactivate()}
        onCancel={() => {
          setUnitToDeactivate(null);
          setDeactivateError(null);
          setAffectedReservations([]);
        }}
      />

      {affectedReservations.length > 0 && (
        <ul className="mt-2 space-y-1 text-sm text-neutral-700">
          {affectedReservations.map((reservation) => (
            <li key={reservation.id}>
              {reservation.areaName} — {reservation.date} {reservation.startTime}
            </li>
          ))}
        </ul>
      )}

      <CredentialsModal
        open={!!credentials}
        credentials={credentials}
        residentName={credentialsFor?.primaryResident.name ?? ""}
        residentPhone={credentialsFor?.primaryResident.phone ?? ""}
        onClose={() => {
          setCredentials(null);
          setCredentialsFor(null);
        }}
      />
    </div>
  );
}

function UnitActions({
  unit,
  onResetPassword,
  onDeactivate,
}: {
  unit: UnitSummary;
  onResetPassword: () => void;
  onDeactivate: () => void;
}) {
  return (
    <div className="flex flex-wrap gap-2">
      <Link
        to={`/admin/unidades/${unit.id}/editar`}
        className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
      >
        Editar
      </Link>
      <Link
        to={`/admin/unidades/${unit.id}/transferir`}
        className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
      >
        Trocar titularidade
      </Link>
      <button
        type="button"
        onClick={onResetPassword}
        className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
      >
        Resetar senha
      </button>
      {unit.active && (
        <button
          type="button"
          onClick={onDeactivate}
          className="rounded-md border border-danger-700 px-3 py-1.5 text-sm font-medium text-danger-700 hover:bg-danger-50"
        >
          Desativar
        </button>
      )}
    </div>
  );
}

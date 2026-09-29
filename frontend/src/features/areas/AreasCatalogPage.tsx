/**
 * Catálogo de áreas comuns (RF-ARE-04, todos os perfis): fotos de vitrine,
 * categoria, capacidade, valor e status. Áreas não ativas aparecem com o
 * status e sem "Reservar" (RN-14). ADMIN vê "Nova área" (RF-ARE-01).
 */
import { useState } from "react";
import { Link } from "react-router-dom";
import { useSession } from "../auth/useSession";
import { useAreasQuery } from "./hooks";
import { areaCategoryLabels, areaStatusLabels } from "../../shared/utils/labels";
import { formatCurrency } from "../../shared/utils/format";
import { AreaStatusBadge } from "../../shared/components/AreaStatusBadge";
import type { AreaCategory, AreaStatus, AreaSummary } from "../../shared/api/types";

function AreaCard({ area }: { area: AreaSummary }) {
  const { user } = useSession();
  const canReserve = user?.role === "UNIT" && area.status === "ACTIVE";

  return (
    <li className="flex flex-col overflow-hidden rounded-lg border border-neutral-200 bg-neutral-0">
      <div className="aspect-video w-full bg-neutral-100">
        {area.coverPhotoUrl ? (
          <img
            src={area.coverPhotoUrl}
            alt={`Foto de ${area.name}`}
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-sm text-neutral-500">
            Sem foto
          </div>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="flex items-start justify-between gap-2">
          <div>
            <h2 className="text-lg font-semibold text-neutral-900">{area.name}</h2>
            <p className="text-sm text-neutral-600">{areaCategoryLabels[area.category]}</p>
          </div>
          <AreaStatusBadge status={area.status} />
        </div>
        <dl className="text-sm text-neutral-700">
          <div>
            <dt className="inline font-medium">Capacidade: </dt>
            <dd className="inline">{area.capacity} pessoas</dd>
          </div>
          <div>
            <dt className="inline font-medium">Valor: </dt>
            <dd className="inline">
              {area.requiresPayment && area.price ? formatCurrency(area.price) : "Gratuita"}
            </dd>
          </div>
        </dl>
        <div className="mt-auto flex flex-wrap gap-2 pt-2">
          <Link
            to={`/areas/${area.id}`}
            className="flex h-11 items-center rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
          >
            Ver detalhes
          </Link>
          {canReserve && (
            <Link
              to={`/areas/${area.id}/reservar`}
              className="flex h-11 items-center rounded-md bg-primary-600 px-3 text-sm font-medium text-white hover:bg-primary-700"
            >
              Reservar
            </Link>
          )}
        </div>
      </div>
    </li>
  );
}

export function AreasCatalogPage() {
  const { user } = useSession();
  const [category, setCategory] = useState<AreaCategory | "">("");
  const [status, setStatus] = useState<AreaStatus | "">("");

  const { data, isLoading } = useAreasQuery({
    category: category || undefined,
    status: status || undefined,
  });

  const areas = data ?? [];

  return (
    <div>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">Áreas comuns</h1>
          <p className="text-sm text-neutral-600">
            Consulte fotos, regras e disponibilidade das áreas do condomínio.
          </p>
        </div>
        {user?.role === "ADMIN" && (
          <Link
            to="/areas/nova"
            className="flex h-11 items-center justify-center rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700"
          >
            + Nova área
          </Link>
        )}
      </div>

      <div className="mb-4 flex flex-col gap-3 sm:flex-row">
        <div className="flex flex-1 flex-col gap-1">
          <label htmlFor="category" className="text-sm font-medium text-neutral-700">
            Categoria
          </label>
          <select
            id="category"
            value={category}
            onChange={(event) => setCategory(event.target.value as AreaCategory | "")}
            className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
          >
            <option value="">Todas</option>
            {Object.entries(areaCategoryLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-1 flex-col gap-1">
          <label htmlFor="status" className="text-sm font-medium text-neutral-700">
            Status
          </label>
          <select
            id="status"
            value={status}
            onChange={(event) => setStatus(event.target.value as AreaStatus | "")}
            className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
          >
            <option value="">Todos</option>
            {Object.entries(areaStatusLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {isLoading && <p className="text-sm text-neutral-600">Carregando áreas…</p>}

      {!isLoading && areas.length === 0 && (
        <p className="text-sm text-neutral-600">
          Nenhuma área encontrada com esses filtros. Ajuste a busca
          {user?.role === "ADMIN" ? " ou cadastre uma nova área." : "."}
        </p>
      )}

      <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {areas.map((area) => (
          <AreaCard key={area.id} area={area} />
        ))}
      </ul>
    </div>
  );
}

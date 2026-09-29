/**
 * Comparador de fotos (RF-ARE-08): duas datas ou duas vistorias da mesma
 * área lado a lado, para acompanhar degradação/conservação. Empilha as
 * colunas no celular.
 */
import { useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { useAreaPhotosQuery, useInspectionsQuery } from "./hooks";
import { formatDate } from "../../shared/utils/format";
import { inspectionConditionLabels } from "../../shared/utils/labels";
import type { InspectionDto, PhotoDto } from "../../shared/api/types";

type Mode = "dates" | "inspections";

function PhotoColumn({ label, photos }: { label: string; photos: PhotoDto[] }) {
  return (
    <div className="flex flex-1 flex-col gap-3 rounded-md border border-neutral-200 p-4">
      <h2 className="text-lg font-semibold text-neutral-900">{label}</h2>
      {photos.length === 0 ? (
        <p className="text-sm text-neutral-600">Nenhuma foto para esta seleção.</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {photos.map((photo) => (
            <li key={photo.id}>
              <img
                src={photo.url}
                alt={photo.caption ?? "Foto da área"}
                className="w-full rounded-sm object-cover"
              />
              {photo.caption && <p className="mt-1 text-sm text-neutral-600">{photo.caption}</p>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function AreaComparePage() {
  const { id } = useParams<{ id: string }>();
  const [mode, setMode] = useState<Mode>("dates");
  const { data: photos, isLoading: loadingPhotos } = useAreaPhotosQuery(id, {
    includeArchived: true,
  });
  const { data: inspections, isLoading: loadingInspections } = useInspectionsQuery(id);

  const dates = useMemo(() => {
    const unique = new Set((photos ?? []).map((photo) => photo.takenAt));
    return Array.from(unique).sort().reverse();
  }, [photos]);

  const [leftDate, setLeftDate] = useState("");
  const [rightDate, setRightDate] = useState("");
  const [leftInspectionId, setLeftInspectionId] = useState("");
  const [rightInspectionId, setRightInspectionId] = useState("");

  const isLoading = loadingPhotos || loadingInspections;
  const hasEnoughDates = dates.length >= 2;
  const hasEnoughInspections = (inspections ?? []).length >= 2;
  const canCompare = mode === "dates" ? hasEnoughDates : hasEnoughInspections;

  function photosForDate(date: string): PhotoDto[] {
    return (photos ?? []).filter((photo) => photo.takenAt === date);
  }

  function photosForInspection(inspectionId: string): PhotoDto[] {
    const inspection = (inspections ?? []).find(
      (item: InspectionDto) => item.id === inspectionId,
    );
    return inspection?.photos ?? [];
  }

  const leftPhotos = mode === "dates" ? photosForDate(leftDate) : photosForInspection(leftInspectionId);
  const rightPhotos =
    mode === "dates" ? photosForDate(rightDate) : photosForInspection(rightInspectionId);

  return (
    <div className="mx-auto max-w-4xl">
      <h1 className="mb-6 text-xl font-semibold text-neutral-900 sm:text-2xl">
        Comparador de fotos
      </h1>

      {isLoading && <p className="text-sm text-neutral-600">Carregando…</p>}

      {!isLoading && !canCompare && (
        <p className="text-sm text-neutral-600">
          Ainda não há {mode === "dates" ? "duas datas" : "duas vistorias"} com fotos suficientes
          para comparar. Adicione mais fotos {mode === "dates" ? "ao histórico" : "em vistorias"} da
          área para habilitar o comparador.
        </p>
      )}

      {!isLoading && (hasEnoughDates || hasEnoughInspections) && (
        <div className="mb-4 flex gap-2">
          <button
            type="button"
            onClick={() => setMode("dates")}
            className={`flex h-11 items-center rounded-md border px-3 text-sm font-medium ${
              mode === "dates"
                ? "border-primary-600 bg-primary-100 text-primary-800"
                : "border-neutral-300 text-neutral-700 hover:bg-neutral-100"
            }`}
          >
            Por data
          </button>
          <button
            type="button"
            onClick={() => setMode("inspections")}
            className={`flex h-11 items-center rounded-md border px-3 text-sm font-medium ${
              mode === "inspections"
                ? "border-primary-600 bg-primary-100 text-primary-800"
                : "border-neutral-300 text-neutral-700 hover:bg-neutral-100"
            }`}
          >
            Por vistoria
          </button>
        </div>
      )}

      {!isLoading && canCompare && mode === "dates" && (
        <div className="mb-4 grid gap-3 sm:grid-cols-2">
          <div className="flex flex-col gap-1">
            <label htmlFor="leftDate" className="text-sm font-medium text-neutral-700">
              Data (esquerda)
            </label>
            <select
              id="leftDate"
              value={leftDate}
              onChange={(event) => setLeftDate(event.target.value)}
              className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
            >
              <option value="">Selecione…</option>
              {dates.map((date) => (
                <option key={date} value={date}>
                  {formatDate(date)}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="rightDate" className="text-sm font-medium text-neutral-700">
              Data (direita)
            </label>
            <select
              id="rightDate"
              value={rightDate}
              onChange={(event) => setRightDate(event.target.value)}
              className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
            >
              <option value="">Selecione…</option>
              {dates.map((date) => (
                <option key={date} value={date}>
                  {formatDate(date)}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      {!isLoading && canCompare && mode === "inspections" && (
        <div className="mb-4 grid gap-3 sm:grid-cols-2">
          <div className="flex flex-col gap-1">
            <label htmlFor="leftInspection" className="text-sm font-medium text-neutral-700">
              Vistoria (esquerda)
            </label>
            <select
              id="leftInspection"
              value={leftInspectionId}
              onChange={(event) => setLeftInspectionId(event.target.value)}
              className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
            >
              <option value="">Selecione…</option>
              {(inspections ?? []).map((inspection) => (
                <option key={inspection.id} value={inspection.id}>
                  {formatDate(inspection.inspectedAt)} — {inspectionConditionLabels[inspection.overallCondition]}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="rightInspection" className="text-sm font-medium text-neutral-700">
              Vistoria (direita)
            </label>
            <select
              id="rightInspection"
              value={rightInspectionId}
              onChange={(event) => setRightInspectionId(event.target.value)}
              className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
            >
              <option value="">Selecione…</option>
              {(inspections ?? []).map((inspection) => (
                <option key={inspection.id} value={inspection.id}>
                  {formatDate(inspection.inspectedAt)} — {inspectionConditionLabels[inspection.overallCondition]}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      {!isLoading && canCompare && (
        <div className="flex flex-col gap-4 sm:flex-row">
          <PhotoColumn
            label={
              mode === "dates"
                ? leftDate
                  ? formatDate(leftDate)
                  : "Selecione a data à esquerda"
                : leftInspectionId
                  ? formatDate(
                      (inspections ?? []).find((item) => item.id === leftInspectionId)
                        ?.inspectedAt ?? "",
                    )
                  : "Selecione a vistoria à esquerda"
            }
            photos={leftPhotos}
          />
          <PhotoColumn
            label={
              mode === "dates"
                ? rightDate
                  ? formatDate(rightDate)
                  : "Selecione a data à direita"
                : rightInspectionId
                  ? formatDate(
                      (inspections ?? []).find((item) => item.id === rightInspectionId)
                        ?.inspectedAt ?? "",
                    )
                  : "Selecione a vistoria à direita"
            }
            photos={rightPhotos}
          />
        </div>
      )}
    </div>
  );
}

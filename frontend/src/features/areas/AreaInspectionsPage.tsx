/**
 * Vistorias da área (RF-ARE-07): lista por data decrescente e formulário de
 * nova vistoria (data, estado geral, observações e fotos).
 */
import { useState } from "react";
import { useParams } from "react-router-dom";
import { useCreateInspection, useInspectionsQuery } from "./hooks";
import { compressImage, validateImageFile } from "../../shared/utils/image";
import { photoCountErrorMessage } from "../../shared/utils/imageLimits";
import { formatDate } from "../../shared/utils/format";
import { inspectionConditionLabels } from "../../shared/utils/labels";
import { ApiError } from "../../shared/api/client";
import type { InspectionCondition } from "../../shared/api/types";

const CONDITIONS: InspectionCondition[] = ["GOOD", "FAIR", "POOR"];

export function AreaInspectionsPage() {
  const { id } = useParams<{ id: string }>();
  const { data: inspections, isLoading } = useInspectionsQuery(id);
  const createInspection = useCreateInspection(id ?? "");

  const [inspectedAt, setInspectedAt] = useState("");
  const [overallCondition, setOverallCondition] = useState<InspectionCondition>("GOOD");
  const [notes, setNotes] = useState("");
  const [photos, setPhotos] = useState<File[]>([]);
  const [formError, setFormError] = useState<string | null>(null);

  async function handleFileChange(fileList: FileList | null) {
    if (!fileList) return;
    setFormError(null);
    const files = Array.from(fileList);
    const countError = photoCountErrorMessage(files.length);
    if (countError) {
      setFormError(countError);
      return;
    }
    for (const file of files) {
      const reason = validateImageFile(file);
      if (reason) {
        setFormError(reason);
        return;
      }
    }
    setPhotos(await Promise.all(files.map((file) => compressImage(file))));
  }

  async function handleSubmit() {
    if (!inspectedAt) {
      setFormError("Informe a data da vistoria.");
      return;
    }
    setFormError(null);
    try {
      await createInspection.mutateAsync({
        data: { inspectedAt, overallCondition, notes: notes || undefined },
        photos,
      });
      setInspectedAt("");
      setOverallCondition("GOOD");
      setNotes("");
      setPhotos([]);
    } catch (err) {
      setFormError(
        err instanceof ApiError ? err.detail : "Não foi possível registrar a vistoria. Tente novamente.",
      );
    }
  }

  const items = inspections ?? [];

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-6 text-xl font-semibold text-neutral-900 sm:text-2xl">Vistorias</h1>

      <div className="mb-6 flex flex-col gap-3 rounded-md border border-neutral-200 bg-neutral-0 p-4">
        <h2 className="text-lg font-semibold text-neutral-900">Nova vistoria</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="flex flex-col gap-1">
            <label htmlFor="inspectedAt" className="text-sm font-medium text-neutral-700">
              Data
            </label>
            <input
              id="inspectedAt"
              type="date"
              value={inspectedAt}
              onChange={(event) => setInspectedAt(event.target.value)}
              className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="overallCondition" className="text-sm font-medium text-neutral-700">
              Estado geral
            </label>
            <select
              id="overallCondition"
              value={overallCondition}
              onChange={(event) => setOverallCondition(event.target.value as InspectionCondition)}
              className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
            >
              {CONDITIONS.map((condition) => (
                <option key={condition} value={condition}>
                  {inspectionConditionLabels[condition]}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="notes" className="text-sm font-medium text-neutral-700">
            Observações (opcional)
          </label>
          <textarea
            id="notes"
            rows={3}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            className="w-full max-w-[70ch] rounded-sm border border-neutral-300 bg-neutral-0 px-3 py-2 text-base text-neutral-900"
          />
        </div>
        <p className="text-sm text-neutral-600">Fotos são opcionais — até 10 por vistoria.</p>
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          aria-label="Fotos da vistoria"
          onChange={(event) => void handleFileChange(event.target.files)}
        />
        {formError && (
          <p role="alert" className="text-sm text-danger-700">
            {formError}
          </p>
        )}
        <button
          type="button"
          onClick={() => void handleSubmit()}
          disabled={createInspection.isPending}
          className="h-11 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto sm:self-start"
        >
          {createInspection.isPending ? "Registrando…" : "Registrar vistoria"}
        </button>
      </div>

      {isLoading && <p className="text-sm text-neutral-600">Carregando vistorias…</p>}

      {!isLoading && items.length === 0 && (
        <p className="text-sm text-neutral-600">Nenhuma vistoria registrada ainda.</p>
      )}

      <ul className="flex flex-col gap-3">
        {items.map((inspection) => (
          <li key={inspection.id} className="rounded-md border border-neutral-200 p-4">
            <div className="flex items-center justify-between">
              <span className="font-medium text-neutral-900">{formatDate(inspection.inspectedAt)}</span>
              <span className="rounded-pill bg-neutral-100 px-3 py-1 text-sm text-neutral-700">
                {inspectionConditionLabels[inspection.overallCondition]}
              </span>
            </div>
            <p className="mt-1 text-sm text-neutral-600">Vistoriado por {inspection.author.name}</p>
            {inspection.notes && <p className="mt-2 text-sm text-neutral-700">{inspection.notes}</p>}
            {inspection.photos.length > 0 && (
              <ul className="mt-3 flex gap-2">
                {inspection.photos.map((photo) => (
                  <li key={photo.id}>
                    <img
                      src={photo.url}
                      alt={photo.caption ?? "Foto da vistoria"}
                      className="h-16 w-16 rounded-sm object-cover"
                    />
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * Formulário de report (`/minhas-reservas/:id/reportar`, RF-REP-01): a janela
 * de report (RN-34) é calculada só pelo backend — o botão "Reportar" já só
 * aparece quando `canReport = true` (`MyReservationsPage`); este formulário
 * não conta a janela de novo. Categoria, descrição (mínimo 10 caracteres,
 * RN-35), morador responsável e até 5 fotos opcionais.
 */
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useReservationQuery } from "../reservations/hooks";
import { useMyUnitQuery } from "../units/hooks";
import { useCreateReport } from "./hooks";
import { ApiError } from "../../shared/api/client";
import { fieldClassName, textareaClassName } from "../../shared/components/formStyles";
import { reportCategoryLabels } from "../../shared/utils/labels";
import { selectAndCompressPhotos } from "../../shared/utils/image";
import { MAX_REPORT_PHOTOS } from "../../shared/utils/imageLimits";
import { formatDate } from "../../shared/utils/format";
import type { ReportCategory } from "../../shared/api/types";

const MIN_DESCRIPTION_LENGTH = 10;

export function ReportFormPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: reservation } = useReservationQuery(id);
  const { data: unit } = useMyUnitQuery();
  const createReport = useCreateReport();

  const [category, setCategory] = useState<ReportCategory | "">("");
  const [description, setDescription] = useState("");
  const [residentId, setResidentId] = useState("");
  const [photos, setPhotos] = useState<{ file: File; previewUrl: string }[]>([]);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [touched, setTouched] = useState(false);

  const trimmedDescription = description.trim();
  const descriptionTooShort = trimmedDescription.length < MIN_DESCRIPTION_LENGTH;
  const canSubmit = !!category && !!residentId && !descriptionTooShort;

  async function handlePhotoChange(fileList: FileList | null) {
    if (!fileList) return;
    setPhotoError(null);
    const result = await selectAndCompressPhotos(fileList, photos.length, MAX_REPORT_PHOTOS);
    if (!result.files) {
      setPhotoError(result.error);
      return;
    }
    setPhotos((current) => [
      ...current,
      ...result.files.map((file) => ({ file, previewUrl: URL.createObjectURL(file) })),
    ]);
  }

  function removePhoto(index: number) {
    setPhotos((current) => current.filter((_, photoIndex) => photoIndex !== index));
  }

  async function handleSubmit() {
    setTouched(true);
    if (!id || !canSubmit) return;
    setSubmitError(null);
    try {
      await createReport.mutateAsync({
        reservationId: id,
        payload: {
          category: category as ReportCategory,
          description: trimmedDescription,
          residentId,
        },
        photos: photos.map((photo) => photo.file),
      });
      navigate("/meus-reports");
    } catch (err) {
      setSubmitError(
        err instanceof ApiError ? err.detail : "Não foi possível enviar o report. Tente novamente.",
      );
    }
  }

  if (!unit) {
    return <p className="text-sm text-neutral-600">Carregando…</p>;
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-1 text-xl font-semibold text-neutral-900 sm:text-2xl">Reportar problema</h1>
      {reservation && (
        <p className="mb-4 text-sm text-neutral-600">
          {reservation.areaName} · {formatDate(reservation.date)}
        </p>
      )}

      <div aria-live="polite">
        {submitError && (
          <p
            role="alert"
            className="mb-4 rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700"
          >
            {submitError}
          </p>
        )}
      </div>

      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void handleSubmit();
        }}
        className="flex flex-col gap-4"
      >
        <div className="flex flex-col gap-1">
          <label htmlFor="report-category" className="text-sm font-medium text-neutral-700">
            Categoria
          </label>
          <select
            id="report-category"
            value={category}
            onChange={(event) => setCategory(event.target.value as ReportCategory)}
            className={fieldClassName(touched && !category)}
          >
            <option value="">Selecione…</option>
            {(Object.keys(reportCategoryLabels) as ReportCategory[]).map((option) => (
              <option key={option} value={option}>
                {reportCategoryLabels[option]}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="report-resident" className="text-sm font-medium text-neutral-700">
            Morador que reporta
          </label>
          <select
            id="report-resident"
            value={residentId}
            onChange={(event) => setResidentId(event.target.value)}
            className={fieldClassName(touched && !residentId)}
          >
            <option value="">Selecione…</option>
            {unit.residents.map((resident) => (
              <option key={resident.id} value={resident.id}>
                {resident.name}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="report-description" className="text-sm font-medium text-neutral-700">
            Descrição (mínimo 10 caracteres)
          </label>
          <textarea
            id="report-description"
            rows={4}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            className={textareaClassName(touched && descriptionTooShort)}
          />
          {touched && descriptionTooShort && (
            <p className="text-sm text-danger-700">
              Escreva pelo menos 10 caracteres para descrever o problema.
            </p>
          )}
        </div>

        <div className="flex flex-col gap-2">
          <span className="text-sm font-medium text-neutral-700">Fotos (opcional, até 5)</span>
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            aria-label="Selecionar fotos"
            onChange={(event) => void handlePhotoChange(event.target.files)}
          />
          {photoError && <p className="text-sm text-danger-700">{photoError}</p>}
          {photos.length > 0 && (
            <ul className="grid grid-cols-3 gap-2 sm:grid-cols-5">
              {photos.map((photo, index) => (
                <li key={photo.previewUrl} className="relative">
                  <img
                    src={photo.previewUrl}
                    alt=""
                    className="aspect-square w-full rounded-sm object-cover"
                  />
                  <button
                    type="button"
                    onClick={() => removePhoto(index)}
                    aria-label={`Remover foto ${index + 1}`}
                    className="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-full bg-neutral-900/70 text-xs text-white"
                  >
                    ×
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="mt-2 flex justify-end">
          <button
            type="submit"
            disabled={createReport.isPending}
            className="h-11 rounded-md bg-primary-600 px-6 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70"
          >
            {createReport.isPending ? "Enviando…" : "Enviar report"}
          </button>
        </div>
      </form>
    </div>
  );
}

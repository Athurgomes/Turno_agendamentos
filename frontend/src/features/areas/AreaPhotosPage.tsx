/**
 * Histórico de conservação da área (RF-ARE-06, RN-17): fotos por data
 * decrescente, com autor, legenda e vitrine. Upload para SYNDIC/ADMIN;
 * arquivar só para ADMIN (a foto arquivada não volta à vitrine).
 */
import { useState } from "react";
import { useParams } from "react-router-dom";
import { useSession } from "../auth/useSession";
import { ConfirmDialog } from "../../shared/components/ConfirmDialog";
import {
  useAreaPhotosQuery,
  useArchiveAreaPhoto,
  useUpdateAreaPhoto,
  useUploadAreaPhotos,
} from "./hooks";
import { compressImage, validateImageFile } from "../../shared/utils/image";
import { photoCountErrorMessage } from "../../shared/utils/imageLimits";
import { formatDate } from "../../shared/utils/format";
import { ApiError } from "../../shared/api/client";
import type { PhotoDto } from "../../shared/api/types";

export function AreaPhotosPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useSession();
  const isAdmin = user?.role === "ADMIN";

  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [includeArchived, setIncludeArchived] = useState(false);
  const { data: photos, isLoading } = useAreaPhotosQuery(id, {
    from: from || undefined,
    to: to || undefined,
    includeArchived,
  });

  const uploadPhotos = useUploadAreaPhotos(id ?? "");
  const updatePhoto = useUpdateAreaPhoto(id ?? "");
  const archivePhoto = useArchiveAreaPhoto(id ?? "");

  const [caption, setCaption] = useState("");
  const [takenAt, setTakenAt] = useState("");
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [photoToArchive, setPhotoToArchive] = useState<PhotoDto | null>(null);

  async function handleFileChange(fileList: FileList | null) {
    if (!fileList) return;
    setUploadError(null);
    const files = Array.from(fileList);
    const countError = photoCountErrorMessage(files.length);
    if (countError) {
      setUploadError(countError);
      return;
    }
    for (const file of files) {
      const reason = validateImageFile(file);
      if (reason) {
        setUploadError(reason);
        return;
      }
    }
    const compressed = await Promise.all(files.map((file) => compressImage(file)));
    setPendingFiles(compressed);
  }

  async function handleUpload() {
    if (pendingFiles.length === 0) {
      setUploadError("Selecione ao menos uma foto.");
      return;
    }
    setUploadError(null);
    try {
      await uploadPhotos.mutateAsync({
        photos: pendingFiles,
        params: { caption: caption || undefined, takenAt: takenAt || undefined },
      });
      setPendingFiles([]);
      setCaption("");
      setTakenAt("");
    } catch (err) {
      setUploadError(
        err instanceof ApiError ? err.detail : "Não foi possível enviar as fotos. Tente novamente.",
      );
    }
  }

  const items = photos ?? [];

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-6 text-xl font-semibold text-neutral-900 sm:text-2xl">
        Fotos e histórico de conservação
      </h1>

      <div className="mb-6 flex flex-col gap-3 rounded-md border border-neutral-200 bg-neutral-0 p-4 sm:flex-row sm:items-end">
        <div className="flex flex-col gap-1">
          <label htmlFor="photos-from" className="text-sm font-medium text-neutral-700">
            De
          </label>
          <input
            id="photos-from"
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
            className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="photos-to" className="text-sm font-medium text-neutral-700">
            Até
          </label>
          <input
            id="photos-to"
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
            className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
          />
        </div>
        <label className="flex items-center gap-2 text-sm font-medium text-neutral-700">
          <input
            type="checkbox"
            checked={includeArchived}
            onChange={(event) => setIncludeArchived(event.target.checked)}
          />
          Incluir arquivadas
        </label>
      </div>

      <div className="mb-6 flex flex-col gap-3 rounded-md border border-neutral-200 bg-neutral-0 p-4">
        <h2 className="text-lg font-semibold text-neutral-900">Adicionar fotos</h2>
        <p className="text-sm text-neutral-600">Envie de 1 a 10 fotos por vez.</p>
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          aria-label="Selecionar fotos"
          onChange={(event) => void handleFileChange(event.target.files)}
        />
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="flex flex-col gap-1">
            <label htmlFor="caption" className="text-sm font-medium text-neutral-700">
              Legenda (opcional)
            </label>
            <input
              id="caption"
              value={caption}
              onChange={(event) => setCaption(event.target.value)}
              className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="takenAt" className="text-sm font-medium text-neutral-700">
              Data da foto
            </label>
            <input
              id="takenAt"
              type="date"
              value={takenAt}
              onChange={(event) => setTakenAt(event.target.value)}
              className="h-11 rounded-sm border border-neutral-300 bg-neutral-0 px-3 text-base text-neutral-900"
            />
          </div>
        </div>
        {uploadError && (
          <p role="alert" className="text-sm text-danger-700">
            {uploadError}
          </p>
        )}
        <button
          type="button"
          onClick={() => void handleUpload()}
          disabled={uploadPhotos.isPending}
          className="h-11 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto sm:self-start"
        >
          {uploadPhotos.isPending ? "Enviando…" : "Enviar fotos"}
        </button>
      </div>

      {isLoading && <p className="text-sm text-neutral-600">Carregando fotos…</p>}

      {!isLoading && items.length === 0 && (
        <p className="text-sm text-neutral-600">
          Nenhuma foto encontrada nesse período. Ajuste o filtro ou adicione a primeira foto.
        </p>
      )}

      <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {items.map((photo) => (
          <li key={photo.id} className="flex flex-col gap-2 rounded-md border border-neutral-200 p-3">
            <img
              src={photo.url}
              alt={photo.caption ?? "Foto da área"}
              className="h-40 w-full rounded-sm object-cover"
            />
            <p className="text-xs text-neutral-600">
              {formatDate(photo.takenAt)}
              {photo.uploadedBy && ` — ${photo.uploadedBy.name}`}
            </p>
            {photo.caption && <p className="text-sm text-neutral-700">{photo.caption}</p>}
            <div className="flex flex-wrap items-center gap-2">
              {photo.featured && (
                <span className="rounded-pill bg-status-active-bg px-2 py-0.5 text-xs text-status-active-text">
                  Vitrine
                </span>
              )}
              {photo.archived && (
                <span className="rounded-pill bg-status-inactive-bg px-2 py-0.5 text-xs text-status-inactive-text">
                  Arquivada
                </span>
              )}
              {!photo.archived && (
                <button
                  type="button"
                  onClick={() =>
                    void updatePhoto.mutateAsync({
                      photoId: photo.id,
                      payload: { featured: !photo.featured },
                    })
                  }
                  className="flex h-11 items-center rounded-md border border-neutral-300 px-3 text-xs font-medium text-neutral-700 hover:bg-neutral-100"
                >
                  {photo.featured ? "Remover da vitrine" : "Marcar como vitrine"}
                </button>
              )}
              {isAdmin && !photo.archived && (
                <button
                  type="button"
                  onClick={() => setPhotoToArchive(photo)}
                  className="flex h-11 items-center rounded-md border border-danger-700 px-3 text-xs font-medium text-danger-700 hover:bg-danger-50"
                >
                  Arquivar
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>

      <ConfirmDialog
        open={!!photoToArchive}
        title="Arquivar foto"
        message="A foto arquivada some da vitrine e não pode mais ser marcada como vitrine novamente. Deseja continuar?"
        confirmLabel="Arquivar"
        isLoading={archivePhoto.isPending}
        onConfirm={() => {
          if (photoToArchive) void archivePhoto.mutateAsync(photoToArchive.id);
          setPhotoToArchive(null);
        }}
        onCancel={() => setPhotoToArchive(null)}
      />
    </div>
  );
}

/**
 * Detalhe do report para SÍNDICO/ADMIN (`/reports`, RF-REP-03/04): todos os
 * campos e comentários (marca "Só equipe" quando `visibleToResident = false`),
 * telefone e WhatsApp, mudança de status (RN-36, só oferece destinos válidos —
 * `reportRules.ts`) e fotos do reparo (`stage = REPAIR`).
 */
import { useState } from "react";
import {
  useAddReportComment,
  useAdminReportQuery,
  useUpdateReportStatus,
  useUploadReportPhotos,
} from "./hooks";
import { nextReportStatusOptions } from "./reportRules";
import { ReportStatusBadge } from "../../shared/components/ReportStatusBadge";
import { ReportStatusDialog, type ReportStatusDialogPayload } from "./ReportStatusDialog";
import { ApiError } from "../../shared/api/client";
import { useServerClock } from "../../shared/hooks/useServerClock";
import { formatCurrency, formatDate, formatDateTimeInstant, formatPhone } from "../../shared/utils/format";
import { reportCategoryLabels, reportStatusLabels } from "../../shared/utils/labels";
import { selectAndCompressPhotos } from "../../shared/utils/image";
import { MAX_REPORT_PHOTOS } from "../../shared/utils/imageLimits";
import { textareaClassName } from "../../shared/components/formStyles";
import type { ReportStatus } from "../../shared/api/types";

export interface ReportDetailPanelProps {
  id: string;
  onClose: () => void;
}

export function ReportDetailPanel({ id, onClose }: ReportDetailPanelProps) {
  const { data: report, isLoading, refetch } = useAdminReportQuery(id);
  const { data: clock } = useServerClock();
  const timezone = clock?.timezone ?? "America/Sao_Paulo";
  const updateStatus = useUpdateReportStatus();
  const addComment = useAddReportComment();
  const uploadPhotos = useUploadReportPhotos();

  const [targetStatus, setTargetStatus] = useState<ReportStatus | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [commentText, setCommentText] = useState("");
  const [visibleToResident, setVisibleToResident] = useState(true);
  const [commentError, setCommentError] = useState<string | null>(null);
  const [pendingPhotos, setPendingPhotos] = useState<File[]>([]);
  const [photoError, setPhotoError] = useState<string | null>(null);

  if (isLoading || !report) {
    return <p className="text-sm text-neutral-600">Carregando detalhe…</p>;
  }

  const statusOptions = nextReportStatusOptions(report.status);

  async function handleConfirmStatus(payload: ReportStatusDialogPayload) {
    if (!targetStatus) return;
    setStatusError(null);
    try {
      await updateStatus.mutateAsync({ id, payload: { status: targetStatus, ...payload } });
      setTargetStatus(null);
    } catch (err) {
      if (err instanceof ApiError) {
        setStatusError(err.detail);
        if (err.code === "INVALID_STATUS_TRANSITION") void refetch();
      } else {
        setStatusError("Não foi possível mudar o status. Tente novamente.");
      }
    }
  }

  async function handleAddComment() {
    const trimmed = commentText.trim();
    if (!trimmed) return;
    setCommentError(null);
    try {
      await addComment.mutateAsync({ id, payload: { text: trimmed, visibleToResident } });
      setCommentText("");
      setVisibleToResident(true);
    } catch (err) {
      setCommentError(
        err instanceof ApiError ? err.detail : "Não foi possível adicionar o comentário. Tente novamente.",
      );
    }
  }

  async function handlePhotoChange(fileList: FileList | null) {
    if (!fileList) return;
    setPhotoError(null);
    const result = await selectAndCompressPhotos(fileList, 0, MAX_REPORT_PHOTOS);
    if (!result.files) {
      setPhotoError(result.error);
      return;
    }
    setPendingPhotos(result.files);
  }

  async function handleUploadPhotos() {
    if (pendingPhotos.length === 0) {
      setPhotoError("Selecione ao menos uma foto do reparo.");
      return;
    }
    setPhotoError(null);
    try {
      await uploadPhotos.mutateAsync({ id, photos: pendingPhotos });
      setPendingPhotos([]);
    } catch (err) {
      setPhotoError(
        err instanceof ApiError ? err.detail : "Não foi possível enviar as fotos. Tente novamente.",
      );
    }
  }

  return (
    <div className="rounded-md border border-neutral-200 bg-neutral-0 p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-lg font-semibold text-neutral-900">{report.areaName}</p>
          <p className="text-sm text-neutral-600">{report.code}</p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="h-10 rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100 md:hidden"
        >
          Voltar
        </button>
      </div>

      <div className="mt-2">
        <ReportStatusBadge status={report.status} />
      </div>

      <dl className="mt-4 flex flex-col gap-2 text-sm text-neutral-700">
        <div className="flex justify-between gap-2">
          <dt className="text-neutral-600">Unidade</dt>
          <dd className="font-medium text-neutral-900">{report.unitIdentifier}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-neutral-600">Morador</dt>
          <dd className="font-medium text-neutral-900">{report.residentName}</dd>
        </div>
        {report.residentPhone && (
          <div className="flex justify-between gap-2">
            <dt className="text-neutral-600">Telefone</dt>
            <dd className="font-medium text-neutral-900">{formatPhone(report.residentPhone)}</dd>
          </div>
        )}
        <div className="flex justify-between gap-2">
          <dt className="text-neutral-600">Categoria</dt>
          <dd className="font-medium text-neutral-900">{reportCategoryLabels[report.category]}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-neutral-600">Data da reserva</dt>
          <dd className="font-medium text-neutral-900">{formatDate(report.reservationDate)}</dd>
        </div>
        {report.maintenanceCost != null && (
          <div className="flex justify-between gap-2">
            <dt className="text-neutral-600">Custo da manutenção</dt>
            <dd className="font-medium text-neutral-900">{formatCurrency(report.maintenanceCost)}</dd>
          </div>
        )}
        {report.statusReason && (
          <div className="flex justify-between gap-2">
            <dt className="text-neutral-600">Motivo do status</dt>
            <dd className="font-medium text-neutral-900">{report.statusReason}</dd>
          </div>
        )}
        <div className="flex justify-between gap-2">
          <dt className="text-neutral-600">Aberto em</dt>
          <dd className="font-medium text-neutral-900">
            {formatDateTimeInstant(report.createdAt, timezone)}
          </dd>
        </div>
        {report.resolvedAt && (
          <div className="flex justify-between gap-2">
            <dt className="text-neutral-600">Resolvido em</dt>
            <dd className="font-medium text-neutral-900">
              {formatDateTimeInstant(report.resolvedAt, timezone)}
            </dd>
          </div>
        )}
      </dl>

      <p className="mt-3 text-sm text-neutral-700">{report.description}</p>

      {report.photos.length > 0 && (
        <ul className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-5">
          {report.photos.map((photo) => (
            <li key={photo.id}>
              <img src={photo.url} alt="" className="aspect-square w-full rounded-sm object-cover" />
            </li>
          ))}
        </ul>
      )}

      <div className="mt-4 flex flex-wrap gap-2">
        {report.whatsappContactUrl && (
          <a
            href={report.whatsappContactUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="flex h-10 items-center justify-center rounded-md bg-primary-600 px-3 text-sm font-medium text-white hover:bg-primary-700"
          >
            Falar no WhatsApp
          </a>
        )}
        {statusOptions.map((option) => (
          <button
            key={option}
            type="button"
            onClick={() => {
              setStatusError(null);
              setTargetStatus(option);
            }}
            className="flex h-10 items-center justify-center rounded-md border border-neutral-300 px-3 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
          >
            {reportStatusLabels[option]}
          </button>
        ))}
      </div>

      <div className="mt-6">
        <h2 className="text-sm font-semibold text-neutral-900">Comentários</h2>
        {report.comments.length === 0 ? (
          <p className="mt-2 text-sm text-neutral-600">Nenhum comentário ainda.</p>
        ) : (
          <ul className="mt-2 flex flex-col gap-2">
            {report.comments.map((comment) => (
              <li key={comment.id} className="rounded-sm border border-neutral-200 p-3 text-sm">
                <p className="flex items-center gap-2 font-medium text-neutral-900">
                  {comment.authorName}
                  {!comment.visibleToResident && (
                    <span className="rounded-pill bg-neutral-200 px-2 py-0.5 text-xs font-medium text-neutral-700">
                      Só equipe
                    </span>
                  )}
                </p>
                <p className="mt-1 text-neutral-700">{comment.text}</p>
              </li>
            ))}
          </ul>
        )}

        <div className="mt-3 flex flex-col gap-2">
          <label htmlFor="report-comment" className="text-sm font-medium text-neutral-700">
            Novo comentário
          </label>
          <textarea
            id="report-comment"
            rows={2}
            value={commentText}
            onChange={(event) => setCommentText(event.target.value)}
            className={textareaClassName(false)}
          />
          <label className="flex items-center gap-2 text-sm text-neutral-700">
            <input
              type="checkbox"
              checked={visibleToResident}
              onChange={(event) => setVisibleToResident(event.target.checked)}
            />
            Visível ao morador
          </label>
          {commentError && <p className="text-sm text-danger-700">{commentError}</p>}
          <div>
            <button
              type="button"
              disabled={addComment.isPending || !commentText.trim()}
              onClick={() => void handleAddComment()}
              className="h-10 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70"
            >
              {addComment.isPending ? "Enviando…" : "Comentar"}
            </button>
          </div>
        </div>
      </div>

      <div className="mt-6">
        <h2 className="text-sm font-semibold text-neutral-900">Fotos do reparo</h2>
        <p className="mt-1 text-sm text-neutral-600">Envie de 1 a 5 fotos do reparo realizado.</p>
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          aria-label="Selecionar fotos do reparo"
          onChange={(event) => void handlePhotoChange(event.target.files)}
        />
        {photoError && <p className="mt-1 text-sm text-danger-700">{photoError}</p>}
        <div className="mt-2">
          <button
            type="button"
            disabled={uploadPhotos.isPending}
            onClick={() => void handleUploadPhotos()}
            className="h-10 rounded-md border border-neutral-300 px-4 text-sm font-medium text-neutral-700 hover:bg-neutral-100 disabled:cursor-not-allowed disabled:opacity-70"
          >
            {uploadPhotos.isPending ? "Enviando…" : "Enviar fotos do reparo"}
          </button>
        </div>
      </div>

      <ReportStatusDialog
        open={!!targetStatus}
        targetStatus={targetStatus}
        error={statusError}
        isLoading={updateStatus.isPending}
        onConfirm={(payload) => void handleConfirmStatus(payload)}
        onCancel={() => {
          setTargetStatus(null);
          setStatusError(null);
        }}
      />
    </div>
  );
}

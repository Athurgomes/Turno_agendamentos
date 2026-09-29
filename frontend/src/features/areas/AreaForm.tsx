/**
 * Formulário completo de área (ADMIN): cadastro (`/areas/nova`) e edição
 * (`/areas/:id/editar`). RN-11 (campos obrigatórios, ≥ 1 foto no cadastro),
 * RN-12 (pagamento), RN-13 (template de categoria, RF-ARE-03), RN-17 (fotos).
 */
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ConfirmDialog } from "../../shared/components/ConfirmDialog";
import { fieldClassName, textareaClassName } from "../../shared/components/formStyles";
import { areaCategoryLabels, weekdayLabels } from "../../shared/utils/labels";
import { compressImage, validateImageFile } from "../../shared/utils/image";
import { photoCountErrorMessage } from "../../shared/utils/imageLimits";
import {
  WEEKDAYS,
  areaFormSchema,
  type AreaFormInput,
  type AreaFormValues,
} from "./areaFormSchema";
import type { AreaCategory, AreaCategoryTemplateDto } from "../../shared/api/types";

export interface AreaFormProps {
  categories: AreaCategoryTemplateDto[];
  defaultValues: AreaFormValues;
  /** Fotos só existem no cadastro — a edição não envia fotos (D-44). */
  showPhotos: boolean;
  defaultPaymentWhatsapp?: string;
  submitLabel: string;
  submittingLabel: string;
  onSubmit: (values: AreaFormValues, photos: File[]) => Promise<void>;
  formError?: string | null;
}

export function AreaForm({
  categories,
  defaultValues,
  showPhotos,
  defaultPaymentWhatsapp,
  submitLabel,
  submittingLabel,
  onSubmit,
  formError,
}: AreaFormProps) {
  const {
    register,
    handleSubmit,
    control,
    setValue,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<AreaFormInput, unknown, AreaFormValues>({
    resolver: zodResolver(areaFormSchema),
    defaultValues,
  });

  const [templateDirty, setTemplateDirty] = useState(
    !!(defaultValues.rules || defaultValues.conductGuidelines),
  );
  const [pendingCategory, setPendingCategory] = useState<AreaCategory | null>(null);
  const [photos, setPhotos] = useState<{ file: File; previewUrl: string }[]>([]);
  const [photoError, setPhotoError] = useState<string | null>(null);

  const category = useWatch({ control, name: "category" });
  const requiresPayment = useWatch({ control, name: "requiresPayment" });
  const schedule = useWatch({ control, name: "schedule" });
  const scheduleErrorMessage =
    errors.schedule && "message" in errors.schedule ? errors.schedule.message : undefined;

  function applyTemplate(category: AreaCategory) {
    const template = categories.find((item) => item.code === category);
    setValue("category", category);
    if (template) {
      setValue("rules", template.rulesTemplate, { shouldDirty: true });
      setValue("conductGuidelines", template.conductTemplate, { shouldDirty: true });
    }
    setTemplateDirty(false);
  }

  function handleCategoryChange(category: AreaCategory) {
    if (!templateDirty) {
      applyTemplate(category);
      return;
    }
    setPendingCategory(category);
  }

  function handleRequiresPaymentChange(checked: boolean) {
    setValue("requiresPayment", checked);
    if (checked && !getValues("paymentWhatsapp") && defaultPaymentWhatsapp) {
      setValue("paymentWhatsapp", defaultPaymentWhatsapp);
    }
  }

  async function handlePhotoInputChange(fileList: FileList | null) {
    if (!fileList) return;
    setPhotoError(null);
    const files = Array.from(fileList);
    const countError = photoCountErrorMessage(photos.length + files.length);
    if (countError) {
      setPhotoError(countError);
      return;
    }
    for (const file of files) {
      const invalidReason = validateImageFile(file);
      if (invalidReason) {
        setPhotoError(invalidReason);
        return;
      }
    }
    const compressed = await Promise.all(files.map((file) => compressImage(file)));
    setPhotos((current) => [
      ...current,
      ...compressed.map((file) => ({ file, previewUrl: URL.createObjectURL(file) })),
    ]);
  }

  function removePhoto(index: number) {
    setPhotos((current) => current.filter((_, photoIndex) => photoIndex !== index));
  }

  const photosMissing = showPhotos && photos.length === 0;

  async function submit(values: AreaFormValues) {
    if (photosMissing) {
      setPhotoError("Adicione ao menos uma foto da área.");
      return;
    }
    await onSubmit(
      values,
      photos.map((photo) => photo.file),
    );
  }

  return (
    <form
      noValidate
      onSubmit={(event) => void handleSubmit(submit)(event)}
      className="flex flex-col gap-6"
    >
      <div aria-live="polite">
        {formError && (
          <p
            role="alert"
            className="rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700"
          >
            {formError}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="name" className="text-sm font-medium text-neutral-700">
          Nome da área
        </label>
        <input
          id="name"
          className={fieldClassName(!!errors.name)}
          aria-invalid={!!errors.name}
          {...register("name")}
        />
        {errors.name && <p className="text-sm text-danger-700">{errors.name.message}</p>}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="category" className="text-sm font-medium text-neutral-700">
          Categoria
        </label>
        <select
          id="category"
          className={fieldClassName(!!errors.category)}
          value={category ?? ""}
          onChange={(event) => handleCategoryChange(event.target.value as AreaCategory)}
        >
          <option value="">Selecione…</option>
          {Object.entries(areaCategoryLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        {errors.category && <p className="text-sm text-danger-700">{errors.category.message}</p>}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="description" className="text-sm font-medium text-neutral-700">
          Descrição
        </label>
        <textarea
          id="description"
          rows={3}
          className={textareaClassName(!!errors.description)}
          {...register("description")}
        />
        {errors.description && (
          <p className="text-sm text-danger-700">{errors.description.message}</p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="rules" className="text-sm font-medium text-neutral-700">
          Regras de uso
        </label>
        <textarea
          id="rules"
          rows={4}
          className={textareaClassName(!!errors.rules)}
          {...register("rules", { onChange: () => setTemplateDirty(true) })}
        />
        {errors.rules && <p className="text-sm text-danger-700">{errors.rules.message}</p>}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="conductGuidelines" className="text-sm font-medium text-neutral-700">
          Sugestões de conduta
        </label>
        <textarea
          id="conductGuidelines"
          rows={4}
          className={textareaClassName(!!errors.conductGuidelines)}
          {...register("conductGuidelines", { onChange: () => setTemplateDirty(true) })}
        />
        {errors.conductGuidelines && (
          <p className="text-sm text-danger-700">{errors.conductGuidelines.message}</p>
        )}
      </div>

      <div className="flex flex-col gap-1 sm:w-48">
        <label htmlFor="capacity" className="text-sm font-medium text-neutral-700">
          Capacidade máxima (pessoas)
        </label>
        <input
          id="capacity"
          type="number"
          min={1}
          className={fieldClassName(!!errors.capacity)}
          {...register("capacity")}
        />
        {errors.capacity && <p className="text-sm text-danger-700">{errors.capacity.message}</p>}
      </div>

      <fieldset className="flex flex-col gap-3 rounded-md border border-neutral-200 p-4">
        <legend className="px-1 text-sm font-medium text-neutral-700">Horário de funcionamento</legend>
        {scheduleErrorMessage && (
          <p role="alert" className="text-sm text-danger-700">
            {scheduleErrorMessage}
          </p>
        )}
        {WEEKDAYS.map((day, index) => {
          const dayOpen = schedule?.[index]?.open;
          const openError = errors.schedule?.[index]?.openTime?.message;
          const closeError = errors.schedule?.[index]?.closeTime?.message;
          return (
            <div key={day} className="grid grid-cols-1 items-center gap-2 sm:grid-cols-4">
              <label className="flex items-center gap-2 text-sm font-medium text-neutral-700">
                <input type="checkbox" {...register(`schedule.${index}.open`)} />
                {weekdayLabels[day]}
              </label>
              <div className="flex flex-col gap-1">
                <input
                  type="time"
                  step={1800}
                  disabled={!dayOpen}
                  aria-label={`Abertura — ${weekdayLabels[day]}`}
                  className={fieldClassName(!!openError)}
                  {...register(`schedule.${index}.openTime`)}
                />
                {openError && <p className="text-sm text-danger-700">{openError}</p>}
              </div>
              <div className="flex flex-col gap-1">
                <input
                  type="time"
                  step={1800}
                  disabled={!dayOpen}
                  aria-label={`Fechamento — ${weekdayLabels[day]}`}
                  className={fieldClassName(!!closeError)}
                  {...register(`schedule.${index}.closeTime`)}
                />
                {closeError && <p className="text-sm text-danger-700">{closeError}</p>}
              </div>
              {!dayOpen && <span className="text-sm text-neutral-500">Fechado</span>}
            </div>
          );
        })}
      </fieldset>

      <fieldset className="flex flex-col gap-3 rounded-md border border-neutral-200 p-4">
        <legend className="px-1 text-sm font-medium text-neutral-700">Cobrança</legend>
        <label className="flex items-center gap-2 text-sm font-medium text-neutral-700">
          <input
            type="checkbox"
            checked={requiresPayment}
            onChange={(event) => handleRequiresPaymentChange(event.target.checked)}
          />
          Exige pagamento
        </label>
        {requiresPayment && (
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1">
              <label htmlFor="price" className="text-sm font-medium text-neutral-700">
                Valor (R$)
              </label>
              <input
                id="price"
                type="number"
                min={0}
                step="0.01"
                className={fieldClassName(!!errors.price)}
                {...register("price")}
              />
              {errors.price && <p className="text-sm text-danger-700">{errors.price.message}</p>}
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="paymentWhatsapp" className="text-sm font-medium text-neutral-700">
                WhatsApp da administração
              </label>
              <input
                id="paymentWhatsapp"
                className={fieldClassName(!!errors.paymentWhatsapp)}
                {...register("paymentWhatsapp")}
              />
              {errors.paymentWhatsapp && (
                <p className="text-sm text-danger-700">{errors.paymentWhatsapp.message}</p>
              )}
            </div>
          </div>
        )}
      </fieldset>

      {showPhotos && (
        <fieldset className="flex flex-col gap-3 rounded-md border border-neutral-200 p-4">
          <legend className="px-1 text-sm font-medium text-neutral-700">Fotos (1 a 10)</legend>
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            aria-label="Adicionar fotos"
            onChange={(event) => void handlePhotoInputChange(event.target.files)}
          />
          {photoError && (
            <p role="alert" className="text-sm text-danger-700">
              {photoError}
            </p>
          )}
          {photos.length > 0 && (
            <ul className="flex flex-wrap gap-3">
              {photos.map((photo, index) => (
                <li key={photo.previewUrl} className="relative">
                  <img
                    src={photo.previewUrl}
                    alt={`Pré-visualização ${index + 1}`}
                    className="h-24 w-24 rounded-sm object-cover"
                  />
                  <button
                    type="button"
                    onClick={() => removePhoto(index)}
                    aria-label={`Remover foto ${index + 1}`}
                    className="absolute -right-2 -top-2 flex h-11 w-11 items-center justify-center rounded-full bg-neutral-900 text-lg leading-none text-white"
                  >
                    <span aria-hidden="true">×</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </fieldset>
      )}

      <button
        type="submit"
        disabled={isSubmitting}
        className="h-11 w-full rounded-md bg-primary-600 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto sm:self-start sm:px-6"
      >
        {isSubmitting ? submittingLabel : submitLabel}
      </button>

      <ConfirmDialog
        open={!!pendingCategory}
        title="Substituir regras e conduta?"
        message="Esta área já tem regras e sugestões de conduta preenchidas. Substituir pelo texto padrão da categoria escolhida?"
        confirmLabel="Substituir"
        onConfirm={() => {
          if (pendingCategory) applyTemplate(pendingCategory);
          setPendingCategory(null);
        }}
        onCancel={() => {
          if (pendingCategory) setValue("category", pendingCategory);
          setPendingCategory(null);
        }}
      />
    </form>
  );
}

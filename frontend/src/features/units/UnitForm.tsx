/**
 * Formulário de unidade (RF-UNI-01, RF-UNI-03): cadastro mostra bloco/número
 * + moradores; edição mostra só os moradores (bloco/número não são
 * editáveis, D-43). Reaproveitado também pela troca de titularidade, que usa
 * só a lista de moradores (`showUnitFields = false`).
 */
import { useForm, useFieldArray, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { fieldClassName } from "../../shared/components/formStyles";
import { ResidentFields } from "./ResidentFields";
import { emptyResident, unitFormSchema, type UnitFormValues } from "./residentForm";

export interface UnitFormProps {
  defaultValues: UnitFormValues;
  /** Bloco/número só aparecem no cadastro; na edição são fixos (D-43). */
  showUnitFields: boolean;
  submitLabel: string;
  submittingLabel: string;
  onSubmit: (values: UnitFormValues) => Promise<void>;
  formError?: string | null;
}

export function UnitForm({
  defaultValues,
  showUnitFields,
  submitLabel,
  submittingLabel,
  onSubmit,
  formError,
}: UnitFormProps) {
  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<UnitFormValues>({
    resolver: zodResolver(unitFormSchema),
    defaultValues,
  });

  const { fields, append, remove } = useFieldArray({ control, name: "residents" });
  const residents = useWatch({ control, name: "residents" });

  function handleSetPrimary(targetIndex: number) {
    fields.forEach((_, index) => {
      setValue(`residents.${index}.primary`, index === targetIndex, {
        shouldDirty: true,
      });
    });
  }

  function handleAddResident() {
    append(emptyResident(false));
  }

  function handleRemove(index: number) {
    remove(index);
  }

  const residentsError =
    errors.residents && "message" in errors.residents ? errors.residents.message : undefined;

  return (
    <form
      noValidate
      onSubmit={(event) => void handleSubmit(onSubmit)(event)}
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

      {showUnitFields && (
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="flex flex-col gap-1">
            <label htmlFor="block" className="text-sm font-medium text-neutral-700">
              Bloco/torre (opcional)
            </label>
            <input
              id="block"
              placeholder="A"
              className={fieldClassName(!!errors.block)}
              {...register("block")}
            />
            {errors.block && (
              <p className="text-sm text-danger-700">{errors.block.message}</p>
            )}
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="number" className="text-sm font-medium text-neutral-700">
              Número da unidade
            </label>
            <input
              id="number"
              className={fieldClassName(!!errors.number)}
              aria-invalid={!!errors.number}
              {...register("number")}
            />
            {errors.number && (
              <p className="text-sm text-danger-700">{errors.number.message}</p>
            )}
          </div>
        </div>
      )}

      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-neutral-900">Moradores</h2>
          <button
            type="button"
            onClick={handleAddResident}
            className="rounded-md border border-neutral-300 px-3 py-1.5 text-sm font-medium text-neutral-700 hover:bg-neutral-100"
          >
            + Adicionar morador
          </button>
        </div>

        {residentsError && (
          <p role="alert" className="text-sm text-danger-700">
            {residentsError}
          </p>
        )}

        {fields.map((field, index) => (
          <ResidentFields
            key={field.id}
            index={index}
            register={register}
            errors={errors}
            isPrimary={!!residents?.[index]?.primary}
            onSetPrimary={handleSetPrimary}
            onRemove={handleRemove}
            canRemove={fields.length > 1}
          />
        ))}
      </div>

      <button
        type="submit"
        disabled={isSubmitting}
        className="h-11 w-full rounded-md bg-primary-600 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto sm:self-start sm:px-6"
      >
        {isSubmitting ? submittingLabel : submitLabel}
      </button>
    </form>
  );
}

/**
 * Edição de área pelo SYNDIC (D-20): só descrição, regras, conduta e
 * capacidade — nenhum campo de horário, cobrança ou status aparece aqui.
 */
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { fieldClassName, textareaClassName } from "../../shared/components/formStyles";
import {
  syndicAreaEditSchema,
  type SyndicAreaEditInput,
  type SyndicAreaEditValues,
} from "./areaFormSchema";

export interface SyndicAreaEditFormProps {
  defaultValues: SyndicAreaEditValues;
  onSubmit: (values: SyndicAreaEditValues) => Promise<void>;
  formError?: string | null;
}

export function SyndicAreaEditForm({
  defaultValues,
  onSubmit,
  formError,
}: SyndicAreaEditFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<SyndicAreaEditInput, unknown, SyndicAreaEditValues>({
    resolver: zodResolver(syndicAreaEditSchema),
    defaultValues,
  });

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
          {...register("rules")}
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
          {...register("conductGuidelines")}
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

      <button
        type="submit"
        disabled={isSubmitting}
        className="h-11 w-full rounded-md bg-primary-600 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto sm:self-start sm:px-6"
      >
        {isSubmitting ? "Salvando…" : "Salvar alterações"}
      </button>
    </form>
  );
}

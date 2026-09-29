/**
 * Linha de morador dentro do `useFieldArray` de `UnitForm`/`TransferOwnershipPage`.
 * O morador principal (rádio marcado) exige e-mail e CPF; os demais só nome e
 * telefone (RN-07). Marcar outro morador como principal é permitido na
 * edição/troca de titularidade (só ADMIN, RN-09).
 */
import type {
  FieldErrors,
  UseFormRegister,
} from "react-hook-form";
import { fieldClassName } from "../../shared/components/formStyles";
import type { ResidentInput } from "./residentForm";

interface FormShape {
  residents: ResidentInput[];
}

export interface ResidentFieldsProps {
  index: number;
  register: UseFormRegister<FormShape>;
  errors: FieldErrors<FormShape>;
  isPrimary: boolean;
  onSetPrimary: (index: number) => void;
  onRemove: (index: number) => void;
  canRemove: boolean;
}

export function ResidentFields({
  index,
  register,
  errors,
  isPrimary,
  onSetPrimary,
  onRemove,
  canRemove,
}: ResidentFieldsProps) {
  const residentErrors = errors.residents?.[index];
  const idPrefix = `resident-${index}`;

  return (
    <fieldset className="rounded-md border border-neutral-200 p-4">
      <legend className="px-1 text-sm font-medium text-neutral-700">
        {index === 0 ? "Morador" : `Morador adicional ${index}`}
      </legend>

      <label className="mb-3 flex items-center gap-2 text-sm text-neutral-700">
        <input
          type="radio"
          name="primary-resident"
          checked={isPrimary}
          onChange={() => onSetPrimary(index)}
        />
        Morador principal
      </label>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label htmlFor={`${idPrefix}-name`} className="text-sm font-medium text-neutral-700">
            Nome
          </label>
          <input
            id={`${idPrefix}-name`}
            className={fieldClassName(!!residentErrors?.name)}
            aria-invalid={!!residentErrors?.name}
            aria-describedby={residentErrors?.name ? `${idPrefix}-name-error` : undefined}
            {...register(`residents.${index}.name`)}
          />
          {residentErrors?.name && (
            <p id={`${idPrefix}-name-error`} className="text-sm text-danger-700">
              {residentErrors.name.message}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={`${idPrefix}-phone`} className="text-sm font-medium text-neutral-700">
            Telefone (com DDI, só dígitos)
          </label>
          <input
            id={`${idPrefix}-phone`}
            inputMode="numeric"
            placeholder="5562999998888"
            className={fieldClassName(!!residentErrors?.phone)}
            aria-invalid={!!residentErrors?.phone}
            aria-describedby={residentErrors?.phone ? `${idPrefix}-phone-error` : undefined}
            {...register(`residents.${index}.phone`)}
          />
          {residentErrors?.phone && (
            <p id={`${idPrefix}-phone-error`} className="text-sm text-danger-700">
              {residentErrors.phone.message}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={`${idPrefix}-email`} className="text-sm font-medium text-neutral-700">
            E-mail{isPrimary ? "" : " (opcional)"}
          </label>
          <input
            id={`${idPrefix}-email`}
            type="email"
            className={fieldClassName(!!residentErrors?.email)}
            aria-invalid={!!residentErrors?.email}
            aria-describedby={residentErrors?.email ? `${idPrefix}-email-error` : undefined}
            {...register(`residents.${index}.email`)}
          />
          {residentErrors?.email && (
            <p id={`${idPrefix}-email-error`} className="text-sm text-danger-700">
              {residentErrors.email.message}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor={`${idPrefix}-cpf`} className="text-sm font-medium text-neutral-700">
            CPF{isPrimary ? "" : " (opcional)"}
          </label>
          <input
            id={`${idPrefix}-cpf`}
            inputMode="numeric"
            placeholder="52998224725"
            className={fieldClassName(!!residentErrors?.cpf)}
            aria-invalid={!!residentErrors?.cpf}
            aria-describedby={residentErrors?.cpf ? `${idPrefix}-cpf-error` : undefined}
            {...register(`residents.${index}.cpf`)}
          />
          {residentErrors?.cpf && (
            <p id={`${idPrefix}-cpf-error`} className="text-sm text-danger-700">
              {residentErrors.cpf.message}
            </p>
          )}
        </div>
      </div>

      {canRemove && (
        <button
          type="button"
          onClick={() => onRemove(index)}
          className="mt-3 text-sm font-medium text-danger-700 hover:underline"
        >
          Remover morador
        </button>
      )}
    </fieldset>
  );
}

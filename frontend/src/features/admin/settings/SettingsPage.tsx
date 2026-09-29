/**
 * Configurações do condomínio (RF-UNI-08, RN-20/21/22/30/34): parâmetros de
 * `condominium_settings` que regem antecedência, limites por unidade e prazo
 * de reports. `timezone` e `slotMinutes` são só leitura no MVP (D-15).
 */
import { useEffect, useState } from "react";
import { useForm, useWatch, type UseFormRegister } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ApiError } from "../../../shared/api/client";
import { fieldClassName } from "../../../shared/components/formStyles";
import { useSettingsQuery, useUpdateSettings } from "./hooks";
import {
  buildSettingsPayload,
  settingsFormSchema,
  settingsToFormValues,
  type SettingsFormInput,
  type SettingsFormValues,
} from "./settingsForm";

interface FieldProps {
  id: keyof SettingsFormInput;
  label: string;
  help: string;
  register: UseFormRegister<SettingsFormInput>;
  type?: string;
  error?: string;
}

/** Fora do componente pai (react-hooks/static-components): recriar em cada render perde estado. */
function Field({ id, label, help, register, type = "text", error }: FieldProps) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-neutral-700">
        {label}
      </label>
      <input
        id={id}
        type={type}
        className={fieldClassName(!!error)}
        aria-invalid={!!error}
        aria-describedby={`${id}-help`}
        {...register(id)}
      />
      <p id={`${id}-help`} className="text-sm text-neutral-600">
        {help}
      </p>
      {error && <p className="text-sm text-danger-700">{error}</p>}
    </div>
  );
}

export function SettingsPage() {
  const { data: settings, isLoading } = useSettingsQuery();
  const updateSettings = useUpdateSettings();
  const [formError, setFormError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
  } = useForm<SettingsFormInput, unknown, SettingsFormValues>({
    resolver: zodResolver(settingsFormSchema),
  });

  useEffect(() => {
    if (settings) reset(settingsToFormValues(settings));
  }, [settings, reset]);

  const windowStart = useWatch({ control, name: "nextDayWindowStart" });
  const windowEnd = useWatch({ control, name: "nextDayWindowEnd" });

  async function onSubmit(values: SettingsFormValues) {
    if (!settings) return;
    setFormError(null);
    setSaved(false);
    try {
      await updateSettings.mutateAsync(buildSettingsPayload(values, settings));
      setSaved(true);
    } catch (err) {
      setFormError(
        err instanceof ApiError ? err.detail : "Não foi possível salvar as configurações. Tente novamente.",
      );
    }
  }

  if (isLoading) {
    return <p className="text-sm text-neutral-600">Carregando configurações…</p>;
  }

  if (!settings) {
    return <p className="text-sm text-neutral-600">Não foi possível carregar as configurações.</p>;
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-xl font-semibold text-neutral-900 sm:text-2xl">Configurações</h1>

      <form
        noValidate
        onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        className="flex flex-col gap-8"
      >
        <div aria-live="polite">
          {formError && (
            <p role="alert" className="rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700">
              {formError}
            </p>
          )}
          {saved && !formError && (
            <p role="status" className="rounded-sm border border-status-active-text bg-status-active-bg px-3 py-2 text-sm text-status-active-text">
              Configurações salvas.
            </p>
          )}
        </div>

        <section className="flex flex-col gap-4 rounded-md border border-neutral-200 bg-neutral-0 p-4">
          <h2 className="text-lg font-semibold text-neutral-900">Identificação</h2>
          <Field
            register={register}
            id="condominiumName"
            label="Nome do condomínio"
            help="Aparece no cabeçalho do sistema e nas mensagens enviadas por WhatsApp."
            error={errors.condominiumName?.message}
          />
          <Field
            register={register}
            id="defaultPaymentWhatsapp"
            label="WhatsApp da administração"
            help="Só dígitos, com DDI. É para onde o morador é levado depois de reservar uma área paga."
            error={errors.defaultPaymentWhatsapp?.message}
          />
        </section>

        <section className="flex flex-col gap-4 rounded-md border border-neutral-200 bg-neutral-0 p-4">
          <h2 className="text-lg font-semibold text-neutral-900">Antecedência das reservas</h2>
          <Field
            register={register}
            id="minAdvanceDays"
            label="Antecedência mínima (dias)"
            type="number"
            help="Quantos dias antes do uso a reserva precisa ser feita. 0 permite reservar para o mesmo dia."
            error={errors.minAdvanceDays?.message}
          />
          <div className="grid gap-3 sm:grid-cols-2">
            <Field
              register={register}
              id="nextDayWindowStart"
              label="Início da janela para o dia seguinte"
              type="time"
              help="Horário em que abre o pedido de reserva para o dia seguinte."
              error={errors.nextDayWindowStart?.message}
            />
            <Field
              register={register}
              id="nextDayWindowEnd"
              label="Fim da janela para o dia seguinte"
              type="time"
              help="Horário em que fecha o pedido de reserva para o dia seguinte."
              error={errors.nextDayWindowEnd?.message}
            />
          </div>
          {windowStart && windowEnd && (
            <p className="text-sm text-neutral-600">
              Reservas para o dia seguinte só podem ser pedidas entre {windowStart} e {windowEnd}.
            </p>
          )}
          <Field
            register={register}
            id="maxAdvanceDays"
            label="Antecedência máxima (dias)"
            type="number"
            help="Até quantos dias no futuro o morador pode reservar."
            error={errors.maxAdvanceDays?.message}
          />
        </section>

        <section className="flex flex-col gap-4 rounded-md border border-neutral-200 bg-neutral-0 p-4">
          <h2 className="text-lg font-semibold text-neutral-900">Limites de uso</h2>
          <Field
            register={register}
            id="maxActiveBookingsPerUnit"
            label="Reservas ativas por unidade"
            type="number"
            help="Quantas reservas futuras a mesma unidade pode manter ao mesmo tempo. 0 = sem limite."
            error={errors.maxActiveBookingsPerUnit?.message}
          />
          <Field
            register={register}
            id="residentCancelDeadlineHours"
            label="Prazo para o morador cancelar (horas)"
            type="number"
            help="Até quantas horas antes do início o morador ainda pode cancelar a própria reserva."
            error={errors.residentCancelDeadlineHours?.message}
          />
        </section>

        <section className="flex flex-col gap-4 rounded-md border border-neutral-200 bg-neutral-0 p-4">
          <h2 className="text-lg font-semibold text-neutral-900">Reports</h2>
          <Field
            register={register}
            id="reportWindowDays"
            label="Prazo para abrir um report (dias)"
            type="number"
            help="Até quantos dias depois da reserva o morador pode registrar uma ocorrência. 0 = sem prazo."
            error={errors.reportWindowDays?.message}
          />
        </section>

        <section className="flex flex-col gap-4 rounded-md border border-neutral-200 bg-neutral-100 p-4">
          <h2 className="text-lg font-semibold text-neutral-900">Somente leitura</h2>
          <div className="flex flex-col gap-1">
            <span className="text-sm font-medium text-neutral-700">Fuso horário</span>
            <span className="text-base text-neutral-900">{settings.timezone}</span>
            <p className="text-sm text-neutral-600">
              Usado para calcular dias e horários das regras. Não é editável no MVP.
            </p>
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm font-medium text-neutral-700">Duração do intervalo de agenda (minutos)</span>
            <span className="text-base text-neutral-900">{settings.slotMinutes}</span>
            <p className="text-sm text-neutral-600">
              Tamanho de cada intervalo reservável nas áreas comuns. Não é editável no MVP.
            </p>
          </div>
        </section>

        <button
          type="submit"
          disabled={isSubmitting}
          className="h-11 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto sm:self-start"
        >
          {isSubmitting ? "Salvando…" : "Salvar configurações"}
        </button>
      </form>
    </div>
  );
}

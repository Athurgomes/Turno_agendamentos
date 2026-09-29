/**
 * Contas de síndico (RF-UNI-07): cria (usuário = e-mail), lista e desativa.
 * O síndico usa esta conta para a gestão operacional; para reservar como
 * morador, ele entra com o login da própria unidade (CLAUDE.md §6).
 */
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ConfirmDialog } from "../../../shared/components/ConfirmDialog";
import { CredentialsModal } from "../../units/CredentialsModal";
import { useCreateSyndic, useDeactivateSyndic, useSyndicsQuery } from "./hooks";
import { formatPhone } from "../../../shared/utils/format";
import { ApiError } from "../../../shared/api/client";
import type { Credentials, SyndicDto } from "../../../shared/api/types";

const syndicSchema = z.object({
  name: z.string().trim().min(1, "Informe o nome do síndico."),
  email: z.string().trim().toLowerCase().email("Informe um e-mail válido."),
  phone: z
    .string()
    .trim()
    .regex(/^\d{12,13}$/, "Telefone deve ter só dígitos, com DDI (ex.: 5562999998888)."),
});

type SyndicFormValues = z.infer<typeof syndicSchema>;

function fieldClassName(hasError: boolean) {
  return [
    "h-11 w-full rounded-sm border bg-neutral-0 px-3 text-base text-neutral-900",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600",
    hasError ? "border-danger-600" : "border-neutral-300",
  ].join(" ");
}

export function SyndicsPage() {
  const { data: syndics, isLoading } = useSyndicsQuery();
  const createSyndic = useCreateSyndic();
  const deactivateSyndic = useDeactivateSyndic();

  const [formError, setFormError] = useState<string | null>(null);
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [createdSyndic, setCreatedSyndic] = useState<{ name: string; phone: string } | null>(null);
  const [syndicToDeactivate, setSyndicToDeactivate] = useState<SyndicDto | null>(null);
  const [deactivateError, setDeactivateError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<SyndicFormValues>({ resolver: zodResolver(syndicSchema) });

  async function onSubmit(values: SyndicFormValues) {
    setFormError(null);
    try {
      const result = await createSyndic.mutateAsync(values);
      setCredentials(result.credentials);
      setCreatedSyndic({ name: values.name, phone: values.phone });
      reset();
    } catch (err) {
      setFormError(
        err instanceof ApiError ? err.detail : "Não foi possível cadastrar o síndico. Tente novamente.",
      );
    }
  }

  async function handleConfirmDeactivate() {
    if (!syndicToDeactivate) return;
    setDeactivateError(null);
    try {
      await deactivateSyndic.mutateAsync(syndicToDeactivate.id);
      setSyndicToDeactivate(null);
    } catch (err) {
      setDeactivateError(
        err instanceof ApiError ? err.detail : "Não foi possível desativar o síndico. Tente novamente.",
      );
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-6 text-xl font-semibold text-neutral-900 sm:text-2xl">Síndicos</h1>

      <form
        noValidate
        onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        className="mb-8 flex flex-col gap-4 rounded-md border border-neutral-200 bg-neutral-0 p-4"
      >
        <h2 className="text-lg font-semibold text-neutral-900">Nova conta de síndico</h2>

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

        <div className="grid gap-3 sm:grid-cols-3">
          <div className="flex flex-col gap-1">
            <label htmlFor="syndic-name" className="text-sm font-medium text-neutral-700">
              Nome
            </label>
            <input
              id="syndic-name"
              className={fieldClassName(!!errors.name)}
              aria-invalid={!!errors.name}
              {...register("name")}
            />
            {errors.name && <p className="text-sm text-danger-700">{errors.name.message}</p>}
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="syndic-email" className="text-sm font-medium text-neutral-700">
              E-mail (será o usuário)
            </label>
            <input
              id="syndic-email"
              type="email"
              className={fieldClassName(!!errors.email)}
              aria-invalid={!!errors.email}
              {...register("email")}
            />
            {errors.email && <p className="text-sm text-danger-700">{errors.email.message}</p>}
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="syndic-phone" className="text-sm font-medium text-neutral-700">
              Telefone (com DDI, só dígitos)
            </label>
            <input
              id="syndic-phone"
              inputMode="numeric"
              placeholder="5562999998888"
              className={fieldClassName(!!errors.phone)}
              aria-invalid={!!errors.phone}
              {...register("phone")}
            />
            {errors.phone && <p className="text-sm text-danger-700">{errors.phone.message}</p>}
          </div>
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="h-11 rounded-md bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto sm:self-start"
        >
          {isSubmitting ? "Cadastrando…" : "Cadastrar síndico"}
        </button>
      </form>

      {isLoading && <p className="text-sm text-neutral-600">Carregando síndicos…</p>}

      {!isLoading && (syndics?.length ?? 0) === 0 && (
        <p className="text-sm text-neutral-600">Nenhum síndico cadastrado ainda.</p>
      )}

      <ul className="flex flex-col gap-3">
        {syndics?.map((syndic) => (
          <li
            key={syndic.id}
            className="flex flex-col gap-2 rounded-md border border-neutral-200 bg-neutral-0 p-4 sm:flex-row sm:items-center sm:justify-between"
          >
            <div>
              <p className="font-medium text-neutral-900">{syndic.name}</p>
              <p className="text-sm text-neutral-600">
                {syndic.email} · {formatPhone(syndic.phone)}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <span
                className={
                  syndic.active
                    ? "rounded-pill bg-status-active-bg px-3 py-1 text-sm text-status-active-text"
                    : "rounded-pill bg-status-inactive-bg px-3 py-1 text-sm text-status-inactive-text"
                }
              >
                {syndic.active ? "Ativo" : "Desativado"}
              </span>
              {syndic.active && (
                <button
                  type="button"
                  onClick={() => setSyndicToDeactivate(syndic)}
                  className="rounded-md border border-danger-700 px-3 py-1.5 text-sm font-medium text-danger-700 hover:bg-danger-50"
                >
                  Desativar
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>

      <CredentialsModal
        open={!!credentials}
        credentials={credentials}
        residentName={createdSyndic?.name ?? ""}
        residentPhone={createdSyndic?.phone ?? ""}
        onClose={() => {
          setCredentials(null);
          setCreatedSyndic(null);
        }}
      />

      <ConfirmDialog
        open={!!syndicToDeactivate}
        title="Desativar síndico"
        message={
          deactivateError ??
          `Tem certeza que deseja desativar a conta de ${syndicToDeactivate?.name ?? ""}? O acesso é revogado imediatamente.`
        }
        confirmLabel="Desativar"
        isLoading={deactivateSyndic.isPending}
        onConfirm={() => void handleConfirmDeactivate()}
        onCancel={() => {
          setSyndicToDeactivate(null);
          setDeactivateError(null);
        }}
      />
    </div>
  );
}

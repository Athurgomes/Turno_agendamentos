/**
 * Troca de senha (RF-AUT-02, RN-04). A política abaixo é só UX: o backend
 * reavalia tudo e pode responder `422 WEAK_PASSWORD` mesmo com o formulário
 * validado aqui (ex.: senha reaproveitada de um vazamento conhecido).
 * Sucesso (`204`) zera `tempPassword` na sessão em memória, o que faz o
 * banner de senha temporária (`TempPasswordBanner`) sumir.
 */
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useSession } from "./useSession";
import { changePassword } from "./api";
import { ApiError } from "../../shared/api/client";
import { getSession, setSession } from "../../shared/api/sessionStore";

// RN-04: mínimo 8 caracteres, letras e números, diferente da atual e do usuário.
function buildSchema(loginIdentifier: string | null) {
  return z
    .object({
      currentPassword: z.string().min(1, "Informe sua senha atual."),
      newPassword: z
        .string()
        .min(8, "A nova senha precisa ter no mínimo 8 caracteres.")
        .regex(/[a-zA-Z]/, "A nova senha precisa ter pelo menos uma letra.")
        .regex(/[0-9]/, "A nova senha precisa ter pelo menos um número."),
      confirmPassword: z.string().min(1, "Confirme a nova senha."),
    })
    .refine((v) => v.newPassword === v.confirmPassword, {
      message: "A confirmação precisa ser igual à nova senha.",
      path: ["confirmPassword"],
    })
    .refine((v) => v.newPassword !== v.currentPassword, {
      message: "A nova senha precisa ser diferente da senha atual.",
      path: ["newPassword"],
    })
    .refine(
      (v) =>
        !loginIdentifier ||
        v.newPassword.toLowerCase() !== loginIdentifier.toLowerCase(),
      {
        message: "A nova senha não pode ser igual ao seu usuário.",
        path: ["newPassword"],
      },
    );
}

type FormValues = z.infer<ReturnType<typeof buildSchema>>;

function fieldClassName(hasError: boolean) {
  return [
    "h-11 w-full rounded-sm border bg-neutral-0 px-3 text-base text-neutral-900",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600",
    hasError ? "border-danger-600" : "border-neutral-300",
  ].join(" ");
}

export function ChangePasswordPage() {
  const { user } = useSession();
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const schema = buildSchema(user?.unitIdentifier ?? null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setFormError(null);
    setSuccess(false);
    try {
      await changePassword(values.currentPassword, values.newPassword);

      const current = getSession();
      if (current.user && current.accessToken) {
        setSession({
          accessToken: current.accessToken,
          user: { ...current.user, tempPassword: false },
        });
      }

      reset();
      setSuccess(true);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "INVALID_CURRENT_PASSWORD") {
          setError("currentPassword", { message: err.detail });
        } else if (err.code === "WEAK_PASSWORD") {
          setError("newPassword", { message: err.detail });
        } else {
          setFormError(err.detail);
        }
      } else {
        setFormError("Não foi possível trocar a senha. Tente novamente.");
      }
    }
  }

  return (
    <div className="mx-auto w-full max-w-sm">
      <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">
        Alterar senha
      </h1>
      <p className="mt-1 text-sm text-neutral-600">
        Use uma senha só sua: mínimo de 8 caracteres, com letras e números.
      </p>

      <form
        noValidate
        onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        className="mt-6 rounded-lg border border-neutral-200 bg-neutral-0 p-6 shadow-sm"
      >
        <div aria-live="polite">
          {formError && (
            <p
              role="alert"
              className="mb-4 rounded-sm border border-danger-600 bg-danger-50 px-3 py-2 text-sm text-danger-700"
            >
              {formError}
            </p>
          )}
          {success && (
            <p
              role="status"
              className="mb-4 rounded-sm border border-success-700 bg-success-50 px-3 py-2 text-sm text-success-700"
            >
              Senha alterada com sucesso.
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label
            htmlFor="currentPassword"
            className="text-sm font-medium text-neutral-700"
          >
            Senha atual
          </label>
          <input
            id="currentPassword"
            type="password"
            autoComplete="current-password"
            aria-invalid={!!errors.currentPassword}
            aria-describedby={
              errors.currentPassword ? "currentPassword-error" : undefined
            }
            className={fieldClassName(!!errors.currentPassword)}
            {...register("currentPassword")}
          />
          {errors.currentPassword && (
            <p id="currentPassword-error" className="text-sm text-danger-700">
              {errors.currentPassword.message}
            </p>
          )}
        </div>

        <div className="mt-4 flex flex-col gap-1">
          <label
            htmlFor="newPassword"
            className="text-sm font-medium text-neutral-700"
          >
            Nova senha
          </label>
          <input
            id="newPassword"
            type="password"
            autoComplete="new-password"
            aria-invalid={!!errors.newPassword}
            aria-describedby={
              errors.newPassword ? "newPassword-error" : undefined
            }
            className={fieldClassName(!!errors.newPassword)}
            {...register("newPassword")}
          />
          {errors.newPassword && (
            <p id="newPassword-error" className="text-sm text-danger-700">
              {errors.newPassword.message}
            </p>
          )}
        </div>

        <div className="mt-4 flex flex-col gap-1">
          <label
            htmlFor="confirmPassword"
            className="text-sm font-medium text-neutral-700"
          >
            Confirmar nova senha
          </label>
          <input
            id="confirmPassword"
            type="password"
            autoComplete="new-password"
            aria-invalid={!!errors.confirmPassword}
            aria-describedby={
              errors.confirmPassword ? "confirmPassword-error" : undefined
            }
            className={fieldClassName(!!errors.confirmPassword)}
            {...register("confirmPassword")}
          />
          {errors.confirmPassword && (
            <p id="confirmPassword-error" className="text-sm text-danger-700">
              {errors.confirmPassword.message}
            </p>
          )}
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="mt-6 flex h-11 w-full items-center justify-center rounded-md bg-primary-600 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {isSubmitting ? "Salvando…" : "Salvar nova senha"}
        </button>
      </form>
    </div>
  );
}

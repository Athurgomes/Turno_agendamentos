/**
 * Tela de login (RF-AUT-01, RF-AUT-05, RN-05). O campo "Usuário ou e-mail"
 * aceita tanto o login de unidade (ex. `a-1203`) quanto o e-mail de contas
 * ADMIN/SYNDIC — quem decide o formato válido é o backend.
 *
 * Mensagens de erro: 401 sempre mostra o texto genérico da RN-05 (nunca
 * revela se o usuário existe); 423 mostra o `detail` do backend (tempo de
 * bloqueio); demais erros mostram o `detail` recebido. Validação de campo
 * vazio aqui é só UX (CLAUDE.md §5) — quem valida de verdade é a API.
 */
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useSession } from "./useSession";
import { homeForRole } from "../../routes/roleHome";
import { login as loginRequest } from "./api";
import { ApiError } from "../../shared/api/client";
import { BrandMark } from "../../shared/components/BrandMark";

const schema = z.object({
  login: z.string().trim().min(1, "Informe seu usuário ou e-mail."),
  password: z.string().min(1, "Informe sua senha."),
});

type FormValues = z.infer<typeof schema>;

interface LocationState {
  from?: { pathname: string };
}

function fieldClassName(hasError: boolean) {
  return [
    "h-11 w-full rounded-sm border bg-neutral-0 px-3 text-base text-neutral-900",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600",
    hasError ? "border-danger-600" : "border-neutral-300",
  ].join(" ");
}

export function LoginPage() {
  const { user, isLoading, setSession } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  if (isLoading) {
    return (
      <div className="flex min-h-[100dvh] items-center justify-center">
        <p className="text-sm text-neutral-600">Carregando…</p>
      </div>
    );
  }

  if (user) {
    const from = (location.state as LocationState | null)?.from;
    return <Navigate to={from?.pathname ?? homeForRole(user.role)} replace />;
  }

  async function onSubmit(values: FormValues) {
    setFormError(null);
    try {
      const data = await loginRequest(values.login, values.password);
      setSession({ accessToken: data.accessToken, user: data.user });
      const from = (location.state as LocationState | null)?.from;
      navigate(from?.pathname ?? homeForRole(data.user.role), {
        replace: true,
      });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setFormError("Usuário ou senha inválidos.");
        } else if (err.status === 429) {
          setFormError(
            "Muitas tentativas seguidas. Aguarde um momento e tente novamente.",
          );
        } else {
          setFormError(err.detail);
        }
      } else {
        setFormError(
          "Não foi possível entrar. Verifique sua conexão e tente novamente.",
        );
      }
    }
  }

  return (
    <div className="flex min-h-[100dvh] flex-col items-center justify-center bg-neutral-50 px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center gap-1 text-center">
          <BrandMark className="h-11 w-11" />
          <h1 className="mt-2 text-xl font-semibold text-neutral-900">
            Turno
          </h1>
          <p className="text-sm text-neutral-600">
            Reservas de áreas comuns do condomínio
          </p>
        </div>

        <form
          noValidate
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
          className="rounded-lg border border-neutral-200 bg-neutral-0 p-6 shadow-sm"
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
          </div>

          <div className="flex flex-col gap-1">
            <label
              htmlFor="login"
              className="text-sm font-medium text-neutral-700"
            >
              Usuário ou e-mail
            </label>
            <input
              id="login"
              type="text"
              autoComplete="username"
              aria-invalid={!!errors.login}
              aria-describedby={errors.login ? "login-error" : undefined}
              className={fieldClassName(!!errors.login)}
              {...register("login")}
            />
            {errors.login && (
              <p id="login-error" className="text-sm text-danger-700">
                {errors.login.message}
              </p>
            )}
          </div>

          <div className="mt-4 flex flex-col gap-1">
            <label
              htmlFor="password"
              className="text-sm font-medium text-neutral-700"
            >
              Senha
            </label>
            <div className="relative">
              <input
                id="password"
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
                aria-invalid={!!errors.password}
                aria-describedby={
                  errors.password ? "password-error" : undefined
                }
                className={fieldClassName(!!errors.password) + " pr-20"}
                {...register("password")}
              />
              <button
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                className="absolute inset-y-0 right-0 px-3 text-sm font-medium text-primary-700 hover:underline"
              >
                {showPassword ? "Ocultar" : "Mostrar"}
              </button>
            </div>
            {errors.password && (
              <p id="password-error" className="text-sm text-danger-700">
                {errors.password.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="mt-6 flex h-11 w-full items-center justify-center rounded-md bg-primary-600 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-70"
          >
            {isSubmitting ? "Entrando…" : "Entrar"}
          </button>
        </form>
      </div>
    </div>
  );
}

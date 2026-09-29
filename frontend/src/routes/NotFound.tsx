import { Link } from "react-router-dom";

export function NotFound() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 p-6 text-center">
      <h1 className="text-xl font-semibold text-neutral-900">
        Página não encontrada
      </h1>
      <p className="text-sm text-neutral-600">
        O endereço acessado não existe ou foi removido.
      </p>
      <Link
        to="/"
        className="mt-2 rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
      >
        Voltar ao início
      </Link>
    </div>
  );
}

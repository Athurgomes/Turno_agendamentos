import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "./client";

/** Erros 4xx são de negócio/validação (RN, permissão) — repetir não resolve. */
function isClientError(error: unknown): boolean {
  return error instanceof ApiError && error.status >= 400 && error.status < 500;
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (isClientError(error)) return false;
        return failureCount < 2;
      },
    },
    mutations: {
      retry: false,
    },
  },
});

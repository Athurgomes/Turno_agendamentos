/**
 * Relógio do servidor (`GET /system/clock`, público — docs/03-api.md
 * "Sistema", D-32). `simulated: true` quando `APP_DEMO_NOW` está ativo no
 * backend; nesse caso a faixa "Horário simulado" (`SimulatedClockBanner`)
 * aparece em todas as telas.
 *
 * `serverNow()` é o utilitário para telas fora deste hook (ex.: calendários e
 * datas mínimas de reserva da F4): devolve o `now` da última resposta do
 * servidor + o tempo decorrido no navegador desde então, em vez de
 * `new Date()` puro — importante quando o relógio está simulado. Antes da
 * primeira resposta, cai para `new Date()` local.
 */
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../api/client";

export interface ServerClockDto {
  now: string;
  timezone: string;
  simulated: boolean;
}

const CLOCK_QUERY_KEY = ["system-clock"] as const;
/** Resync a cada 5 min: drift do relógio do navegador nesse intervalo é desprezível para as RNs de agenda. */
const REFRESH_MS = 5 * 60 * 1000;

// Módulo-nível (fora do React) para que `serverNow()` funcione sem um componente montado.
let offsetMs = 0;
let offsetKnown = false;

function recordOffset(nowIso: string) {
  offsetMs = new Date(nowIso).getTime() - Date.now();
  offsetKnown = true;
}

export function useServerClock() {
  return useQuery({
    queryKey: CLOCK_QUERY_KEY,
    queryFn: async () => {
      const data = await apiFetch<ServerClockDto>("/system/clock");
      recordOffset(data.now);
      return data;
    },
    staleTime: REFRESH_MS,
    refetchInterval: REFRESH_MS,
  });
}

export function serverNow(): Date {
  if (!offsetKnown) return new Date();
  return new Date(Date.now() + offsetMs);
}

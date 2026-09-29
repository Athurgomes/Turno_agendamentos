/**
 * Faixa "Horário simulado" (D-32, docs/08 §3): aparece em todas as telas,
 * inclusive `/login`, enquanto `GET /system/clock` responder
 * `simulated: true`. Some por completo com `simulated: false` — não é
 * fechável, porque a condição que a gera continua verdadeira (DESIGN.md
 * "Banner"). Renderizada em `App.tsx`, acima do roteador, para cobrir tanto
 * as telas com `AppShell` quanto o login (que não usa o shell).
 */
import { useEffect, useState } from "react";
import { serverNow, useServerClock } from "../hooks/useServerClock";
import { formatDateTimeInstant } from "../utils/format";

export function SimulatedClockBanner() {
  const { data } = useServerClock();
  const simulated = data?.simulated ?? false;
  // Re-renderiza a cada minuto para acompanhar `serverNow()`, sem esperar o próximo fetch.
  const [, tick] = useState(0);

  useEffect(() => {
    if (!simulated) return;
    const id = setInterval(() => tick((n) => n + 1), 60_000);
    return () => clearInterval(id);
  }, [simulated]);

  if (!data?.simulated) return null;

  return (
    <div
      role="status"
      className="border-b border-primary-200 bg-primary-50 px-4 py-1.5 text-center text-xs text-primary-800 sm:px-6"
    >
      Horário simulado: {formatDateTimeInstant(serverNow(), data.timezone)}
    </div>
  );
}

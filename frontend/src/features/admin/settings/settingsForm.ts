/**
 * Esquema e montagem de payload do formulário de configurações
 * (`docs/03-api.md` "Contas de síndico e configurações", RN-20/21/22/30/34).
 * Validação aqui é só UX — quem decide de verdade é o backend.
 */
import { z } from "zod";
import type { SettingsDto } from "../../../shared/api/types";

const TIME_REGEX = /^([01]\d|2[0-3]):[0-5]\d$/;

function nonNegativeInt(message: string) {
  return z.coerce.number().int(message).min(0, message);
}

export const settingsFormSchema = z
  .object({
    condominiumName: z.string().trim().min(1, "Informe o nome do condomínio."),
    defaultPaymentWhatsapp: z
      .string()
      .trim()
      .regex(/^\d{12,13}$/, "Telefone deve ter só dígitos, com DDI (ex.: 5562999998888)."),
    minAdvanceDays: nonNegativeInt("Use um número inteiro, 0 ou mais."),
    nextDayWindowStart: z.string().trim().regex(TIME_REGEX, "Use o formato HH:mm."),
    nextDayWindowEnd: z.string().trim().regex(TIME_REGEX, "Use o formato HH:mm."),
    maxAdvanceDays: nonNegativeInt("Use um número inteiro, 0 ou mais."),
    maxActiveBookingsPerUnit: nonNegativeInt("Use um número inteiro, 0 ou mais (0 = sem limite)."),
    residentCancelDeadlineHours: nonNegativeInt("Use um número inteiro, 0 ou mais."),
    reportWindowDays: nonNegativeInt("Use um número inteiro, 0 ou mais."),
  })
  .superRefine((values, ctx) => {
    if (values.maxAdvanceDays < values.minAdvanceDays) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["maxAdvanceDays"],
        message: "A antecedência máxima não pode ser menor que a mínima.",
      });
    }
    if (values.nextDayWindowStart >= values.nextDayWindowEnd) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["nextDayWindowEnd"],
        message: "O fim da janela precisa ser depois do início.",
      });
    }
  });

/** Valores brutos do formulário (números ainda como string, antes do `z.coerce`). */
export type SettingsFormInput = z.input<typeof settingsFormSchema>;
/** Valores validados (números já convertidos) — o que chega em `onSubmit`. */
export type SettingsFormValues = z.output<typeof settingsFormSchema>;

/**
 * A API devolve `LocalTime` como `"HH:mm:ss"` (Jackson), não `"HH:mm"` como
 * o contrato documenta — divergência do backend em relação a docs/03,
 * reportada ao orquestrador. O formulário só aceita `"HH:mm"`.
 */
function toHhMm(time: string): string {
  return time.slice(0, 5);
}

/** Campos editáveis do `SettingsDto` — omite `timezone`/`slotMinutes` (só leitura, D-15). */
export function settingsToFormValues(settings: SettingsDto): SettingsFormInput {
  return {
    condominiumName: settings.condominiumName,
    defaultPaymentWhatsapp: settings.defaultPaymentWhatsapp,
    minAdvanceDays: settings.minAdvanceDays,
    nextDayWindowStart: toHhMm(settings.nextDayWindowStart),
    nextDayWindowEnd: toHhMm(settings.nextDayWindowEnd),
    maxAdvanceDays: settings.maxAdvanceDays,
    maxActiveBookingsPerUnit: settings.maxActiveBookingsPerUnit,
    residentCancelDeadlineHours: settings.residentCancelDeadlineHours,
    reportWindowDays: settings.reportWindowDays,
  };
}

/** Corpo de `PUT /admin/settings`: reaproveita `timezone`/`slotMinutes` já carregados (campos só leitura). */
export function buildSettingsPayload(values: SettingsFormValues, current: SettingsDto): SettingsDto {
  return {
    ...values,
    timezone: current.timezone,
    slotMinutes: current.slotMinutes,
  };
}

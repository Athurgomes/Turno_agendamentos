/**
 * Esquema e montagem de payload do formulário de área (`docs/03-api.md`
 * "Áreas", D-44; RN-11 a RN-13). Validação aqui é só UX — quem decide de
 * verdade é o backend (`PAYMENT_INFO_REQUIRED`, `INVALID_FILE`...).
 */
import { z } from "zod";
import type { AreaCategory, IsoWeekday, OpeningHoursDto } from "../../shared/api/types";

const TIME_REGEX = /^([01]\d|2[0-3]):(00|30)$/;

export const WEEKDAYS: IsoWeekday[] = [1, 2, 3, 4, 5, 6, 7];

const scheduleDaySchema = z
  .object({
    dayOfWeek: z.number().int().min(1).max(7),
    open: z.boolean(),
    openTime: z.string(),
    closeTime: z.string(),
  })
  .superRefine((day, ctx) => {
    if (!day.open) return;
    if (!TIME_REGEX.test(day.openTime)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["openTime"],
        message: "Horário deve ser em passos de 30 minutos.",
      });
    }
    if (!TIME_REGEX.test(day.closeTime)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["closeTime"],
        message: "Horário deve ser em passos de 30 minutos.",
      });
    }
    if (
      TIME_REGEX.test(day.openTime) &&
      TIME_REGEX.test(day.closeTime) &&
      day.closeTime <= day.openTime
    ) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["closeTime"],
        message: "O fechamento deve ser depois da abertura.",
      });
    }
  });

export type ScheduleDay = z.infer<typeof scheduleDaySchema>;

/** Cadastro (`POST /areas`) e edição por ADMIN (`PUT /areas/{id}`, todos os campos). */
export const areaFormSchema = z
  .object({
    name: z.string().trim().min(1, "Informe o nome da área."),
    category: z.string().trim().min(1, "Selecione uma categoria."),
    description: z.string().trim().min(1, "Descreva a área."),
    rules: z.string().trim().min(1, "Informe as regras de uso."),
    conductGuidelines: z.string().trim().min(1, "Informe as sugestões de conduta."),
    capacity: z.coerce.number().int().min(1, "A capacidade deve ser maior que zero."),
    requiresPayment: z.boolean(),
    price: z.union([z.coerce.number(), z.literal("")]).optional(),
    paymentWhatsapp: z.string().trim().optional().or(z.literal("")),
    schedule: z.array(scheduleDaySchema).length(7),
  })
  .superRefine((values, ctx) => {
    if (values.requiresPayment) {
      const price = typeof values.price === "number" ? values.price : Number(values.price);
      if (!price || price <= 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["price"],
          message: "Informe um valor maior que zero.",
        });
      }
      if (!values.paymentWhatsapp) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["paymentWhatsapp"],
          message: "Informe o WhatsApp da administração.",
        });
      }
    }
    if (!values.schedule.some((day) => day.open)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["schedule"],
        message: "Selecione ao menos um dia de funcionamento.",
      });
    }
  });

/** Valores brutos do formulário (capacidade/valor ainda como string, antes do `z.coerce`). */
export type AreaFormInput = z.input<typeof areaFormSchema>;
/** Valores validados (números já convertidos) — o que chega em `onSubmit`. */
export type AreaFormValues = z.output<typeof areaFormSchema>;

export interface CreateAreaPayload {
  name: string;
  category: AreaCategory;
  description: string;
  rules: string;
  conductGuidelines: string;
  capacity: number;
  requiresPayment: boolean;
  price?: number | null;
  paymentWhatsapp?: string | null;
  openingHours: OpeningHoursDto[];
}

/** `PUT /areas/{id}`: atualização parcial, campo ausente = inalterado. */
export type UpdateAreaPayload = Partial<CreateAreaPayload> & { version?: number };

/**
 * Monta o corpo de `POST /areas` (parte `data`) e o corpo completo de `PUT /areas/{id}` do
 * ADMIN. Quando `requiresPayment` é falso, envia `price`/`paymentWhatsapp` como `null` de forma
 * explícita — o backend também limpa esses campos nesse caso (D-44), mas o payload já reflete a
 * intenção sem depender só do lado do servidor.
 */
export function buildAreaPayload(values: AreaFormValues): CreateAreaPayload {
  return {
    name: values.name,
    category: values.category as AreaCategory,
    description: values.description,
    rules: values.rules,
    conductGuidelines: values.conductGuidelines,
    capacity: values.capacity,
    requiresPayment: values.requiresPayment,
    price: values.requiresPayment ? Number(values.price) : null,
    paymentWhatsapp: values.requiresPayment ? values.paymentWhatsapp || null : null,
    openingHours: values.schedule
      .filter((day) => day.open)
      .map((day) => ({
        dayOfWeek: day.dayOfWeek as IsoWeekday,
        openTime: day.openTime,
        closeTime: day.closeTime,
      })),
  };
}

export function emptyScheduleDay(dayOfWeek: IsoWeekday): ScheduleDay {
  return { dayOfWeek, open: false, openTime: "08:00", closeTime: "18:00" };
}

export function defaultSchedule(): ScheduleDay[] {
  return WEEKDAYS.map((day) => emptyScheduleDay(day));
}

/**
 * A API devolve `LocalTime` como `"HH:mm:ss"` (Jackson), não `"HH:mm"` como
 * o contrato documenta — divergência do backend em relação a docs/03,
 * reportada ao orquestrador. O formulário só aceita `"HH:mm"`.
 */
function toHhMm(time: string): string {
  return time.slice(0, 5);
}

export function scheduleFromOpeningHours(hours: OpeningHoursDto[]): ScheduleDay[] {
  return WEEKDAYS.map((day) => {
    const match = hours.find((hour) => hour.dayOfWeek === day);
    return match
      ? {
          dayOfWeek: day,
          open: true,
          openTime: toHhMm(match.openTime),
          closeTime: toHhMm(match.closeTime),
        }
      : emptyScheduleDay(day);
  });
}

/** Edição por SYNDIC (D-20): só descrição, regras, conduta e capacidade. */
export const syndicAreaEditSchema = z.object({
  description: z.string().trim().min(1, "Descreva a área."),
  rules: z.string().trim().min(1, "Informe as regras de uso."),
  conductGuidelines: z.string().trim().min(1, "Informe as sugestões de conduta."),
  capacity: z.coerce.number().int().min(1, "A capacidade deve ser maior que zero."),
});

export type SyndicAreaEditInput = z.input<typeof syndicAreaEditSchema>;
export type SyndicAreaEditValues = z.output<typeof syndicAreaEditSchema>;

export function buildSyndicEditPayload(values: SyndicAreaEditValues): UpdateAreaPayload {
  return {
    description: values.description,
    rules: values.rules,
    conductGuidelines: values.conductGuidelines,
    capacity: values.capacity,
  };
}

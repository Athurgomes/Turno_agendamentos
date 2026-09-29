/**
 * Esquema e montagem de payload do formulário de unidade/moradores
 * (`docs/03-api.md` "Unidades e moradores", D-43; RN-07, RN-08). Validação
 * aqui é só UX — quem decide de verdade é o backend (`CPF_INVALID`,
 * `DUPLICATE_CPF_IN_UNIT`, `PRIMARY_RESIDENT_REQUIRED`).
 */
import { z } from "zod";
import { isValidCpf } from "../../shared/utils/cpf";

const PHONE_REGEX = /^\d{12,13}$/;

export const residentInputSchema = z
  .object({
    id: z.string().optional(),
    name: z.string().trim().min(1, "Informe o nome do morador."),
    phone: z
      .string()
      .trim()
      .regex(PHONE_REGEX, "Telefone deve ter só dígitos, com DDI (ex.: 5562999998888)."),
    email: z
      .string()
      .trim()
      .toLowerCase()
      .optional()
      .or(z.literal(""))
      .refine((value) => !value || z.string().email().safeParse(value).success, {
        message: "E-mail inválido.",
      }),
    cpf: z.string().trim().optional().or(z.literal("")),
    primary: z.boolean(),
  })
  .superRefine((resident, ctx) => {
    if (resident.primary) {
      if (!resident.email) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["email"],
          message: "E-mail é obrigatório para o morador principal.",
        });
      }
      if (!resident.cpf) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["cpf"],
          message: "CPF é obrigatório para o morador principal.",
        });
      } else if (!isValidCpf(resident.cpf)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["cpf"],
          message: "CPF inválido. Confira os dígitos.",
        });
      }
    } else if (resident.cpf && !isValidCpf(resident.cpf)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["cpf"],
        message: "CPF inválido. Confira os dígitos.",
      });
    }
  });

export type ResidentInput = z.infer<typeof residentInputSchema>;

function residentsRefinement(residents: ResidentInput[], ctx: z.RefinementCtx) {
  const primaryCount = residents.filter((resident) => resident.primary).length;
  if (primaryCount !== 1) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["residents"],
      message: "Selecione exatamente um morador principal.",
    });
  }

  const cpfSeen = new Set<string>();
  residents.forEach((resident, index) => {
    if (!resident.cpf) return;
    if (cpfSeen.has(resident.cpf)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: [index, "cpf"],
        message: "Este CPF já foi informado para outro morador desta unidade.",
      });
    }
    cpfSeen.add(resident.cpf);
  });
}

/** Cadastro (`POST /units`) e troca de titularidade (`POST /units/{id}/transfer`): bloco/número + lista de moradores. */
export const unitFormSchema = z.object({
  block: z.string().trim().optional().or(z.literal("")),
  number: z.string().trim().min(1, "Informe o número da unidade."),
  residents: z
    .array(residentInputSchema)
    .min(1, "Cadastre ao menos o morador principal.")
    .superRefine(residentsRefinement),
});

export type UnitFormValues = z.infer<typeof unitFormSchema>;

/** Troca de titularidade: mesmo corpo do cadastro, sem `block`/`number` (contrato fixo). */
export const transferFormSchema = z.object({
  residents: z
    .array(residentInputSchema)
    .min(1, "Cadastre ao menos o novo morador principal.")
    .superRefine(residentsRefinement),
});

export type TransferFormValues = z.infer<typeof transferFormSchema>;

export function emptyResident(primary = false): ResidentInput {
  return { name: "", phone: "", email: "", cpf: "", primary };
}

interface PrimaryMemberPayload {
  name: string;
  phone: string;
  email: string;
  cpf: string;
}

interface MemberPayload {
  name: string;
  phone: string;
  email?: string;
  cpf?: string;
}

export interface CreateUnitPayload {
  block?: string;
  number: string;
  primary: PrimaryMemberPayload;
  members: MemberPayload[];
}

export interface PrimaryAndMembersPayload {
  primary: PrimaryMemberPayload;
  members: MemberPayload[];
}

function splitPrimaryAndMembers(residents: ResidentInput[]): PrimaryAndMembersPayload {
  const primary = residents.find((resident) => resident.primary);
  if (!primary) {
    throw new Error("Nenhum morador principal selecionado — validação do schema deveria ter barrado isto.");
  }
  const members = residents
    .filter((resident) => !resident.primary)
    .map((resident) => ({
      name: resident.name,
      phone: resident.phone,
      email: resident.email || undefined,
      cpf: resident.cpf || undefined,
    }));

  return {
    primary: {
      name: primary.name,
      phone: primary.phone,
      // Schema (superRefine) já garante e-mail/CPF preenchidos para quem é `primary`.
      email: primary.email as string,
      cpf: primary.cpf as string,
    },
    members,
  };
}

/** Monta o corpo de `POST /units`. */
export function buildCreatePayload(values: UnitFormValues): CreateUnitPayload {
  const { primary, members } = splitPrimaryAndMembers(values.residents);
  return {
    block: values.block || undefined,
    number: values.number,
    primary,
    members,
  };
}

/** Monta o corpo de `POST /units/{id}/transfer`. */
export function buildTransferPayload(values: TransferFormValues): PrimaryAndMembersPayload {
  return splitPrimaryAndMembers(values.residents);
}

export interface EditResidentPayload {
  id?: string;
  name: string;
  phone: string;
  email?: string;
  cpf?: string;
  primary: boolean;
}

export interface EditUnitPayload {
  residents: EditResidentPayload[];
}

/** Monta o corpo de `PUT /units/{id}`. */
export function buildEditPayload(values: UnitFormValues): EditUnitPayload {
  return {
    residents: values.residents.map((resident) => ({
      id: resident.id,
      name: resident.name,
      phone: resident.phone,
      email: resident.email || undefined,
      cpf: resident.cpf || undefined,
      primary: resident.primary,
    })),
  };
}

/**
 * `POST /me/unit/residents` (RN-09): a conta UNIT cadastra um adicional.
 * CPF é opcional aqui (o backend não exige) — a validação de dígito
 * verificador é só UX, igual ao restante do formulário de moradores.
 */
export const addMyResidentSchema = z.object({
  name: z.string().trim().min(1, "Informe o nome do morador."),
  phone: z
    .string()
    .trim()
    .regex(PHONE_REGEX, "Telefone deve ter só dígitos, com DDI (ex.: 5562999998888)."),
  email: z
    .string()
    .trim()
    .toLowerCase()
    .optional()
    .or(z.literal(""))
    .refine((value) => !value || z.string().email().safeParse(value).success, {
      message: "E-mail inválido.",
    }),
  cpf: z
    .string()
    .trim()
    .optional()
    .or(z.literal(""))
    .refine((value) => !value || isValidCpf(value), {
      message: "CPF inválido. Confira os dígitos.",
    }),
});

export type AddMyResidentValues = z.infer<typeof addMyResidentSchema>;

export interface AddMyResidentPayload {
  name: string;
  phone: string;
  email?: string;
  cpf?: string;
}

export function buildAddMyResidentPayload(values: AddMyResidentValues): AddMyResidentPayload {
  return {
    name: values.name,
    phone: values.phone,
    email: values.email || undefined,
    cpf: values.cpf || undefined,
  };
}

/**
 * `PUT /me/unit/residents/{id}` (RN-09): a conta UNIT edita nome/telefone/
 * e-mail de qualquer morador da própria unidade. CPF nunca aparece aqui —
 * não é um campo editável pela conta UNIT. E-mail continua obrigatório para
 * o morador principal.
 */
export function editMyResidentSchema(isPrimary: boolean) {
  return z.object({
    name: z.string().trim().min(1, "Informe o nome do morador."),
    phone: z
      .string()
      .trim()
      .regex(PHONE_REGEX, "Telefone deve ter só dígitos, com DDI (ex.: 5562999998888)."),
    email: isPrimary
      ? z.string().trim().toLowerCase().email("E-mail é obrigatório para o morador principal.")
      : z
          .string()
          .trim()
          .toLowerCase()
          .optional()
          .or(z.literal(""))
          .refine((value) => !value || z.string().email().safeParse(value).success, {
            message: "E-mail inválido.",
          }),
  });
}

export type EditMyResidentValues = z.infer<ReturnType<typeof editMyResidentSchema>>;

export interface EditMyResidentPayload {
  name: string;
  phone: string;
  email?: string;
}

export function buildEditMyResidentPayload(values: EditMyResidentValues): EditMyResidentPayload {
  return {
    name: values.name,
    phone: values.phone,
    email: values.email || undefined,
  };
}

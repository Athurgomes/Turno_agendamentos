import { describe, expect, it } from "vitest";
import {
  buildCreatePayload,
  buildEditPayload,
  buildTransferPayload,
  emptyResident,
  transferFormSchema,
  unitFormSchema,
} from "./residentForm";

const validPrimary = {
  name: "Maria Souza",
  phone: "5562999998888",
  email: "maria@exemplo.test",
  cpf: "52998224725",
  primary: true,
};

const validMember = {
  name: "João Souza",
  phone: "5562999997777",
  email: "",
  cpf: "",
  primary: false,
};

describe("unitFormSchema", () => {
  it("aceita bloco, número e um morador principal válido", () => {
    const result = unitFormSchema.safeParse({
      block: "A",
      number: "1203",
      residents: [validPrimary],
    });
    expect(result.success).toBe(true);
  });

  it("exige e-mail e CPF do morador principal", () => {
    const result = unitFormSchema.safeParse({
      number: "1203",
      residents: [{ ...validPrimary, email: "", cpf: "" }],
    });
    expect(result.success).toBe(false);
    const messages = !result.success
      ? result.error.issues.map((issue) => issue.path.join("."))
      : [];
    expect(messages).toEqual(
      expect.arrayContaining(["residents.0.email", "residents.0.cpf"]),
    );
  });

  it("rejeita CPF com dígito verificador inválido", () => {
    const result = unitFormSchema.safeParse({
      number: "1203",
      residents: [{ ...validPrimary, cpf: "11111111111" }],
    });
    expect(result.success).toBe(false);
  });

  it("rejeita telefone fora do padrão de dígitos com DDI", () => {
    const result = unitFormSchema.safeParse({
      number: "1203",
      residents: [{ ...validPrimary, phone: "62999998888" }],
    });
    expect(result.success).toBe(false);
  });

  it("exige exatamente um morador principal", () => {
    const noPrimary = unitFormSchema.safeParse({
      number: "1203",
      residents: [{ ...validPrimary, primary: false }],
    });
    expect(noPrimary.success).toBe(false);

    const twoPrimaries = unitFormSchema.safeParse({
      number: "1203",
      residents: [validPrimary, { ...validPrimary, primary: true, cpf: "11144477735" }],
    });
    expect(twoPrimaries.success).toBe(false);
  });

  it("rejeita CPF repetido na mesma unidade (RN-08)", () => {
    const result = unitFormSchema.safeParse({
      number: "1203",
      residents: [
        validPrimary,
        { ...validMember, cpf: validPrimary.cpf, primary: false },
      ],
    });
    expect(result.success).toBe(false);
  });

  it("aceita moradores adicionais só com nome e telefone", () => {
    const result = unitFormSchema.safeParse({
      number: "1203",
      residents: [validPrimary, validMember],
    });
    expect(result.success).toBe(true);
  });
});

describe("buildCreatePayload", () => {
  it("separa o principal dos adicionais no formato do contrato (POST /units)", () => {
    const parsed = unitFormSchema.parse({
      block: "A",
      number: "1203",
      residents: [validMember, validPrimary],
    });

    expect(buildCreatePayload(parsed)).toEqual({
      block: "A",
      number: "1203",
      primary: {
        name: validPrimary.name,
        phone: validPrimary.phone,
        email: validPrimary.email,
        cpf: validPrimary.cpf,
      },
      members: [
        {
          name: validMember.name,
          phone: validMember.phone,
          email: undefined,
          cpf: undefined,
        },
      ],
    });
  });

  it("omite bloco vazio", () => {
    const parsed = unitFormSchema.parse({
      number: "101",
      residents: [validPrimary],
    });
    expect(buildCreatePayload(parsed).block).toBeUndefined();
  });
});

describe("buildTransferPayload", () => {
  it("monta { primary, members } sem bloco/número", () => {
    const parsed = transferFormSchema.parse({
      residents: [validPrimary, validMember],
    });
    const payload = buildTransferPayload(parsed);
    expect(payload).toEqual({
      primary: {
        name: validPrimary.name,
        phone: validPrimary.phone,
        email: validPrimary.email,
        cpf: validPrimary.cpf,
      },
      members: [
        {
          name: validMember.name,
          phone: validMember.phone,
          email: undefined,
          cpf: undefined,
        },
      ],
    });
    expect(payload).not.toHaveProperty("block");
  });
});

describe("buildEditPayload", () => {
  it("mantém id de quem já existia e omite de quem é novo (PUT /units/{id})", () => {
    const parsed = unitFormSchema.parse({
      number: "1203",
      residents: [
        { ...validPrimary, id: "resident-1" },
        { ...validMember, id: undefined },
      ],
    });

    const payload = buildEditPayload(parsed);
    expect(payload.residents).toEqual([
      {
        id: "resident-1",
        name: validPrimary.name,
        phone: validPrimary.phone,
        email: validPrimary.email,
        cpf: validPrimary.cpf,
        primary: true,
      },
      {
        id: undefined,
        name: validMember.name,
        phone: validMember.phone,
        email: undefined,
        cpf: undefined,
        primary: false,
      },
    ]);
  });
});

describe("emptyResident", () => {
  it("cria morador em branco, principal quando pedido", () => {
    expect(emptyResident(true)).toEqual({
      name: "",
      phone: "",
      email: "",
      cpf: "",
      primary: true,
    });
    expect(emptyResident()).toMatchObject({ primary: false });
  });
});

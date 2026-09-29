/**
 * Validação e máscara de CPF — só para UX (RN-07). Quem valida de verdade é o
 * backend (`docs/03-api.md`, `CPF_INVALID`).
 */

function digitsOnly(value: string): string {
  return value.replace(/\D/g, "");
}

function checkDigit(digits: string, weightStart: number): number {
  const sum = digits
    .split("")
    .reduce((acc, digit, index) => acc + Number(digit) * (weightStart - index), 0);
  const remainder = (sum * 10) % 11;
  return remainder === 10 ? 0 : remainder;
}

/** Dígitos verificadores válidos e não todos os dígitos iguais (ex. `111.111.111-11`). */
export function isValidCpf(value: string): boolean {
  const digits = digitsOnly(value);
  if (digits.length !== 11) return false;
  if (/^(\d)\1{10}$/.test(digits)) return false;

  const firstCheck = checkDigit(digits.slice(0, 9), 10);
  const secondCheck = checkDigit(digits.slice(0, 10), 11);
  return firstCheck === Number(digits[9]) && secondCheck === Number(digits[10]);
}

/** `52998224725` → `529.982.247-25`. Entrada sem 11 dígitos volta como veio. */
export function maskCpf(value: string): string {
  const digits = digitsOnly(value);
  if (digits.length !== 11) return value;
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`;
}

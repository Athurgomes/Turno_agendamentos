/** Mensagem de WhatsApp de credenciais (RF-UNI-02) — texto fixo, só interpola dados. */
import type { Credentials } from "../../shared/api/types";

export function buildWhatsAppMessage(
  residentName: string,
  credentials: Credentials,
  origin: string,
): string {
  return (
    `Olá, ${residentName}! Seu acesso ao sistema de reservas do condomínio: ` +
    `usuário ${credentials.username}, senha temporária ${credentials.tempPassword}. ` +
    `Entre em ${origin} e troque a senha no primeiro acesso.`
  );
}

export function buildWhatsAppUrl(phone: string, message: string): string {
  return `https://wa.me/${phone}?text=${encodeURIComponent(message)}`;
}

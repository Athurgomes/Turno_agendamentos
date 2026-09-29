/**
 * Limite de fotos por envio (`docs/03-api.md` "Áreas": cadastro e upload avulso aceitam 1 a 10
 * fotos; vistoria aceita 0 a 10). Validação aqui é só UX — o backend confere de novo.
 */
export const MAX_PHOTOS_PER_UPLOAD = 10;

/** Report (RN-35) e fotos do reparo (RF-REP-04) aceitam no máximo 5 por envio. */
export const MAX_REPORT_PHOTOS = 5;

/** Mensagem pt-BR quando a seleção passa do limite, ou `null` se está dentro dele. */
export function photoCountErrorMessage(count: number, max: number = MAX_PHOTOS_PER_UPLOAD): string | null {
  if (count > max) {
    return `Selecione no máximo ${max} fotos por vez.`;
  }
  return null;
}

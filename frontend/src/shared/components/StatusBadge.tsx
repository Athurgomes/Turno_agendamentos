/**
 * Pílula de status genérica (`DESIGN.md` "Badge de status" — RN-14, RN-25/29/32, RN-36): par
 * fundo/texto vem sempre da paleta de status do design system, nunca de cor solta. Um badge por
 * card/linha; o rótulo de texto sempre acompanha a cor (WCAG 1.4.1).
 */
export interface StatusBadgeProps {
  label: string;
  toneClassName: string;
}

export function StatusBadge({ label, toneClassName }: StatusBadgeProps) {
  return (
    <span className={`rounded-pill px-3 py-1 text-sm font-medium ${toneClassName}`}>{label}</span>
  );
}

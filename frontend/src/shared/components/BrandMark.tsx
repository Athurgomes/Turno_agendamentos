/**
 * Ícone da marca "Turno" (casa), reaproveitado em `public/favicon.svg` e nas
 * telas que exibem o logotipo (RNF-06: decorativo aqui, então `aria-hidden`;
 * quem precisar anunciar o nome do produto usa texto ao lado, não o ícone).
 */
interface BrandMarkProps {
  className?: string;
}

export function BrandMark({ className }: BrandMarkProps) {
  return (
    <svg
      viewBox="0 0 32 32"
      fill="none"
      aria-hidden="true"
      className={className}
    >
      <rect width="32" height="32" rx="8" fill="#1D4ED8" />
      <path
        d="M8 16.5 16 9l8 7.5M11 15v8h10v-8"
        stroke="#ffffff"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

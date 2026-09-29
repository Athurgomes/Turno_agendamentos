/**
 * Classes Tailwind compartilhadas de campo/textarea (`DESIGN.md` "Campo com label"): altura
 * mínima de toque, anel de foco visível e borda de erro. Único lugar de estilo de campo — não
 * duplicar em cada formulário.
 */
export function fieldClassName(hasError: boolean) {
  return [
    "h-11 w-full rounded-sm border bg-neutral-0 px-3 text-base text-neutral-900",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600",
    hasError ? "border-danger-600" : "border-neutral-300",
  ].join(" ");
}

export function textareaClassName(hasError: boolean) {
  return [
    "w-full max-w-[70ch] rounded-sm border bg-neutral-0 px-3 py-2 text-base text-neutral-900",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600",
    hasError ? "border-danger-600" : "border-neutral-300",
  ].join(" ");
}

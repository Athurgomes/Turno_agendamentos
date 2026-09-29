/** Página provisória: o conteúdo real chega na fase que implementa cada tela. */
export function Placeholder({ title }: { title: string }) {
  return (
    <div className="p-4 sm:p-6">
      <h1 className="text-xl font-semibold text-neutral-900 sm:text-2xl">
        {title}
      </h1>
      <p className="mt-2 text-sm text-neutral-600">
        Esta tela ainda será implementada.
      </p>
    </div>
  );
}

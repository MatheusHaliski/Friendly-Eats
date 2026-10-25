'use client';

import { PlaceSearch } from './PlaceSearch';

/** Explorar (RF10): busca por nome, filtros na URL e lista de cards; o cartão abre o modal do lugar. */
export function ExploreView() {
  return (
    <div className="grid gap-6">
      <div className="grid gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight sm:text-4xl">Explorar</h1>
        <p className="text-muted">Restaurantes, bares, cafés, padarias e mercados avaliados por quem come.</p>
      </div>
      <PlaceSearch basePath="/" />
    </div>
  );
}

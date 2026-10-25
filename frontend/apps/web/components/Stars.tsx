import { IconStar, IconStarHalf } from './icons';

/** Arredonda para meia estrela só como apoio visual; o sinal principal é o número ao lado. */
export function starShapes(average: number): Array<'cheia' | 'meia' | 'vazia'> {
  const halves = Math.round(average * 2);
  return Array.from({ length: 5 }, (_, i) => {
    const v = halves - i * 2;
    return v >= 2 ? 'cheia' : v === 1 ? 'meia' : 'vazia';
  });
}

/** Estrelas da média (decorativas: aria-hidden). Meia estrela pela forma: metade cheia sobre o contorno. */
export function Stars({ average, size = 18 }: { average: number; size?: number }) {
  return (
    <span className="inline-flex items-center" aria-hidden="true" data-testid="stars">
      {starShapes(average).map((shape, i) => (
        <span key={i} className="relative inline-flex" data-star={shape} style={{ width: size, height: size }}>
          <IconStar size={size} className={shape === 'cheia' ? 'text-star' : 'text-star-empty'} fill={shape === 'cheia' ? 'currentColor' : 'none'} />
          {shape === 'meia' && <IconStarHalf size={size} className="absolute inset-0 text-star" fill="currentColor" />}
        </span>
      ))}
    </span>
  );
}

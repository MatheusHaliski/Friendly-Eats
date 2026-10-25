'use client';

import type { BackgroundView, PostBorder } from '@friendly-eats/api';
import { tokens } from '@friendly-eats/tokens';
import type { CSSProperties, ReactNode } from 'react';
import { artCss, bestInk, DARK_INK, mixHex, softerInk } from '@/lib/frame';

/**
 * Variáveis do container emoldurado: a cor e a tinta vêm da moldura (RF16); os tons secundários são derivados da
 * tinta até passarem de 4,5:1 (texto) e 3:1 (contorno). As cores de categoria (bolinha, alerta) usam o tema que
 * combina com o container e ficam numa pílula com o fundo desse tema, onde foram medidas.
 */
export function containerStyle(color: string, ink: string): CSSProperties {
  const theme = bestInk(color) === DARK_INK ? tokens.light : tokens.dark;
  const vars: Record<string, string> = {};
  for (const [name, value] of Object.entries(theme)) vars[`--fe-${name}`] = value;
  Object.assign(vars, {
    '--fe-color-bg': color,
    '--fe-color-surface': color,
    '--fe-color-text': ink,
    '--fe-color-text-muted': softerInk(ink, color, 0.62, 4.5),
    '--fe-color-border': mixHex(ink, color, 0.25),
    '--fe-color-border-strong': softerInk(ink, color, 0.45, 3),
    '--fe-color-surface-sunken': mixHex(ink, color, 0.1),
    '--fe-color-focus': ink,
    '--fe-color-primary': ink,
    '--fe-color-on-primary': color,
    '--fe-color-brand': ink,
    '--fe-badge-bg': theme['color-bg'],
    '--fe-badge-ink': theme['color-text'],
  });
  return { ...vars, colorScheme: theme === tokens.light ? 'light' : 'dark' } as CSSProperties;
}

interface FrameProps {
  background: BackgroundView | null;
  /** Feeds sem moldura (alerta): o card aparece sem a arte, mesmo que viesse uma. */
  framed: boolean;
  border: PostBorder;
  expired?: boolean;
  /** Semente da arte gerada no cliente (o id do post). */
  seed: string;
  /** Animação só quando a pessoa não pediu "Reduzir movimento". */
  motion: boolean;
  /** Elemento do container: article no feed, div na página do post. */
  as?: 'article' | 'div';
  labelledBy?: string;
  testId?: string;
  children: ReactNode;
}

/** Moldura (arte só na faixa externa) + container opaco com o conteúdo; sem moldura, só o container. */
export function PostFrame({ background, framed, border, expired, seed, motion, as = 'article', labelledBy, testId, children }: FrameProps) {
  const Tag = as;
  const bg = framed ? background : null;
  const container = (
    <Tag
      className="fe-post"
      data-border={border}
      data-expired={expired ? 'true' : undefined}
      data-framed={bg ? 'true' : undefined}
      aria-labelledby={labelledBy}
      data-testid={testId}
      style={bg ? containerStyle(bg.container.color, bg.container.ink) : undefined}
    >
      {children}
    </Tag>
  );
  if (!bg) return container;
  const layout = bg.config.layoutId ?? 'CLASSICO';
  const css = artCss(bg, seed);
  const aura = bg.config.aura;
  const mosaic = bg.artKind === 'AURA' && aura?.format === 'MOSAICO';
  const animation = motion && bg.config.animationId && bg.config.animationId !== 'NONE' ? bg.config.animationId : undefined;
  const paint = (key?: number) =>
    bg.artKind === 'LAYOUT' ? (
      <span key={key} className="fe-frame-paint fe-frame-lousa" />
    ) : (
      <span key={key} className="fe-frame-paint" style={css ? { background: css } : undefined} />
    );
  return (
    <div className="fe-frame" data-layout={layout} data-anim={animation} data-art={bg.artKind} data-testid={testId ? `${testId}-moldura` : undefined}>
      <div className="fe-frame-art" aria-hidden="true" data-soft={aura?.variantId === 'SUAVE' ? 'true' : undefined} data-mosaic={mosaic ? 'true' : undefined}>
        {mosaic
          ? [0, 1, 2, 3].map((i) => (
              <span key={i} className="fe-frame-tile">
                {paint()}
              </span>
            ))
          : paint()}
      </div>
      {bg.config.materialId && bg.artKind !== 'LAYOUT' && (
        <span className={`fe-frame-mat fe-mat-${bg.config.materialId}`} aria-hidden="true" data-material={bg.config.materialId} />
      )}
      {animation === 'FUMEGAR' && <span className="fe-frame-steam" aria-hidden="true" />}
      {container}
    </div>
  );
}

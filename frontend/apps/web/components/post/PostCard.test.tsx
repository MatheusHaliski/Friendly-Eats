import type { PostDetailView } from '@friendly-eats/api';
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import postsJson from '../../e2e/fixtures/posts.json';
import { PostCard } from './PostCard';
import { containerStyle } from './PostFrame';

const posts = postsJson as unknown as PostDetailView[];
const card = (n: number) => {
  const p = posts.find((x) => x.id.endsWith(String(n).padStart(12, '0')))!;
  return { ...p, excerpt: p.body, detailPath: `/api/v1/posts/${p.id}` };
};

describe('PostCard (RF14 · RF16)', () => {
  it('arte só na faixa externa (decorativa); texto, bolinha e selo do tipo no container', () => {
    const { container } = render(<PostCard post={card(1)} motion={false} />);
    const art = container.querySelector('.fe-frame-art')!;
    expect(art).toHaveAttribute('aria-hidden', 'true');
    expect(art.textContent).toBe('');
    const article = screen.getByRole('article', { name: 'PF caprichado no Sabor Mineiro' });
    expect(article).toHaveClass('fe-post');
    expect(within(article).getByTestId('tipo-post')).toHaveTextContent('Foto de prato');
    expect(within(article).getByTestId('tipo-conta')).toHaveTextContent('Comensal');
    expect(within(article).getByRole('img', { name: /Prato feito visto de cima/ })).toBeInTheDocument();
    expect(within(article).getByRole('link', { name: 'Júlia Andrade' })).toHaveAttribute('href', '/u/42');
  });

  it('sem "Reduzir movimento" não liga a animação; com movimento, liga', () => {
    const { container, rerender } = render(<PostCard post={card(2)} motion={false} />);
    expect(container.querySelector('.fe-frame')).not.toHaveAttribute('data-anim');
    expect(container.querySelector('.fe-frame-steam')).toBeNull();
    rerender(<PostCard post={card(2)} motion />);
    expect(container.querySelector('.fe-frame')).toHaveAttribute('data-anim', 'FUMEGAR');
  });

  it('alerta sanitário: faixa hazard com a fonte e nunca moldura', () => {
    const { container } = render(<PostCard post={card(20)} motion />);
    expect(container.querySelector('.fe-frame')).toBeNull();
    expect(screen.getByTestId('faixa-alerta')).toHaveTextContent('Alerta sanitário·Fonte: Anvisa (www.gov.br)');
  });

  it('encerrado: rótulo em texto e borda tracejada', () => {
    render(<PostCard post={card(8)} motion={false} />);
    expect(screen.getByTestId('chip-encerrado')).toHaveTextContent('Encerrado');
    expect(screen.getByRole('article')).toHaveAttribute('data-expired', 'true');
  });

  it('container emoldurado: tinta e tons secundários com 4,5:1 e a pílula da bolinha no fundo do tema', () => {
    const s = containerStyle('#F5EDE3', '#17120D') as Record<string, string>;
    expect(s['--fe-color-text']).toBe('#17120D');
    expect(s['--fe-badge-bg']).toBe('#FFFFFF');
    expect(s.colorScheme).toBe('light');
    const d = containerStyle('#2A2119', '#F5EDE3') as Record<string, string>;
    expect(d['--fe-badge-bg']).toBe('#17120D');
    expect(d['--fe-color-account-oficial-ring']).toBe('#D6D0C8');
  });
});

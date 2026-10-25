import type { FrameCatalog } from '@friendly-eats/api';
import { describe, expect, it } from 'vitest';
import catalogJson from '../e2e/fixtures/catalogo-molduras.json';
import {
  DEFAULT_FRAME,
  applyStudio,
  artSources,
  bestInk,
  containerOf,
  contrastRatio,
  feiraArt,
  formatRatio,
  fromConfig,
  previewBackground,
  seasonGradient,
  softerInk,
  toConfig,
  validateFrame,
  type FrameState,
} from './frame';

const catalog = catalogJson as unknown as FrameCatalog;
const start = (): FrameState => structuredClone(DEFAULT_FRAME);

describe('contraste (o mesmo do Contrast.java)', () => {
  it('razão e formato com vírgula', () => {
    expect(formatRatio(contrastRatio('#F5EDE3', '#2A2119'))).toBe('13,6:1');
    expect(formatRatio(contrastRatio('#17120D', '#E88F12'))).toBe('7,4:1');
    expect(formatRatio(4.5)).toBe('4,5:1');
    expect(formatRatio(4.45)).toBe('4,5:1');
    expect(formatRatio(contrastRatio('#FFFFFF', '#FFFFFF'))).toBe('1,0:1');
  });

  it('tinta automática: a clara em fundo escuro, a escura em fundo claro', () => {
    expect(bestInk('#2A2119')).toBe('#F5EDE3');
    expect(bestInk('#F5EDE3')).toBe('#17120D');
    expect(bestInk('#E88F12')).toBe('#17120D');
    expect(bestInk('#808080')).toBe('#17120D');
  });

  it('tom secundário chega ao mínimo pedido', () => {
    const t2 = softerInk('#F5EDE3', '#2A2119', 0.5, 4.5);
    expect(contrastRatio(t2, '#2A2119')).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(t2, '#2A2119')).toBeLessThan(contrastRatio('#F5EDE3', '#2A2119'));
  });
});

describe('uma fonte de arte por vez', () => {
  it('cor → gradiente → cartela → padrão: cada escolha tira a anterior e avisa', () => {
    let r = applyStudio(start(), { type: 'color', id: '#3b1f2b' }, catalog);
    expect(r.state.color).toBe('#3B1F2B');
    expect(r.notice).toBeNull();

    r = applyStudio(r.state, { type: 'gradient', id: 'brasa' }, catalog);
    expect(r.state).toMatchObject({ color: null, gradientId: 'brasa' });
    expect(r.notice).toBe('Uma arte por vez: a cor Berinjela saiu.');

    r = applyStudio(r.state, { type: 'season', presetId: null, auto: true }, catalog);
    expect(r.state).toMatchObject({ gradientId: null, seasonal: { presetId: null, auto: true } });
    expect(r.notice).toBe('Uma arte por vez: o gradiente Brasa saiu.');

    r = applyStudio(r.state, { type: 'aura', id: 'azulejo' }, catalog);
    expect(r.state.seasonal).toEqual({ presetId: null, auto: false });
    expect(r.state.aura).toEqual({ presetId: 'azulejo', variantId: 'ORIGINAL', format: 'IMAGEM_UNICA' });
    expect(artSources(r.state)).toBe(1);

    r = applyStudio(r.state, { type: 'auraFormat', format: 'MOSAICO' }, catalog);
    r = applyStudio(r.state, { type: 'aura', id: 'forno' }, catalog);
    expect(r.state.aura).toEqual({ presetId: 'forno', variantId: 'ORIGINAL', format: 'MOSAICO' });
  });

  it('cartela escolhida e automática não convivem', () => {
    let s = applyStudio(start(), { type: 'season', presetId: 'natal', auto: false }, catalog).state;
    expect(s.seasonal).toEqual({ presetId: 'natal', auto: false });
    s = applyStudio(s, { type: 'season', presetId: 'natal', auto: true }, catalog).state;
    expect(s.seasonal).toEqual({ presetId: null, auto: true });
  });
});

describe('layout Cardápio (arte própria)', () => {
  it('limpa a arte e o material e explica numa frase', () => {
    let s = applyStudio(start(), { type: 'aura', id: 'toalha-xadrez' }, catalog).state;
    s = applyStudio(s, { type: 'material', id: 'linho' }, catalog).state;
    const r = applyStudio(s, { type: 'layout', id: 'CARDAPIO' }, catalog);
    expect(r.state).toMatchObject({ layoutId: 'CARDAPIO', aura: null, materialId: null, color: null, gradientId: null });
    expect(r.notice).toBe('Cardápio (lousa) já traz arte própria: o padrão Toalha xadrez e o material Linho saíram.');
    expect(validateFrame(r.state, catalog, 'POST')).toEqual([]);
  });

  it('escolher arte ou material no Cardápio volta o layout para Clássico', () => {
    const cardapio = applyStudio(start(), { type: 'layout', id: 'CARDAPIO' }, catalog).state;
    const r = applyStudio(cardapio, { type: 'gradient', id: 'matcha' }, catalog);
    expect(r.state).toMatchObject({ layoutId: 'CLASSICO', gradientId: 'matcha' });
    expect(r.notice).toBe('O layout voltou para Clássico: Cardápio (lousa) já traz arte própria.');
    expect(applyStudio(cardapio, { type: 'material', id: 'vidro' }, catalog).state.layoutId).toBe('CLASSICO');
  });

  it('a validação recusa o que o servidor recusa', () => {
    const bad: FrameState = { ...start(), color: '#2A2119', gradientId: 'brasa', layoutId: 'CARDAPIO', materialId: 'inox' };
    expect(validateFrame(bad, catalog, 'POST')).toEqual([
      'Escolha uma única arte para a moldura: cor, gradiente, cartela, padrão ou arte própria.',
      'O layout Cardápio (lousa) já traz arte própria: não combina com outra arte nem material.',
    ]);
  });
});

describe('container e contraste', () => {
  it('AUTO usa a melhor tinta sobre a cor da skin ou a escolhida', () => {
    expect(containerOf(start(), catalog)).toMatchObject({ color: '#2A2119', ink: '#F5EDE3', contrast: '13,6:1', ok: true });
    const laranja = applyStudio(start(), { type: 'containerColor', color: '#E88F12' }, catalog).state;
    expect(containerOf(laranja, catalog)).toMatchObject({ ink: '#17120D', contrast: '7,4:1', ok: true });
  });

  it('tinta fixa com menos de 4,5:1 avisa e sugere a que passa', () => {
    let s = applyStudio(start(), { type: 'containerColor', color: '#E9C46A' }, catalog).state;
    s = applyStudio(s, { type: 'ink', ink: '#F5EDE3' }, catalog).state;
    const c = containerOf(s, catalog);
    expect(c).toMatchObject({ ok: false, contrast: '1,4:1', suggestion: '#17120D' });
    expect(validateFrame(s, catalog, 'POST')).toEqual([
      `Texto com contraste 1,4:1 sobre o container (mínimo 4,5:1). Sugestão: tinta #17120D (${c.suggestionContrast}).`,
    ]);
  });

  it('alerta sanitário não tem moldura', () => {
    const s = applyStudio(start(), { type: 'color', id: '#2A2119' }, catalog).state;
    expect(validateFrame(s, catalog, 'ALERT')).toEqual([
      'Alertas sanitários não têm moldura: a faixa de alerta precisa ser reconhecida na hora.',
    ]);
    expect(validateFrame(start(), catalog, 'ALERT')).toEqual([]);
  });
});

describe('configuração enviada e prévia', () => {
  it('padrão vira null; só as chaves escolhidas vão no background', () => {
    expect(toConfig(start())).toBeNull();
    let s = applyStudio(start(), { type: 'aura', id: 'neon' }, catalog).state;
    s = applyStudio(s, { type: 'ink', ink: 'AUTO' }, catalog).state;
    s = applyStudio(s, { type: 'animation', id: 'FUMEGAR' }, catalog).state;
    expect(toConfig(s)).toEqual({ v: 1, aura: { presetId: 'neon', variantId: 'ORIGINAL', format: 'IMAGEM_UNICA' }, animationId: 'FUMEGAR' });
    expect(fromConfig(toConfig(s))).toEqual(s);
  });

  it('a forma canônica do servidor volta para o mesmo estado', () => {
    const canonical = {
      v: 1 as const,
      color: null,
      gradientId: 'cafe',
      seasonal: { presetId: null, auto: false },
      animationId: 'NONE',
      aura: null,
      materialId: 'vidro',
      layoutId: 'POLAROID' as const,
      skinId: 'PADRAO',
      container: { color: '#F5EDE3', ink: 'AUTO' },
    };
    expect(toConfig(fromConfig(canonical))).toEqual({ v: 1, gradientId: 'cafe', materialId: 'vidro', layoutId: 'POLAROID', container: { color: '#F5EDE3' } });
  });

  it('cartela: o mesmo gradiente do BackgroundService (135°, paradas inteiras)', () => {
    expect(seasonGradient(['#A', '#B', '#C'].map((c) => c.padEnd(7, '0')))).toBe('linear-gradient(135deg,#A00000 0%,#B00000 50%,#C00000 100%)');
    const auto = applyStudio(start(), { type: 'season', presetId: null, auto: true }, catalog).state;
    const bg = previewBackground(auto, catalog)!;
    expect(bg).toMatchObject({ artKind: 'SEASONAL', artId: 'primavera' });
    expect(bg.css).toBe('linear-gradient(135deg,#F28AB2 0%,#E63946 25%,#90BE6D 50%,#F6C90E 75%,#FFF0F3 100%)');
    expect(previewBackground(applyStudio(start(), { type: 'layout', id: 'CARDAPIO' }, catalog).state, catalog)).toMatchObject({
      artKind: 'LAYOUT',
      artId: 'CARDAPIO',
      css: null,
    });
  });

  it('Feira é gerada no cliente e é sempre igual para o mesmo post', () => {
    const a = feiraArt(['#E63946', '#F7C948'], 'post-1');
    expect(a).toBe(feiraArt(['#E63946', '#F7C948'], 'post-1'));
    expect(a).not.toBe(feiraArt(['#E63946', '#F7C948'], 'post-2'));
    expect(a).toMatch(/^url\("data:image\/svg\+xml,/);
    expect(a).not.toContain('#');
  });
});

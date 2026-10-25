import type { PostDetailView, PostVocabulary } from '@friendly-eats/api';
import { describe, expect, it } from 'vitest';
import vocabularyJson from '../e2e/fixtures/vocabulario-posts.json';
import postsJson from '../e2e/fixtures/posts.json';
import { buildDraft, buildPatch, emptyValues, formSpec, fromLocalInput, parseRequirement, publishableKinds, toLocalInput, valuesFromPost } from './post-form';

const vocabulary = vocabularyJson as unknown as PostVocabulary;
const kind = (code: string) => vocabulary.kinds.find((k) => k.code === code)!;
const sorted = (s: Set<string>) => [...s].sort();

describe('requisitos do vocabulário', () => {
  it('lê alternativas, "+" e qualificadores', () => {
    expect(parseRequirement('priceFromCents+priceToCents | discountPercent')).toEqual([
      { fields: ['priceFromCents', 'priceToCents'], qualifier: null },
      { fields: ['discountPercent'], qualifier: null },
    ]);
    expect(parseRequirement('establishmentId (dono) | advertising: true (criador)')).toEqual([
      { fields: ['establishmentId'], qualifier: 'dono' },
      { fields: ['advertising'], qualifier: 'criador' },
    ]);
    expect(parseRequirement('alert.sourceUrl (https)')).toEqual([{ fields: ['alert.sourceUrl'], qualifier: null }]);
  });
});

describe('formulário por tipo', () => {
  it('foto de prato: foto e texto alternativo obrigatórios, preço opcional, sem receita nem vigência', () => {
    const s = formSpec(kind('FOTO_PRATO'), 'COMENSAL');
    expect(sorted(s.required)).toEqual(['photo', 'title']);
    expect(s.fields.has('priceCents')).toBe(true);
    expect(s.fields.has('recipe')).toBe(false);
    expect(s.fields.has('period')).toBe(false);
    expect(s.fields.has('background')).toBe(true);
  });

  it('receita: ingredientes e passos obrigatórios; rendimento e tempo opcionais', () => {
    const s = formSpec(kind('RECEITA'), 'CHEF');
    expect(sorted(s.required)).toEqual(['recipe', 'title']);
    expect(s.fields.has('servings') && s.fields.has('prepMinutes')).toBe(true);
    expect(s.fields.has('priceCents')).toBe(false);
  });

  it('promoção: o dono precisa do lugar; o criador, do chip Publicidade; de/por ou desconto', () => {
    const dono = formSpec(kind('PROMOCAO'), 'ESTABELECIMENTO');
    // de/por e desconto ficam no mesmo grupo da tela (escolha entre os dois)
    expect(sorted(dono.required)).toEqual(['period', 'place', 'promoPrice', 'title']);
    expect(dono.oneOf).toEqual([]);
    const criador = formSpec(kind('PROMOCAO'), 'CRIADOR');
    expect(sorted(criador.required)).toEqual(['advertising', 'period', 'promoPrice', 'title']);
    expect(criador.fields.has('place')).toBe(true);
    expect(criador.fields.has('promoPrice')).toBe(true);
    expect(emptyValues(kind('PROMOCAO'), 'CRIADOR').advertising).toBe(true);
    expect(emptyValues(kind('PROMOCAO'), 'ESTABELECIMENTO').advertising).toBe(false);
  });

  it('evento: preço ou gratuito, um dos dois', () => {
    const s = formSpec(kind('EVENTO'), 'COMUNIDADE');
    expect(s.oneOf).toEqual([['priceCents', 'free']]);
    expect(s.fields.has('free')).toBe(true);
  });

  it('alerta sanitário: os campos do alerta; sem moldura, sem publicidade e sem classificação', () => {
    const s = formSpec(kind('ALERTA_SANITARIO'), 'OFICIAL');
    expect(sorted(s.required)).toEqual(['alert', 'title']);
    for (const k of ['background', 'advertising', 'ageRating'] as const) expect(s.fields.has(k)).toBe(false);
  });

  it('só os tipos que a conta publica', () => {
    expect(publishableKinds(vocabulary.kinds, ['RECEITA', 'FOTO_PRATO']).map((k) => k.code)).toEqual(['FOTO_PRATO', 'RECEITA']);
  });
});

describe('pedido', () => {
  it('foto de prato sem foto e sem texto alternativo: erros por campo, antes de enviar', () => {
    const s = formSpec(kind('FOTO_PRATO'), 'COMENSAL');
    const r = buildDraft(s, { ...emptyValues(s.kind, 'COMENSAL'), title: 'Moqueca' }, { key: null, keepCurrent: false }, null);
    expect(r.errors).toEqual({ photo: ['Envie a foto do prato.'] });
    const r2 = buildDraft(s, { ...emptyValues(s.kind, 'COMENSAL'), title: 'Moqueca' }, { key: 'uploads/42/a.jpg', keepCurrent: false }, null);
    expect(r2.errors).toEqual({ altText: ['Descreva a foto para quem usa leitor de tela.'] });
  });

  it('só vão os campos do tipo; preço em centavos; moldura junto', () => {
    const s = formSpec(kind('FOTO_PRATO'), 'COMENSAL');
    const v = { ...emptyValues(s.kind, 'COMENSAL'), title: ' Moqueca ', altText: 'Panela de barro', price: '42,5', allergens: { PEIXES: 'CONTEM' as const } };
    const { draft, errors } = buildDraft(s, v, { key: 'uploads/42/a.jpg', keepCurrent: false }, { v: 1, gradientId: 'brasa' });
    expect(errors).toEqual({});
    expect(draft).toMatchObject({
      kind: 'FOTO_PRATO',
      title: 'Moqueca',
      mediaObjectKey: 'uploads/42/a.jpg',
      altText: 'Panela de barro',
      priceCents: 4250,
      allergens: { PEIXES: 'CONTEM' },
      background: { v: 1, gradientId: 'brasa' },
    });
    for (const k of ['recipe', 'alert', 'startsAt', 'priceFromCents', 'free']) expect(draft).not.toHaveProperty(k);
  });

  it('promoção: "de" maior que "por"; desconto de 1 a 90', () => {
    const s = formSpec(kind('PROMOCAO'), 'ESTABELECIMENTO');
    const base = { ...emptyValues(s.kind, 'ESTABELECIMENTO'), title: 'Feijoada', establishmentId: 1, startsAt: '2026-10-26T12:00', endsAt: '2026-10-26T15:00' };
    expect(buildDraft(s, { ...base, priceFrom: '30', priceTo: '35' }, { key: null, keepCurrent: false }, null).errors).toEqual({
      priceTo: ['O preço "de" precisa ser maior que o "por".'],
    });
    const ok = buildDraft(s, { ...base, promoMode: 'DISCOUNT', discount: '20' }, { key: null, keepCurrent: false }, null);
    expect(ok.errors).toEqual({});
    expect(ok.draft).toMatchObject({ discountPercent: 20, priceFromCents: null, priceToCents: null, establishmentId: 1 });
    expect(ok.draft.startsAt).toBe(new Date('2026-10-26T12:00').toISOString());
  });

  it('evento gratuito manda free e limpa o preço', () => {
    const s = formSpec(kind('EVENTO'), 'COMUNIDADE');
    const v = { ...emptyValues(s.kind, 'COMUNIDADE'), title: 'Feira', startsAt: '2026-11-01T09:00', endsAt: '2026-11-01T13:00', eventMode: 'FREE' as const };
    expect(buildDraft(s, v, { key: null, keepCurrent: false }, null).draft).toMatchObject({ free: true, priceCents: null });
    expect(buildDraft(s, { ...v, eventMode: 'PAID' }, { key: null, keepCurrent: false }, null).errors).toEqual({
      price: ['Informe o preço ou marque "Gratuito".'],
    });
  });

  it('receita e alerta: uma linha por item', () => {
    const r = formSpec(kind('RECEITA'), 'CHEF');
    const rv = { ...emptyValues(r.kind, 'CHEF'), title: 'Pão de queijo', ingredients: '500 g de polvilho\n\n2 ovos ', steps: 'Misture\nAsse', servings: '20' };
    expect(buildDraft(r, rv, { key: null, keepCurrent: false }, null).draft.recipe).toEqual({
      ingredients: ['500 g de polvilho', '2 ovos'],
      steps: ['Misture', 'Asse'],
      servings: 20,
      prepMinutes: null,
    });
    const a = formSpec(kind('ALERTA_SANITARIO'), 'OFICIAL');
    const av = { ...emptyValues(a.kind, 'OFICIAL'), title: 'Recolhimento', alertSourceUrl: 'http://anvisa.gov.br' };
    expect(Object.keys(buildDraft(a, av, { key: null, keepCurrent: false }, null).errors).sort()).toEqual([
      'alertBrand',
      'alertLots',
      'alertProduct',
      'alertReason',
      'alertSourceUrl',
    ]);
  });

  it('edição: não manda o tipo; foto e moldura só quando mudam', () => {
    const post = (postsJson as unknown as PostDetailView[]).find((p) => p.kind.code === 'FOTO_PRATO' && p.mediaUrl)!;
    const s = formSpec(kind('FOTO_PRATO'), post.author.category!.code);
    const v = valuesFromPost(post);
    const kept = buildPatch(s, v, { key: null, keepCurrent: true, removed: false }, null, false);
    expect(kept.errors).toEqual({});
    expect(kept.patch).not.toHaveProperty('kind');
    expect(kept.patch).not.toHaveProperty('mediaObjectKey');
    expect(kept.patch).not.toHaveProperty('background');
    expect(kept.patch.altText).toBe(post.altText);
    const cleared = buildPatch(s, v, { key: null, keepCurrent: true, removed: false }, null, true);
    expect(cleared.patch.background).toBeNull();
  });

  it('datetime-local ida e volta no fuso do navegador', () => {
    const iso = fromLocalInput('2026-11-01T18:30')!;
    expect(toLocalInput(iso)).toBe('2026-11-01T18:30');
    expect(fromLocalInput('')).toBeNull();
  });
});

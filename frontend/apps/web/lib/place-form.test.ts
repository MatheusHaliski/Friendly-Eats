import type { ActionProviderItem } from '@friendly-eats/api';
import { describe, expect, it } from 'vitest';
import { buildPlaceDraft, emptyPlaceValues, mapPlaceErrors, type PlaceFormValues } from './place-form';

const PROVIDERS: ActionProviderItem[] = [
  { code: 'IFOOD', label: 'iFood', kind: 'DELIVERY', domains: ['ifood.com.br'] },
  { code: 'RAPPI', label: 'Rappi', kind: 'DELIVERY', domains: ['rappi.com.br'] },
  { code: 'THEFORK', label: 'TheFork', kind: 'RESERVA', domains: ['thefork.com.br'] },
  { code: 'WHATSAPP', label: 'WhatsApp', kind: 'WHATSAPP', domains: ['wa.me'] },
];

function filled(): PlaceFormValues {
  return {
    ...emptyPlaceValues(),
    name: ' Sabor Mineiro ',
    primaryType: 'RESTAURANTE_A_LA_CARTE',
    primaryCuisine: 'MINEIRA',
    uf: 'MG',
    municipalityIbge: '3106200',
    city: 'Belo Horizonte',
    street: 'Rua Sergipe',
    latitude: '-19,937',
    longitude: '-43.933',
    priceMin: '45',
    priceMax: '65,50',
  };
}

describe('buildPlaceDraft', () => {
  it('monta o corpo com horários, cobranças, facetas e botões', () => {
    const v = filled();
    v.hoursInformed = true;
    v.weekly.FRIDAY = [
      { opens: '11:30', closes: '15:00' },
      { opens: '18:00', closes: '02:00' },
    ];
    v.weekly.MONDAY = [{ opens: '', closes: '' }];
    v.special = [{ date: '2026-12-25', closed: true, ranges: [{ opens: '10:00', closes: '12:00' }], note: ' Natal ' }];
    v.serviceFee = '10';
    v.couvertKind = 'ARTISTICO';
    v.couvertValue = '15';
    v.couvertDays = ['FRIDAY'];
    v.adjustments = [{ method: 'PIX', kind: 'DESCONTO', percent: '5' }];
    v.facets = { WIFI: 'SIM', PIX: '' };
    v.links = { IFOOD: ' https://www.ifood.com.br/x ', RAPPI: '', THEFORK: 'https://www.thefork.com.br/y' };
    v.preferredDelivery = 'IFOOD';
    v.whatsapp = '(31) 99999-8888';
    const { draft, errors } = buildPlaceDraft(v, false, PROVIDERS);
    expect(errors).toEqual({});
    expect(draft.name).toBe('Sabor Mineiro');
    expect(draft.location).toMatchObject({ latitude: -19.937, longitude: -43.933, addressVisibility: 'ADDR_PUBLIC_FULL' });
    expect(draft.price).toEqual({ minCents: 4500, maxCents: 6550, notApplicable: false });
    expect(draft.hours).toEqual({
      weekly: { FRIDAY: [{ opens: '11:30', closes: '15:00' }, { opens: '18:00', closes: '02:00' }] },
      special: [{ date: '2026-12-25', closed: true, ranges: [], note: 'Natal' }],
    });
    expect(draft.charges).toEqual({
      serviceFeePercent: 10,
      couvert: { kind: 'ARTISTICO', cents: 1500, days: ['FRIDAY'] },
      paymentAdjustments: [{ method: 'PIX', kind: 'DESCONTO', percent: 5 }],
    });
    expect(draft.facets).toEqual({ WIFI: 'SIM' });
    expect(draft.actions).toEqual({
      links: [
        { provider: 'IFOOD', url: 'https://www.ifood.com.br/x', preferred: true },
        { provider: 'THEFORK', url: 'https://www.thefork.com.br/y', preferred: false },
      ],
      whatsapp: '(31) 99999-8888',
    });
    expect(draft.status).toBeUndefined();
    expect(buildPlaceDraft(v, true, PROVIDERS).draft.status).toBe('ACTIVE');
  });

  it('sem horário da semana manda weekly nulo ("Horário não informado")', () => {
    const { draft } = buildPlaceDraft(filled(), false, PROVIDERS);
    expect(draft.hours).toEqual({ weekly: null, special: [] });
    expect(draft.temporaryClosure).toEqual({ closed: false, reopensOn: null });
  });

  it('confere o que dá para conferir no navegador', () => {
    const v = { ...emptyPlaceValues(), latitude: '-19.9', hoursInformed: true, serviceFee: '30', couvertKind: 'ARTISTICO' as const };
    v.weekly = { ...v.weekly, TUESDAY: [{ opens: '25:00', closes: '15:00' }] };
    v.links = { IFOOD: 'http://www.ifood.com.br/x' };
    const { errors } = buildPlaceDraft(v, false, PROVIDERS);
    expect(Object.keys(errors).sort()).toEqual(
      ['city', 'couvert', 'hours-TUESDAY', 'link-IFOOD', 'municipalityIbge', 'name', 'pin', 'primaryType', 'serviceFee', 'uf'].sort(),
    );
    expect(errors.couvert).toContain('Couvert artístico só nos dias com apresentação ao vivo: escolha os dias.');
  });
});

describe('mapPlaceErrors', () => {
  it('leva cada frase do 422 ao campo', () => {
    const { fields, general } = mapPlaceErrors([
      'Em segunda, as faixas 11:00–15:00 e 14:00–18:00 se sobrepõem.',
      'A faixa de sábado que passa da meia-noite (22:00–06:00) invade o horário de domingo (05:00).',
      'Consumação mínima não é aceita: os Procons a consideram cobrança abusiva (CDC art. 39). Cobre só o que for consumido.',
      'Couvert artístico exige apresentação ao vivo, mas "Música ao vivo" está como Não.',
      'Link do iFood: endereço HTTPS de ifood.com.br (ex.: https://www.ifood.com.br/...).',
      'WhatsApp: informe o número com DDD, ex.: (31) 99999-8888.',
      '"Cão-guia ou de assistência sempre permitido" é garantido por lei e não se edita.',
      'A data de volta precisa ser depois de hoje.',
      'O código IBGE 3550308 não é de um município de RJ.',
      'Algo novo que a tela não conhece.',
    ]);
    expect(Object.keys(fields).sort()).toEqual(
      ['couvert', 'facets', 'hours-MONDAY', 'hours-SATURDAY', 'links', 'municipalityIbge', 'reopensOn', 'serviceFee', 'whatsapp'].sort(),
    );
    expect(general).toEqual(['Algo novo que a tela não conhece.']);
  });
});

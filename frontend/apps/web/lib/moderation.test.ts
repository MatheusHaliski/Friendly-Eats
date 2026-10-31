import { describe, expect, it } from 'vitest';
import { parseTargetLink, publicReportHref, reasonsFor } from './moderation';
import type { ReasonView } from '@friendly-eats/api';

describe('parseTargetLink', () => {
  it('lê post, link curto, perfil e lugar, com ou sem domínio', () => {
    const id = '0199aaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee';
    expect(parseTargetLink(`https://friendlyeats.app/p/${id}`)).toEqual({ type: 'POST', id });
    expect(parseTargetLink(`/posts/${id.toUpperCase()}#comentarios`)).toEqual({ type: 'POST', id });
    expect(parseTargetLink('https://friendlyeats.app/u/310')).toEqual({ type: 'PROFILE', id: '310' });
    expect(parseTargetLink(' /lugares/12?aba=posts ')).toEqual({ type: 'ESTABLISHMENT', id: '12' });
  });

  it('recusa o que não é do app', () => {
    expect(parseTargetLink('https://exemplo.com/qualquer')).toBeNull();
    expect(parseTargetLink('/u/abc')).toBeNull();
  });
});

describe('motivos', () => {
  const reasons = [
    { code: 'SPAM', targets: ['POST', 'COMMENT'], ownerContest: false },
    { code: 'AVALIACAO_FALSA', targets: ['REVIEW'], ownerContest: true },
  ] as ReasonView[];

  it('filtra pelo tipo do alvo e, na contestação, só os da política', () => {
    expect(reasonsFor(reasons, 'COMMENT').map((r) => r.code)).toEqual(['SPAM']);
    expect(reasonsFor(reasons, 'POST', true).map((r) => r.code)).toEqual(['AVALIACAO_FALSA']);
    expect(reasonsFor(undefined, 'POST')).toEqual([]);
  });

  it('monta o link do formulário sem conta', () => {
    expect(publicReportHref('COMMENT', 42)).toBe('/denunciar?tipo=COMMENT&id=42');
  });
});

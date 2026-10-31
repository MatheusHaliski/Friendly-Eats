import type { AgeVerificationView, DataExportView, SessionUserView } from '@friendly-eats/api';
import { describe, expect, it } from 'vitest';
import { alcoholNotice, formatBytes, nextExportAllowedAt, privacyRequestMailto } from './titular';

const session = { id: 1 } as SessionUserView;
const age = (p: Partial<AgeVerificationView>) => ({ alcoholContentVisible: false, declaredAdult: true, ...p }) as AgeVerificationView;

describe('alcoholNotice', () => {
  it('pede login ao visitante e verificação ao adulto sem idade verificada', () => {
    expect(alcoholNotice(null, undefined)).toBe('login');
    expect(alcoholNotice(session, age({}))).toBe('verify');
  });
  it('não mostra nada a quem já vê nem a quem declarou menos de 18', () => {
    expect(alcoholNotice(session, age({ alcoholContentVisible: true }))).toBe('none');
    expect(alcoholNotice(session, age({ declaredAdult: false }))).toBe('none');
    expect(alcoholNotice(session, undefined)).toBe('none');
  });
});

describe('privacyRequestMailto', () => {
  it('monta assunto e corpo codificados', () => {
    const url = privacyRequestMailto('dpo@example.com', { name: 'Ana', email: 'ana@example.com', right: 'Acesso', message: 'Quero uma cópia & o histórico' });
    expect(url.startsWith('mailto:dpo%40example.com?subject=')).toBe(true);
    expect(decodeURIComponent(url)).toContain('Pedido do titular (LGPD): Acesso');
    expect(decodeURIComponent(url)).toContain('Quero uma cópia & o histórico');
  });
});

describe('formatBytes e nextExportAllowedAt', () => {
  it('formata tamanhos em pt-BR', () => {
    expect(formatBytes(512)).toBe('512 bytes');
    expect(formatBytes(1536)).toBe('1,5 KB');
    expect(formatBytes(null)).toBe('');
  });
  it('libera um pedido por dia, sem contar os que falharam', () => {
    const now = new Date('2026-10-31T12:00:00Z');
    const e = (status: DataExportView['status'], requestedAt: string) => ({ status, requestedAt }) as DataExportView;
    expect(nextExportAllowedAt([], now)).toBeNull();
    expect(nextExportAllowedAt([e('READY', '2026-10-31T10:00:00Z')], now)?.toISOString()).toBe('2026-11-01T10:00:00.000Z');
    expect(nextExportAllowedAt([e('FAILED', '2026-10-31T10:00:00Z')], now)).toBeNull();
    expect(nextExportAllowedAt([e('READY', '2026-10-29T10:00:00Z')], now)).toBeNull();
  });
});

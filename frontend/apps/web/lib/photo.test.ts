import { describe, expect, it, vi } from 'vitest';
import { checkPhoto, initials, uploadProfilePhoto } from './photo';

describe('checkPhoto', () => {
  it('aceita JPEG e PNG até 10 MB', () => {
    expect(checkPhoto({ type: 'image/jpeg', size: 1000 })).toBeNull();
    expect(checkPhoto({ type: 'image/png', size: 10 * 1024 * 1024 })).toBeNull();
    expect(checkPhoto({ type: 'image/gif', size: 1000 })).toBe('Envie a foto em JPEG ou PNG.');
    expect(checkPhoto({ type: 'image/png', size: 10 * 1024 * 1024 + 1 })).toBe('A foto vai até 10 MB.');
  });
});

describe('uploadProfilePhoto', () => {
  it('pede a URL, envia ao bucket com o ticket e grava a chave', async () => {
    const order: string[] = [];
    const ticket = { objectKey: 'uploads/42/a.jpg', uploadUrl: 'https://b/x', method: 'PUT', headers: { 'Content-Type': 'image/jpeg' }, expiresAt: '', maxBytes: 1 };
    const api = {
      media: { requestUpload: vi.fn(async () => (order.push('ticket'), ticket)) },
      me: { setPhoto: vi.fn(async (key: string) => (order.push(`photo:${key}`), { avatarUrl: '/m/avatars/1.jpg' })) },
    };
    const upload = vi.fn(async () => {
      order.push('put');
    });
    const file = new File(['abc'], 'eu.jpg', { type: 'image/jpeg' });
    const view = await uploadProfilePhoto(file, { api: api as never, upload });
    expect(order).toEqual(['ticket', 'put', 'photo:uploads/42/a.jpg']);
    expect(api.media.requestUpload).toHaveBeenCalledWith({ kind: 'IMAGE', contentType: 'image/jpeg', sizeBytes: 3 });
    expect(upload).toHaveBeenCalledWith(ticket, file);
    expect(view).toEqual({ avatarUrl: '/m/avatars/1.jpg' });
  });
});

describe('initials', () => {
  it('primeira e última palavra', () => {
    expect(initials('Júlia Andrade')).toBe('JA');
    expect(initials('  bistrô ')).toBe('B');
    expect(initials('')).toBe('?');
  });
});

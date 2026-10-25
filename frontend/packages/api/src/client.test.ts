import { describe, expect, it, vi } from 'vitest';

type FetchArgs = [url: string, init: RequestInit & { headers: Record<string, string> }];
const mockFetch = (impl: (...args: FetchArgs) => Promise<Response>) => vi.fn<(...args: FetchArgs) => Promise<Response>>(impl);
const asFetch = (f: unknown) => f as typeof fetch;
import { ApiError, createClient, readCookie, toQuery, uploadToBucket } from './client.ts';
import { createApi } from './endpoints.ts';

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

describe('toQuery', () => {
  it('repete o nome nas listas e pula vazios', () => {
    expect(toQuery({ q: 'pão', cuisine: ['BRASILEIRA', 'MINEIRA'], price: [], minRating: 4, lat: undefined, sort: '' })).toBe(
      '?q=p%C3%A3o&cuisine=BRASILEIRA&cuisine=MINEIRA&minRating=4',
    );
    expect(toQuery({})).toBe('');
  });
});

describe('readCookie', () => {
  it('lê o valor decodificado', () => {
    expect(readCookie('XSRF-TOKEN', 'fe_session=abc; XSRF-TOKEN=a%2Fb=c')).toBe('a/b=c');
    expect(readCookie('XSRF-TOKEN', 'fe_session=abc')).toBeNull();
  });
});

describe('createClient', () => {
  it('GET não pede CSRF e usa a mesma origem', async () => {
    const fetch = vi.fn().mockResolvedValue(json(200, { ok: true }));
    const client = createClient({ fetch, cookies: () => '' });
    await expect(client.get('/api/v1/taxonomy')).resolves.toEqual({ ok: true });
    expect(fetch).toHaveBeenCalledTimes(1);
    const [url, init] = fetch.mock.calls[0]!;
    expect(url).toBe('/api/v1/taxonomy');
    expect(init.credentials).toBe('same-origin');
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('antes da primeira escrita busca /auth/csrf e manda o cookie no cabeçalho', async () => {
    let cookie = '';
    const fetch = mockFetch(async (url) => {
      if (url === '/api/v1/auth/csrf') {
        cookie = 'XSRF-TOKEN=tok-1';
        return json(200, { headerName: 'X-XSRF-TOKEN', token: 'mascarado' });
      }
      return json(200, { status: 'AUTHENTICATED' });
    });
    const api = createApi(createClient({ fetch: asFetch(fetch), cookies: () => cookie }));
    await api.auth.login({ email: 'a@b.com', password: 'x' });
    expect(fetch.mock.calls.map((c) => c[0])).toEqual(['/api/v1/auth/csrf', '/api/v1/auth/login']);
    const init = fetch.mock.calls[1]![1];
    expect(init.headers['X-XSRF-TOKEN']).toBe('tok-1');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(String(init.body))).toEqual({ email: 'a@b.com', password: 'x' });
  });

  it('com o cookie presente não busca o CSRF de novo', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    const client = createClient({ fetch, cookies: () => 'XSRF-TOKEN=ja-tenho' });
    await client.del('/api/v1/establishments/1/reviews/me');
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch.mock.calls[0]![1].headers['X-XSRF-TOKEN']).toBe('ja-tenho');
  });

  it('token CSRF velho: busca outro e repete uma vez', async () => {
    let cookie = 'XSRF-TOKEN=velho';
    const fetch = mockFetch(async (url) => {
      if (url === '/api/v1/auth/csrf') {
        cookie = 'XSRF-TOKEN=novo';
        return json(200, { token: 'x' });
      }
      if (fetch.mock.calls.filter((c) => c[0] === url).length === 1) {
        return json(403, { status: 403, title: 'Sessão desatualizada', detail: 'Token CSRF ausente ou inválido.' });
      }
      return json(200, { id: 1 });
    });
    const client = createClient({ fetch: asFetch(fetch), cookies: () => cookie });
    await expect(client.post('/api/v1/auth/logout')).resolves.toEqual({ id: 1 });
    expect(fetch.mock.calls.at(-1)![1].headers['X-XSRF-TOKEN']).toBe('novo');
  });

  it('422 vira ApiError com errors[] e 429 traz o Retry-After', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(json(422, { status: 422, title: 'Revise os dados', detail: 'Revise os campos destacados.', errors: ['a', 'b'] }))
      .mockResolvedValueOnce(json(429, { status: 429, detail: 'Muitas tentativas.' }, { 'Retry-After': '42' }));
    const client = createClient({ fetch, cookies: () => 'XSRF-TOKEN=t' });
    const e422 = await client.post('/x').catch((e: unknown) => e);
    expect(e422).toBeInstanceOf(ApiError);
    expect((e422 as ApiError).errors).toEqual(['a', 'b']);
    expect((e422 as ApiError).detail).toBe('Revise os campos destacados.');
    const e429 = (await client.post('/x').catch((e: unknown) => e)) as ApiError;
    expect(e429.status).toBe(429);
    expect(e429.retryAfterSeconds).toBe(42);
  });

  it('erro sem corpo JSON recebe mensagem em pt-BR', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('<html>', { status: 502 }));
    const err = (await createClient({ fetch }).get('/x').catch((e: unknown) => e)) as ApiError;
    expect(err.status).toBe(502);
    expect(err.detail).toMatch(/Algo deu errado/);
  });

  it('PUT da avaliação distingue 201 (criou) de 200 (editou)', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(json(201, { id: 9 })).mockResolvedValueOnce(json(200, { id: 9 }));
    const api = createApi(createClient({ fetch, cookies: () => 'XSRF-TOKEN=t' }));
    const draft = { rating: 4, visitMode: 'DINE_IN' as const, dimensions: {}, spentPerPersonCents: null, body: null };
    await expect(api.establishments.saveMyReview(1, draft)).resolves.toMatchObject({ created: true });
    await expect(api.establishments.saveMyReview(1, draft)).resolves.toMatchObject({ created: false });
  });

  it('202 com corpo devolve o corpo (pedido de exclusão) e 202 vazio devolve undefined', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(json(202, { accountStatus: 'PENDING_DELETION', deletionScheduledFor: '2026-11-24T12:00:00Z', graceDays: 30 }))
      .mockResolvedValueOnce(new Response(null, { status: 202 }));
    const api = createApi(createClient({ fetch, cookies: () => 'XSRF-TOKEN=t' }));
    await expect(api.me.requestDeletion({ password: 'x', code: null, confirm: true })).resolves.toMatchObject({ graceDays: 30 });
    await expect(api.me.resendVerification()).resolves.toBeUndefined();
  });

  it('PATCH e DELETE com corpo mandam o CSRF e leem a resposta', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(json(200, { fontScale: 120 })).mockResolvedValueOnce(json(200, { closed: 2 }));
    const api = createApi(createClient({ fetch, cookies: () => 'XSRF-TOKEN=t' }));
    await expect(api.me.updatePreferences({ fontScale: 120 })).resolves.toEqual({ fontScale: 120 });
    expect(fetch.mock.calls[0]![1].method).toBe('PATCH');
    expect(fetch.mock.calls[0]![1].headers['X-XSRF-TOKEN']).toBe('t');
    await expect(api.me.endOtherSessions()).resolves.toEqual({ closed: 2 });
    expect(fetch.mock.calls[1]![0]).toBe('/api/v1/me/sessions');
    expect(fetch.mock.calls[1]![1].method).toBe('DELETE');
  });
});

describe('uploadToBucket', () => {
  const ticket = { uploadUrl: 'https://bucket.exemplo/uploads/42/a.jpg?X-Amz-Signature=abc', method: 'PUT', headers: { 'Content-Type': 'image/jpeg' } };

  it('PUT direto, sem cookie nem CSRF, só com os cabeçalhos assinados', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
    const file = new Blob(['x'], { type: 'image/jpeg' });
    await uploadToBucket(ticket, file, fetch);
    const [url, init] = fetch.mock.calls[0]!;
    expect(url).toBe(ticket.uploadUrl);
    expect(init).toMatchObject({ method: 'PUT', credentials: 'omit', headers: { 'Content-Type': 'image/jpeg' }, body: file });
  });

  it('403 do bucket vira mensagem em pt-BR', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('<Error/>', { status: 403 }));
    const err = (await uploadToBucket(ticket, new Blob(['x']), fetch).catch((e: unknown) => e)) as ApiError;
    expect(err).toBeInstanceOf(ApiError);
    expect(err.detail).toMatch(/Escolha a foto de novo/);
  });
});

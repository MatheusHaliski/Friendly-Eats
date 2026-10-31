// Capturas das telas principais nos dois temas. Só roda com SCREENSHOTS_DIR definido:
//   SCREENSHOTS_DIR=/tmp/telas npm run e2e -- screenshots
import path from 'node:path';
import { expect, test } from './support/test';

const dir = process.env.SCREENSHOTS_DIR;

test.describe('capturas de tela', () => {
  test.skip(!dir, 'defina SCREENSHOTS_DIR para gerar as capturas');

  const telas: Array<{ nome: string; path: string; logado?: boolean; pronto: (page: import('@playwright/test').Page) => Promise<void> }> = [
    { nome: 'explorar', path: '/', pronto: (p) => expect(p.getByTestId('lista-lugares')).toBeVisible() },
    { nome: 'lugar-rated', path: '/lugares/1', logado: true, pronto: (p) => expect(p.getByTestId('lista-avaliacoes')).toBeVisible() },
    { nome: 'cadastro', path: '/cadastro', pronto: (p) => expect(p.getByText('Versão 2026-10-31.1.')).toBeVisible() },
    { nome: 'entrar', path: '/entrar', pronto: (p) => expect(p.getByRole('heading', { name: 'Entrar' })).toBeVisible() },
  ];

  for (const tela of telas) {
    for (const tema of ['claro', 'escuro'] as const) {
      test(`${tela.nome} (${tema})`, async ({ page, api }) => {
        if (tela.logado) api.login();
        await page.setViewportSize({ width: 1280, height: 900 });
        await page.emulateMedia({ colorScheme: tema === 'claro' ? 'light' : 'dark', reducedMotion: 'reduce' });
        await page.goto(tela.path);
        await tela.pronto(page);
        if (tela.nome === 'cadastro') await page.getByRole('radio', { name: /^Estabelecimento/ }).check();
        await page.screenshot({ path: path.join(dir!, `${tela.nome}-${tema}.png`), fullPage: true });
      });
    }
  }

  const secoes = ['perfil', 'exibicao', 'privacidade', 'seguranca', 'conta'] as const;
  for (const secao of secoes) {
    for (const tema of ['claro', 'escuro'] as const) {
      test(`config-${secao} (${tema})`, async ({ page, api }) => {
        api.login();
        if (secao === 'perfil') api.account.consents = api.account.consents.map((c) => ({ ...c, granted: c.purpose === 'SENSITIVE_PROFILE_DATA' }));
        await page.setViewportSize({ width: 1280, height: 900 });
        await page.emulateMedia({ colorScheme: tema === 'claro' ? 'light' : 'dark', reducedMotion: 'reduce' });
        await page.goto(`/configuracoes/${secao}`);
        await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
        await page.waitForLoadState('networkidle');
        await page.screenshot({ path: path.join(dir!, `config-${secao}-${tema}.png`), fullPage: true });
      });
    }
  }

  for (const tema of ['claro', 'escuro'] as const) {
    test(`config-lista, 2FA e exclusão (${tema})`, async ({ page, api }) => {
      api.login();
      await page.emulateMedia({ colorScheme: tema === 'claro' ? 'light' : 'dark', reducedMotion: 'reduce' });
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto('/configuracoes');
      await expect(page.getByRole('heading', { level: 1, name: 'Configurações' })).toBeVisible();
      await page.screenshot({ path: path.join(dir!, `config-lista-celular-${tema}.png`), fullPage: true });

      await page.setViewportSize({ width: 1280, height: 900 });
      await page.goto('/configuracoes/seguranca');
      const painel = page.getByTestId('painel-2fa');
      await painel.getByRole('button', { name: 'Ativar' }).click();
      await painel.getByLabel('Senha atual').fill('uma frase longa e segura');
      await painel.getByRole('button', { name: 'Continuar' }).click();
      await expect(painel.getByTestId('qr-code')).toBeVisible();
      await painel.screenshot({ path: path.join(dir!, `config-2fa-qr-${tema}.png`) });
      await painel.getByLabel('Código de 6 dígitos').fill('123456');
      await painel.getByRole('button', { name: 'Confirmar e ativar' }).click();
      await expect(painel.getByTestId('codigos-recuperacao')).toBeVisible();
      await painel.screenshot({ path: path.join(dir!, `config-2fa-codigos-${tema}.png`) });

      await page.goto('/configuracoes/conta');
      await page.getByRole('button', { name: 'Excluir minha conta…' }).click();
      await expect(page.getByRole('alertdialog')).toBeVisible();
      await page.screenshot({ path: path.join(dir!, `config-excluir-conta-${tema}.png`) });
    });
  }

  test('reativar conta (escuro)', async ({ page, api }) => {
    api.on('POST', /\/auth\/login$/, {
      status: 200,
      body: { status: 'REACTIVATION_AVAILABLE', user: null, deletionScheduledFor: '2026-11-24T03:15:00Z', message: null },
    });
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/entrar');
    await page.getByLabel('E-mail').fill('julia@example.com');
    await page.getByLabel('Senha', { exact: true }).fill('uma frase longa e segura');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Reativar minha conta' })).toBeVisible();
    await page.screenshot({ path: path.join(dir!, 'config-reativar-conta-escuro.png'), fullPage: true });
  });

  test('explorar no celular (escuro)', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');
    await expect(page.getByTestId('lista-lugares')).toBeVisible();
    await page.screenshot({ path: path.join(dir!, 'explorar-celular-escuro.png'), fullPage: true });
  });
  // ---------------------------------------------------------------- posts, feed, estúdio e perfis (RF9 · RF12 · RF14 · RF15 · RF16)
  type Tela = { nome: string; path: string; logado?: 'comensal' | 'estabelecimento'; pronto: (p: import('@playwright/test').Page) => Promise<void>; depois?: (p: import('@playwright/test').Page) => Promise<void> };
  const telasPosts: Tela[] = [
    { nome: 'feed-posts', path: '/feed', logado: 'comensal', pronto: (p) => expect(p.getByTestId('lista-posts').getByRole('listitem').first()).toBeVisible() },
    { nome: 'feed-alertas', path: '/feed?aba=alertas', pronto: (p) => expect(p.getByTestId('lista-alertas')).toBeVisible() },
    { nome: 'feed-perfis', path: '/feed?aba=perfis', pronto: (p) => expect(p.getByTestId('lista-perfis')).toBeVisible() },
    {
      nome: 'feed-restaurantes-modal',
      path: '/feed?aba=restaurantes',
      pronto: (p) => expect(p.getByTestId('lista-lugares')).toBeVisible(),
      depois: async (p) => {
        await p.getByRole('link', { name: 'Sabor Mineiro' }).click();
        await expect(p.getByTestId('modal-lugar')).toContainText('Rua');
      },
    },
    { nome: 'post', path: '/posts/0199a1b2-c3d4-7e5f-8a9b-000000000001', logado: 'comensal', pronto: (p) => expect(p.getByTestId('comentario').first()).toBeVisible() },
    { nome: 'post-receita-cardapio', path: '/posts/0199a1b2-c3d4-7e5f-8a9b-000000000003', pronto: (p) => expect(p.getByTestId('receita')).toBeVisible() },
    { nome: 'post-alerta', path: '/posts/0199a1b2-c3d4-7e5f-8a9b-000000000020', logado: 'comensal', pronto: (p) => expect(p.getByTestId('faixa-alerta')).toBeVisible() },
    {
      nome: 'publicar-estudio',
      path: '/publicar',
      logado: 'estabelecimento',
      pronto: (p) => expect(p.getByRole('heading', { level: 1, name: 'Publicar' })).toBeVisible(),
      depois: async (p) => {
        await p.getByRole('radio', { name: /Prato do dia/ }).check();
        await p.getByRole('textbox', { name: /^Título/ }).fill('Feijoada completa');
        await p.getByRole('button', { name: /^Brasa/ }).click();
      },
    },
    {
      nome: 'estudio-contraste-baixo',
      path: '/publicar',
      logado: 'comensal',
      pronto: (p) => expect(p.getByRole('heading', { level: 1, name: 'Publicar' })).toBeVisible(),
      depois: async (p) => {
        await p.getByRole('radio', { name: /Receita/ }).check();
        await p.getByRole('tab', { name: 'Container' }).click();
        await p.getByRole('button', { name: /^Farinha/ }).click();
        await p.getByRole('button', { name: /^Clara/ }).click();
        await expect(p.getByTestId('contraste').getByRole('alert')).toBeVisible();
      },
    },
    { nome: 'perfil-publico', path: '/u/310', pronto: (p) => expect(p.getByTestId('posts-perfil')).toBeVisible() },
    { nome: 'perfil-privado', path: '/u/808', pronto: (p) => expect(p.getByText('Perfil privado')).toBeVisible() },
  ];
  for (const tela of telasPosts) {
    for (const tema of ['claro', 'escuro'] as const) {
      for (const tamanho of ['computador', 'celular'] as const) {
        test(`${tela.nome} (${tema}, ${tamanho})`, async ({ page, api }) => {
          if (tela.logado === 'estabelecimento') api.login('estabelecimento', { categoryVerification: 'VERIFIED' });
          else if (tela.logado) api.login();
          await page.setViewportSize(tamanho === 'computador' ? { width: 1280, height: 900 } : { width: 390, height: 844 });
          await page.emulateMedia({ colorScheme: tema === 'claro' ? 'light' : 'dark', reducedMotion: 'reduce' });
          await page.goto(tela.path);
          await tela.pronto(page);
          if (tela.depois) await tela.depois(page);
          if (!tela.nome.includes('modal')) {
            // carrega as fotos com loading="lazy" e volta ao topo (o cabeçalho é fixo)
            await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
            await page.waitForLoadState('networkidle');
            await page.evaluate(() => window.scrollTo(0, 0));
          }
          await page.waitForLoadState('networkidle');
          await page.screenshot({ path: path.join(dir!, `${tela.nome}-${tamanho}-${tema}.png`), fullPage: !tela.nome.includes('modal') });
        });
      }
    }
  }
});

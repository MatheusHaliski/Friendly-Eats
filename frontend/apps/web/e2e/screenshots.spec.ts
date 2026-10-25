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
    { nome: 'cadastro', path: '/cadastro', pronto: (p) => expect(p.getByText('Versão 2026-10-25.1.')).toBeVisible() },
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

  test('explorar no celular (escuro)', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');
    await expect(page.getByTestId('lista-lugares')).toBeVisible();
    await page.screenshot({ path: path.join(dir!, 'explorar-celular-escuro.png'), fullPage: true });
  });
});

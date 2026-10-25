import { expect, expectAccessibleInBothThemes, test } from './support/test';

test.describe('Casca do app', () => {
  test('lang, pular para o conteúdo e cabeçalhos de segurança', async ({ page }) => {
    const res = await page.goto('/termos');
    expect(res?.headers()['x-content-type-options']).toBe('nosniff');
    expect(res?.headers()['referrer-policy']).toBe('no-referrer');
    expect(res?.headers()['content-security-policy']).toContain("frame-ancestors 'none'");
    await expect(page.locator('html')).toHaveAttribute('lang', 'pt-BR');
    await page.keyboard.press('Tab');
    const skip = page.getByRole('link', { name: 'Pular para o conteúdo' });
    await expect(skip).toBeFocused();
    await expect(skip).toBeVisible();
    await page.keyboard.press('Enter');
    await expect(page.locator('#conteudo')).toBeFocused();
  });

  test('tema: alterna, persiste só a preferência e respeita o sistema', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/termos');
    const botao = page.getByRole('button', { name: 'Tema escuro' });
    await expect(botao).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(23, 18, 13)');
    await botao.click();
    await expect(botao).toHaveAttribute('aria-pressed', 'false');
    await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(255, 255, 255)');
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    const storage = await page.evaluate(() => Object.keys(localStorage));
    expect(storage).toEqual(['fe:tema']);
  });

  test('busca do cabeçalho leva ao Explorar com q', async ({ page }) => {
    await page.goto('/termos');
    await page.getByRole('searchbox', { name: 'Buscar lugares por nome' }).fill('nonna');
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/\?q=nonna$/);
    await expect(page.getByRole('searchbox', { name: 'Nome do lugar' })).toHaveValue('nonna');
  });

  test('celular: sem rolagem horizontal e alvos de pelo menos 24 px', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 780 });
    for (const path of ['/', '/lugares/1', '/cadastro', '/entrar']) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `rolagem horizontal em ${path}`).toBeLessThanOrEqual(0);
    }
    await page.goto('/lugares/1');
    const pequenos = await page.evaluate(() =>
      [...document.querySelectorAll<HTMLElement>('a, button, input, select, textarea, label[for]')]
        .filter((el) => el.offsetParent !== null && !el.classList.contains('sr-only') && !el.closest('p, li, dd'))
        .map((el) => ({ el: el.outerHTML.slice(0, 80), r: el.getBoundingClientRect() }))
        .filter(({ r }) => r.width > 0 && (r.width < 24 || r.height < 24))
        .map(({ el }) => el),
    );
    expect(pequenos).toEqual([]);
  });

  test('Termos de Uso vêm do Markdown do repositório', async ({ page }) => {
    await page.goto('/termos');
    await expect(page.getByRole('heading', { level: 1, name: 'Termos de Uso do Friendly Eats' })).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: '1. Quem somos' })).toBeVisible();
    await expect(page.getByRole('table').first()).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Termos');
  });
});

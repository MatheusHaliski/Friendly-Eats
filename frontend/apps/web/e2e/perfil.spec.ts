import { expect, expectAccessibleInBothThemes, test } from './support/test';

test.describe('Perfil público (RF15)', () => {
  test('cabeçalho, links, membro desde, posts e compartilhar', async ({ page, api, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.goto('/u/310');
    await expect(page.getByRole('heading', { level: 1, name: 'Rafael Lima' })).toBeVisible();
    await expect(page).toHaveTitle('Rafael Lima · Friendly Eats');
    await expect(page.getByRole('main').getByText('@chefrafa').first()).toBeVisible();
    await expect(page.getByText('Chef', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('ele/dele')).toBeVisible();
    await expect(page.getByText('Belo Horizonte – MG')).toBeVisible();
    await expect(page.getByText('março de 2026')).toBeVisible();
    await expect(page.getByRole('link', { name: /chefrafa\.com\.br/ })).toHaveAttribute('href', 'https://chefrafa.com.br');
    await expect(page.getByTestId('posts-perfil').getByRole('article')).toHaveCount(1);
    await page.getByRole('button', { name: 'Copiar link do perfil' }).click();
    await expect(page.getByText('Link do perfil copiado.')).toBeVisible();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('https://friendlyeats.app/u/310');
    expect(api.find('GET', /\/users\/310\/posts$/)).toHaveLength(1);
    await expectAccessibleInBothThemes(page, 'Perfil público');
  });

  test('perfil privado: só o mínimo, uma mensagem e os posts não abrem', async ({ page, api }) => {
    await page.goto('/u/808');
    await expect(page.getByRole('heading', { level: 1, name: 'Marina Souza' })).toBeVisible();
    await expect(page.getByText('Perfil privado')).toBeVisible();
    await expect(page.getByText('Doces e confeitaria.')).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'Posts' })).toHaveCount(0);
    expect(api.find('GET', /\/users\/808\/posts$/)).toHaveLength(0);
    await expectAccessibleInBothThemes(page, 'Perfil privado');
  });

  test('o titular vê o próprio perfil e o 18+ dele; inexistente dá 404', async ({ page, api }) => {
    api.login('comensal', { id: 501, name: 'Ana Come Bem', handle: 'anacomebem' });
    await page.goto('/u/501');
    await expect(page.getByTestId('posts-perfil').getByRole('article', { name: 'Chope cremoso no Boteco do Zé' })).toBeVisible();
    await page.goto('/u/999');
    await expect(page.getByRole('heading', { level: 1, name: 'Perfil não encontrado' })).toBeVisible();
  });
});

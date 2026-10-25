import { expect, expectAccessibleInBothThemes, test } from './support/test';

test.describe('Confirmar e-mail (RF1)', () => {
  test('código errado, certo e reenvio', async ({ page, api }) => {
    api.login('comensal', { accountStatus: 'PENDING_VERIFICATION', emailVerified: false });
    await page.goto('/confirmar-email');
    await expect(page.getByText('julia@example.com')).toBeVisible();
    await page.getByLabel('Código de 6 dígitos').fill('000000');
    await page.getByRole('button', { name: 'Confirmar' }).click();
    await expect(page.getByLabel('Código de 6 dígitos')).toHaveAccessibleDescription('Código incorreto. Restam 4 tentativas.');

    await page.getByRole('button', { name: 'Reenviar código' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Enviamos um novo código para julia@example.com.' })).toBeVisible();
    await expect(page.getByRole('button', { name: /Reenviar em/ })).toBeDisabled();
    expect(api.find('POST', /\/verify-email\/resend$/)).toHaveLength(1);

    await page.getByLabel('Código de 6 dígitos').fill('123456');
    await page.getByRole('button', { name: 'Confirmar' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'E-mail confirmado. A sua conta está ativa.' })).toBeVisible();
    expect(api.last('POST', /\/verify-email$/)!.body).toEqual({ code: '123456' });
  });

  test('sem sessão, pede para entrar', async ({ page }) => {
    await page.goto('/confirmar-email');
    await expect(page.getByRole('link', { name: 'Entrar' }).last()).toHaveAttribute('href', '/entrar?voltar=%2Fconfirmar-email');
  });
});

test.describe('Minha conta (RF4, RF6)', () => {
  test('dados da sessão e sair', async ({ page, api }) => {
    api.login();
    await page.goto('/conta');
    await expect(page.getByRole('heading', { level: 1, name: 'Minha conta' })).toBeVisible();
    await expect(page.getByRole('main').getByText('@juliandrade')).toBeVisible();
    await expect(page.getByRole('main').getByText('Ativa', { exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Meus lugares' })).toHaveCount(0);
    await page.getByRole('button', { name: 'Sair' }).click();
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();
    expect(api.find('POST', /\/auth\/logout$/)).toHaveLength(1);
  });

  test('Estabelecimento vê Meus lugares com published e publishBlockers', async ({ page, api }) => {
    api.login('estabelecimento');
    await page.goto('/conta');
    await expect(page.getByText('Verificação em andamento')).toBeVisible();
    const lugares = page.getByRole('region', { name: 'Meus lugares' }).getByRole('listitem').filter({ has: page.getByRole('heading') });
    await expect(lugares).toHaveCount(2);
    await expect(lugares.first()).toContainText('No ar');
    await expect(lugares.last()).toContainText('Fora do ar');
    await expect(lugares.last()).toContainText('Aguardando a conferência do selo da conta (CNPJ ou CPF e documentos).');
    await expectAccessibleInBothThemes(page, 'Minha conta');
  });

  test('sem sessão vai para o login com voltar', async ({ page }) => {
    await page.goto('/conta');
    await expect(page).toHaveURL(/\/entrar\?voltar=%2Fconta$/);
  });
});

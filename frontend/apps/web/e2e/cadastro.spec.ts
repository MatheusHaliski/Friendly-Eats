import { problem } from './support/mock-api';
import { expect, expectAccessibleInBothThemes, test } from './support/test';

async function preencherBasico(page: import('@playwright/test').Page) {
  await page.getByLabel('Nome de exibição (obrigatório)').fill('Maria do Acarajé');
  await page.getByLabel('@usuário (obrigatório)').fill('@MariaAcaraje');
  await page.getByLabel('E-mail (obrigatório)', { exact: true }).fill('maria@example.com');
  await page.getByLabel('Senha (obrigatório)').fill('acarajé quentinho todo dia');
  await page.getByLabel('Data de nascimento (obrigatório)').fill('1985-04-12');
}

test.describe('Cadastro (RF1)', () => {
  test('7 tipos com cor, glifo e rótulo; Termos desmarcados com a versão da API', async ({ page }) => {
    await page.goto('/cadastro');
    const tipos = page.getByRole('group', { name: /Tipo de conta/ }).getByRole('radio');
    await expect(tipos).toHaveCount(7);
    for (const [nome, glifo] of [
      ['Comensal', '◇'],
      ['Criador', '✦'],
      ['Chef', '⬠'],
      ['Estabelecimento', '▭'],
      ['Varejo & Marca', '△'],
      ['Comunidade', '⬢'],
      ['Oficial', '◎'],
    ]) {
      const label = page.locator('label', { hasText: nome! }).first();
      await expect(label).toContainText(glifo!);
    }
    await expect(page.getByRole('checkbox', { name: 'Li e aceito os Termos de Uso do Friendly Eats' })).not.toBeChecked();
    await expect(page.getByText('Versão 2026-10-25.1.')).toBeVisible();
    await expect(page.getByRole('link', { name: /Ler os Termos de Uso/ })).toHaveAttribute('href', '/termos');
  });

  test('estabelecimento sem CNPJ troca para informalFormat + CPF e vai para /confirmar-email', async ({ page, api }) => {
    await page.goto('/cadastro');
    await preencherBasico(page);
    await page.getByRole('radio', { name: /^Estabelecimento/ }).check();
    await expect(page.getByLabel('CNPJ (obrigatório)')).toBeVisible();
    await expect(page.getByLabel('CNAE principal (obrigatório)')).toBeVisible();
    await page.getByRole('checkbox', { name: 'Não tenho CNPJ (ambulante, food truck, cozinha caseira)' }).check();
    await expect(page.getByLabel('CNPJ (obrigatório)')).toHaveCount(0);
    await expect(page.getByLabel('CNAE principal (obrigatório)')).toHaveCount(0);
    await page.getByLabel('Formato do negócio (obrigatório)').selectOption('AMBULANTE');
    await page.getByLabel(/CPF do titular/).fill('529.982.247-25');
    await page.getByLabel('Nome do negócio (obrigatório)').fill('Acarajé da Dinha');
    await page.getByLabel('Cidade (obrigatório)').fill('Salvador');
    await page.getByLabel('UF (obrigatório)').selectOption('BA');
    await page.getByRole('checkbox', { name: 'Li e aceito os Termos de Uso do Friendly Eats' }).check();
    await page.getByRole('button', { name: 'Criar conta' }).click();
    await expect(page).toHaveURL(/\/confirmar-email$/);

    const body = api.last('POST', /\/auth\/register$/)!.body as Record<string, unknown>;
    expect(body).toMatchObject({
      name: 'Maria do Acarajé',
      handle: 'mariaacaraje',
      email: 'maria@example.com',
      userType: 'ESTABELECIMENTO',
      dateOfBirth: '1985-04-12',
      acceptTerms: true,
      termsVersion: '2026-10-25.1',
    });
    expect(body.typeDetails).toEqual({
      informalFormat: 'AMBULANTE',
      cpf: '529.982.247-25',
      tradeName: 'Acarajé da Dinha',
      city: 'Salvador',
      state: 'BA',
    });
    await expect(page.getByText('maria@example.com')).toBeVisible();
  });

  test('validação local e 422 por campo', async ({ page, api }) => {
    await page.goto('/cadastro');
    await page.getByRole('button', { name: 'Criar conta' }).click();
    const resumo = page.getByTestId('error-summary');
    await expect(resumo).toBeFocused();
    await expect(resumo).toContainText('A senha precisa ter pelo menos 12 caracteres.');
    await expect(resumo).toContainText('Escolha o tipo de conta.');
    await expect(resumo).toContainText('Para criar a conta, leia e aceite os Termos de Uso.');
    expect(api.find('POST', /\/auth\/register$/)).toHaveLength(0);

    api.on('POST', /\/auth\/register$/, problem(422, 'Revise os campos destacados.', {
      errors: ['Essa senha é fácil de adivinhar. Escolha outra.', 'CNAE no formato da Receita, ex.: 5611-2/01.', 'Preencha o campo "representativeEmail" da categoria Estabelecimento.'],
    }));
    await preencherBasico(page);
    await page.getByRole('radio', { name: /^Estabelecimento/ }).check();
    await page.getByLabel('CNPJ (obrigatório)').fill('11.222.333/0001-81');
    await page.getByLabel('Razão social (obrigatório)').fill('Acarajé Ltda');
    await page.getByLabel('Nome fantasia (obrigatório)').fill('Acarajé');
    await page.getByLabel('CNAE principal (obrigatório)').fill('5611201');
    await page.getByLabel('Cidade (obrigatório)').fill('Salvador');
    await page.getByLabel('UF (obrigatório)').selectOption('BA');
    await page.getByLabel(/^Nome do representante/).fill('Maria');
    await page.getByLabel(/^Cargo do representante/).fill('Sócia');
    await page.getByLabel(/^E-mail do representante/).fill('maria@example.com');
    await page.getByRole('checkbox', { name: 'Li e aceito os Termos de Uso do Friendly Eats' }).check();
    await page.getByRole('button', { name: 'Criar conta' }).click();
    await expect(page.getByLabel('Senha (obrigatório)')).toHaveAccessibleDescription(/Essa senha é fácil de adivinhar/);
    await expect(page.getByLabel('CNAE principal (obrigatório)')).toHaveAccessibleDescription(/CNAE no formato da Receita/);
    await expect(page.getByLabel(/^E-mail do representante/)).toHaveAccessibleDescription(/Preencha o campo “E-mail do representante” da categoria Estabelecimento\./);
  });

  test('409 de @usuário em uso marca o campo', async ({ page, api }) => {
    api.on('POST', /\/auth\/register$/, problem(409, 'Esse @usuário já está em uso. Tente outro.'));
    await page.goto('/cadastro');
    await preencherBasico(page);
    await page.getByRole('radio', { name: /^Comensal/ }).check();
    await page.getByRole('checkbox', { name: 'Li e aceito os Termos de Uso do Friendly Eats' }).check();
    await page.getByRole('button', { name: 'Criar conta' }).click();
    await expect(page.getByLabel('@usuário (obrigatório)')).toHaveAccessibleDescription(/Esse @usuário já está em uso/);
  });

  test('menor de 16 informa o e-mail do responsável', async ({ page }) => {
    await page.goto('/cadastro');
    await page.getByRole('radio', { name: /^Comensal/ }).check();
    await expect(page.getByLabel(/E-mail do responsável/)).toHaveCount(0);
    const ano = new Date().getFullYear() - 14;
    await page.getByLabel('Data de nascimento (obrigatório)').fill(`${ano}-01-01`);
    await expect(page.getByLabel(/E-mail do responsável/)).toBeVisible();
  });

  test('sem violações graves de acessibilidade', async ({ page }) => {
    await page.goto('/cadastro');
    await page.getByRole('radio', { name: /^Estabelecimento/ }).check();
    await expect(page.getByLabel('CNPJ (obrigatório)')).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Cadastro');
  });
});

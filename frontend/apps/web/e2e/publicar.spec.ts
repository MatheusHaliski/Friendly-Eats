import { PRATO_FIXTURE, problem } from './support/mock-api';
import { expect, expectAccessibleInBothThemes, test } from './support/test';

test.describe('Publicar e editar (RF9) com o estúdio de moldura (RF16)', () => {
  test('foto de prato com foto (URL pré-assinada), alergênico e moldura', async ({ page, api }) => {
    api.login();
    await page.goto('/publicar');
    await expect(page.getByRole('heading', { level: 1, name: 'Publicar' })).toBeVisible();
    // comensal: só os tipos que a conta publica (vocabulário com sessão)
    const tipos = page.getByRole('radiogroup', { name: 'Tipo de post' }).getByRole('radio');
    await expect(tipos).toHaveCount(3);
    await page.getByRole('radio', { name: /Foto de prato/ }).check();
    await expect(page.getByText('Foto (obrigatória)')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Receita' })).toHaveCount(0);

    await page.getByRole('textbox', { name: /^Título/ }).fill('Moqueca capixaba');
    await page.getByRole('textbox', { name: 'Texto' }).fill('Panela de barro, urucum e muito coentro.');
    await page.getByLabel('Escolher foto').setInputFiles(PRATO_FIXTURE);
    await expect(page.getByText('Foto enviada.')).toBeVisible();
    expect(api.account.bucketPuts[0]).toMatchObject({ method: 'PUT' });
    await expect(page.getByTestId('previa-foto')).toBeVisible();
    await page.getByLabel(/Texto alternativo da foto/).fill('Moqueca na panela de barro, com rodelas de tomate e coentro.');
    await page.getByRole('textbox', { name: /^Preço/ }).fill('89,90');
    await page.getByText('Declarar alergênicos do prato').click();
    await page.getByLabel('Peixes').selectOption('CONTEM');
    await page.getByLabel('Crustáceos').selectOption('PODE_CONTER');

    // estúdio: padrão + material e a prévia ao vivo
    const estudio = page.getByTestId('estudio');
    await estudio.getByRole('tab', { name: 'Padrão e material' }).click();
    await expect(estudio.getByTestId('recomendacoes')).toContainText('Toalha xadrez');
    await estudio.getByRole('button', { name: /^Maré/ }).click();
    await expect(estudio.getByRole('button', { name: /^Maré/ })).toHaveAttribute('aria-pressed', 'true');
    await estudio.getByRole('button', { name: 'Vidro' }).click();
    const previa = page.getByTestId('previa');
    await expect(previa.getByTestId('card-post-moldura')).toHaveAttribute('data-art', 'AURA');
    await expect(previa.getByRole('heading', { name: 'Moqueca capixaba' })).toBeVisible();
    await expect(previa.getByTestId('preco-post')).toContainText('R$ 89,90');

    await page.getByRole('button', { name: 'Publicar', exact: true }).click();
    await expect(page).toHaveURL(/\/posts\/0199a1b2-c3d4-7e5f-8a9b-000000000500$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Moqueca capixaba' })).toBeVisible();
    const post = api.last('POST', /^\/api\/v1\/posts$/)!;
    expect(post.headers['x-xsrf-token']).toBe(api.csrfToken);
    expect(post.body).toEqual({
      kind: 'FOTO_PRATO',
      title: 'Moqueca capixaba',
      body: 'Panela de barro, urucum e muito coentro.',
      mediaObjectKey: 'uploads/42/01991.png',
      altText: 'Moqueca na panela de barro, com rodelas de tomate e coentro.',
      establishmentId: null,
      cuisine: null,
      foodFormat: null,
      dietaryStyles: [],
      allergens: { PEIXES: 'CONTEM', CRUSTACEOS: 'PODE_CONTER' },
      warnings: [],
      ageRating: 'LIVRE',
      advertising: false,
      city: null,
      priceCents: 8990,
      background: { v: 1, aura: { presetId: 'mare', variantId: 'ORIGINAL', format: 'IMAGEM_UNICA' }, materialId: 'vidro' },
    });
    await expect(page.getByTestId('post-moldura')).toHaveAttribute('data-art', 'AURA');
    await expect(page.locator('[data-material="vidro"]')).toBeAttached();
  });

  test('erros locais e o 422 aparecem por campo, com o resumo focado', async ({ page, api }) => {
    api.login();
    await page.goto('/publicar');
    await page.getByRole('radio', { name: /Foto de prato/ }).check();
    await page.getByRole('button', { name: 'Publicar', exact: true }).click();
    const resumo = page.getByTestId('error-summary');
    await expect(resumo).toBeFocused();
    await expect(resumo).toContainText('Dê um título ao post.');
    await expect(resumo).toContainText('Envie a foto do prato.');
    expect(api.find('POST', /^\/api\/v1\/posts$/)).toHaveLength(0);

    await page.getByRole('radio', { name: /Pergunta/ }).check();
    await page.getByRole('textbox', { name: /^Pergunta/ }).fill('Qual o melhor pastel da feira?');
    api.on('POST', /^\/api\/v1\/posts$/, problem(422, 'Revise os dados', { errors: ['"title" passa de 140 caracteres.', 'Algo que a tela não conhece.'] }));
    await page.getByRole('button', { name: 'Publicar', exact: true }).click();
    await expect(resumo).toContainText('“Título” passa de 140 caracteres.');
    await expect(resumo).toContainText('Algo que a tela não conhece.');
    await expect(page.getByRole('textbox', { name: /^Pergunta/ })).toHaveAttribute('aria-invalid', 'true');
  });

  test('prato do dia do estabelecimento: lugar dos "Meus lugares" e preço obrigatórios', async ({ page, api }) => {
    api.login('estabelecimento', { categoryVerification: 'VERIFIED' });
    await page.goto('/publicar');
    await page.getByRole('radio', { name: /Prato do dia/ }).check();
    await expect(page.getByText('Sai do feed às 23h59')).toBeVisible();
    await page.getByRole('textbox', { name: /^Título/ }).fill('Frango com quiabo');
    await page.getByRole('button', { name: 'Publicar', exact: true }).click();
    await expect(page.getByTestId('error-summary')).toContainText('Escolha o lugar.');
    await expect(page.getByTestId('error-summary')).toContainText('Informe o preço.');
    await page.getByRole('radio', { name: /Sabor Mineiro · Restaurante/ }).check();
    await page.getByRole('textbox', { name: /^Preço/ }).fill('38');
    await page.getByRole('button', { name: 'Publicar', exact: true }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Frango com quiabo' })).toBeVisible();
    expect(api.last('POST', /^\/api\/v1\/posts$/)?.body).toMatchObject({ kind: 'PRATO_DO_DIA', establishmentId: 1, priceCents: 3800 });
  });

  test('conta em verificação não publica e vê o porquê', async ({ page, api }) => {
    api.login('estabelecimento');
    await page.goto('/publicar');
    await expect(page.getByText('ainda está em verificação')).toBeVisible();
  });

  test('editar: o tipo não muda, a foto e a moldura só vão quando mudam', async ({ page, api }) => {
    api.login();
    await page.goto('/posts/0199a1b2-c3d4-7e5f-8a9b-000000000001/editar');
    await expect(page.getByRole('heading', { level: 1, name: 'Editar post' })).toBeVisible();
    await expect(page.getByTestId('tipo-post').first()).toHaveText(/Foto de prato/);
    await expect(page.getByRole('textbox', { name: /^Título/ })).toHaveValue('PF caprichado no Sabor Mineiro');
    await expect(page.getByLabel('Ovos')).toHaveValue('CONTEM');
    await page.getByRole('textbox', { name: /^Título/ }).fill('PF caprichado (com tutu!)');
    await page.getByRole('button', { name: 'Salvar alterações' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'PF caprichado (com tutu!)' })).toBeVisible();
    const patch = api.last('PATCH', /\/posts\//)!.body as Record<string, unknown>;
    expect(patch.title).toBe('PF caprichado (com tutu!)');
    expect(patch).not.toHaveProperty('kind');
    expect(patch).not.toHaveProperty('mediaObjectKey');
    expect(patch).not.toHaveProperty('background');
    expect(patch.allergens).toEqual({ LEITE: 'PODE_CONTER', OVOS: 'CONTEM' });

    await page.goto('/posts/0199a1b2-c3d4-7e5f-8a9b-000000000001/editar');
    await page.getByTestId('estudio').getByRole('tab', { name: 'Cor' }).click();
    await page.getByTestId('estudio').getByRole('button', { name: 'Sem arte' }).click();
    await page.getByTestId('estudio').getByRole('tab', { name: 'Padrão e material' }).click();
    await page.getByTestId('estudio').getByRole('button', { name: 'Nenhum' }).click();
    await page.getByRole('button', { name: 'Salvar alterações' }).click();
    await expect(page.getByTestId('post-moldura')).toHaveCount(0);
    expect((api.last('PATCH', /\/posts\//)!.body as Record<string, unknown>).background).toBeNull();
  });

  test('sem violações graves no formulário com o estúdio', async ({ page, api }) => {
    api.login();
    await page.goto('/publicar');
    await page.getByRole('radio', { name: /Receita/ }).check();
    await expect(page.getByTestId('estudio')).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Publicar');
  });
});

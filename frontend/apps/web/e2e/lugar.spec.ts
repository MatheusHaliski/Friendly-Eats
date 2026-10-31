import { problem } from './support/mock-api';
import { expect, expectAccessibleInBothThemes, test } from './support/test';

test.describe('Página do lugar (RF6, RF7, RF8)', () => {
  test('nota RATED: número, estrelas decorativas, histograma 5→1 e médias por dimensão', async ({ page }) => {
    await page.goto('/lugares/1');
    await expect(page.getByRole('heading', { level: 1, name: 'Sabor Mineiro' })).toBeVisible();
    await expect(page).toHaveTitle('Sabor Mineiro · Friendly Eats');
    const nota = page.getByRole('region', { name: 'Nota' });
    await expect(nota.getByRole('img', { name: 'Nota 4,3 de 5, com base em 6 avaliações' })).toContainText('4,3');
    await expect(nota.getByTestId('stars').first()).toHaveAttribute('aria-hidden', 'true');
    const histograma = nota.getByRole('list', { name: 'Distribuição das notas' }).getByRole('listitem');
    await expect(histograma).toHaveCount(5);
    await expect(histograma.first()).toContainText('5 estrelas: 3 avaliações (50%)');
    await expect(histograma.last()).toContainText('1 estrela: 0 avaliações (0%)');
    await expect(nota.getByText('Comida')).toBeVisible();
    await expect(nota.getByText('4,6')).toBeVisible();

    await expect(page.getByText('Restaurante (à la carte)')).toBeVisible();
    await expect(page.getByText('Restaurantes', { exact: true })).toBeVisible();
    await expect(page.getByTestId('preco')).toContainText('Moderado · R$ 40 a R$ 80 por pessoa · informado pelo estabelecimento');
    await expect(page.getByRole('img', { name: 'Faixa de preço: moderado, R$ 40 a R$ 80 por pessoa, informado pelo estabelecimento' })).toBeVisible();
    await expect(page.getByText('Opções sem glúten')).toBeVisible();
    await expect(page.getByText('Informação declarada pelo estabelecimento; confirme sobre contaminação cruzada')).toBeVisible();
    await expect(page.getByText('Comida caseira')).toBeVisible();
    const responsavel = page.getByRole('region', { name: 'Responsável' });
    await expect(responsavel).toContainText('Sabor Mineiro Ltda');
    await expect(responsavel.getByText('Conta verificada')).toBeAttached();
    await expect(page.getByTestId('lista-avaliacoes').getByRole('article')).toHaveCount(6);
    await expect(page.getByRole('article', { name: 'Avaliação de Bruno Costa' })).toContainText('editada');
    await expect(page.getByRole('img', { name: 'Nota 3 de 5: Regular' })).toBeVisible();
  });

  test('RATING_FEW e RATING_NONE não mostram estrelas nem média', async ({ page }) => {
    await page.goto('/lugares/2');
    await expect(page.getByTestId('rating-state')).toHaveText('3 avaliações, ainda sem nota média');
    await expect(page.getByRole('region', { name: 'Nota' }).getByTestId('stars')).toHaveCount(0);
    await expect(page.getByTestId('preco')).toContainText('segundo 5 avaliações');

    await page.goto('/lugares/3');
    await expect(page.getByTestId('rating-state')).toHaveText('Sem avaliações ainda');
    await expect(page.getByRole('region', { name: 'Nota' }).getByTestId('stars')).toHaveCount(0);
    await expect(page.getByTestId('preco')).toHaveText('Preço não informado');
    await expect(page.getByText('Localização aproximada')).toBeVisible();
    await expect(page.getByText('Opções sem lactose')).toBeVisible();
  });

  test('visitante é convidado a entrar para avaliar', async ({ page }) => {
    await page.goto('/lugares/1');
    await expect(page.getByRole('link', { name: 'Entrar para avaliar' })).toHaveAttribute('href', '/entrar?voltar=%2Flugares%2F1');
  });

  test('cria, edita e apaga a própria avaliação, com CSRF e 201/200', async ({ page, api }) => {
    api.login();
    await page.goto('/lugares/1');
    const form = page.getByRole('form', { name: 'Avaliar este lugar' });
    await expect(form).toBeVisible();

    // nota pelo teclado: setas no grupo de rádio
    await form.getByRole('radio', { name: '1 – Péssimo' }).focus();
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('ArrowRight');
    await expect(form.getByRole('radio', { name: '4 – Bom' })).toBeChecked();
    await expect(form.getByTestId('nota-escolhida')).toHaveText('4 – Bom');

    // as dimensões dependem do modo
    await form.getByRole('radio', { name: 'Entrega' }).check();
    await expect(form.getByRole('radiogroup', { name: 'Entrega' })).toBeVisible();
    await expect(form.getByRole('radiogroup', { name: 'Ambiente' })).toHaveCount(0);
    await expect(form.getByRole('radiogroup', { name: 'Atendimento' })).toHaveCount(0);
    await form.getByRole('radio', { name: 'No local' }).check();
    await expect(form.getByRole('radiogroup', { name: 'Ambiente' })).toBeVisible();
    await expect(form.getByRole('radiogroup', { name: 'Entrega' })).toHaveCount(0);
    await form.getByRole('radiogroup', { name: 'Comida' }).getByRole('radio', { name: '5 – Excelente' }).check();

    await form.getByLabel('Quanto você gastou por pessoa?').fill('45,90');
    await form.getByLabel('Conte como foi').fill('Pão de queijo perfeito.');
    await form.getByRole('button', { name: 'Publicar avaliação' }).click();

    await expect(page.getByRole('status').filter({ hasText: 'Sua avaliação foi publicada.' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Sua avaliação' })).toBeVisible();
    const put = api.last('PUT', /\/reviews\/me$/)!;
    expect(put.headers['x-xsrf-token']).toBe(api.csrfToken);
    expect(put.body).toEqual({
      rating: 4,
      visitMode: 'DINE_IN',
      dimensions: { COMIDA: 5 },
      spentPerPersonCents: 4590,
      body: 'Pão de queijo perfeito.',
    });
    expect(api.find('GET', /\/auth\/csrf$/)).toHaveLength(1);

    await page.getByRole('button', { name: 'Editar avaliação' }).click();
    await page.getByRole('radio', { name: '5 – Excelente' }).first().check();
    await page.getByRole('button', { name: 'Salvar alterações' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Sua avaliação foi atualizada.' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Sua avaliação' })).toContainText('5 – Excelente');
    await expect(page.getByRole('region', { name: 'Sua avaliação' })).toContainText('editada');

    await page.getByRole('button', { name: 'Apagar avaliação' }).click();
    await page.getByRole('button', { name: 'Sim, apagar' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Sua avaliação foi apagada.' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Publicar avaliação' })).toBeVisible();
    expect(api.find('DELETE', /\/reviews\/me$/)).toHaveLength(1);
  });

  test('erros de validação local e 422 aparecem por campo', async ({ page, api }) => {
    api.login();
    await page.goto('/lugares/1');
    const form = page.getByRole('form', { name: 'Avaliar este lugar' });
    await form.getByRole('button', { name: 'Publicar avaliação' }).click();
    const resumo = page.getByTestId('error-summary');
    await expect(resumo).toBeFocused();
    await expect(resumo).toContainText('Dê uma nota de 1 a 5.');
    await expect(resumo).toContainText('Escolha como foi a visita.');
    expect(api.find('PUT', /\/reviews\/me$/)).toHaveLength(0);

    api.on('PUT', /\/reviews\/me$/, problem(422, 'Revise os campos destacados.', {
      errors: ['Gasto por pessoa entre R$ 0 e R$ 5.000.', 'Ambiente não se avalia em "Entrega".', '"body" passa de 2000 caracteres.'],
    }));
    await form.getByRole('radio', { name: '3 – Regular' }).first().check();
    await form.getByRole('radio', { name: 'No local' }).check();
    await form.getByLabel('Quanto você gastou por pessoa?').fill('80');
    await form.getByRole('button', { name: 'Publicar avaliação' }).click();
    await expect(resumo).toContainText('Gasto por pessoa entre R$ 0 e R$ 5.000.');
    await expect(form.getByLabel('Quanto você gastou por pessoa?')).toHaveAttribute('aria-invalid', 'true');
    await expect(form.getByLabel('Quanto você gastou por pessoa?')).toHaveAccessibleDescription(/Gasto por pessoa entre R\$ 0 e R\$ 5\.000\./);
    await expect(form.getByLabel('Conte como foi')).toHaveAccessibleDescription(/“Texto da avaliação” passa de 2000 caracteres\./);
    await expect(form.getByRole('radiogroup', { name: 'Ambiente' })).toHaveAccessibleDescription(/Ambiente não se avalia em "Entrega"\./);
  });

  test('403 e 409 mostram o detail; 401 leva ao login com voltar', async ({ page, api }) => {
    api.login('comensal', { accountStatus: 'PENDING_VERIFICATION', emailVerified: false });
    api.on('PUT', /\/reviews\/me$/, problem(403, 'Sua conta precisa estar ativa para avaliar. Confirme seu e-mail.'));
    await page.goto('/lugares/1');
    const form = page.getByRole('form', { name: 'Avaliar este lugar' });
    await form.getByRole('radio', { name: '2 – Ruim' }).first().check();
    await form.getByRole('radio', { name: 'Retirada' }).check();
    await form.getByRole('button', { name: 'Publicar avaliação' }).click();
    await expect(page.getByRole('main').getByRole('alert')).toContainText('Sua conta precisa estar ativa para avaliar. Confirme seu e-mail.');

    api.on('PUT', /\/reviews\/me$/, problem(409, 'Sua avaliação deste lugar foi removida pela moderação.'));
    await form.getByRole('button', { name: 'Publicar avaliação' }).click();
    await expect(page.getByRole('main').getByRole('alert')).toContainText('Sua avaliação deste lugar foi removida pela moderação.');

    api.on('PUT', /\/reviews\/me$/, problem(401, 'Entre na sua conta para continuar.'));
    await form.getByRole('button', { name: 'Publicar avaliação' }).click();
    await expect(page).toHaveURL(/\/entrar\?voltar=%2Flugares%2F1$/);
  });

  test('conta de organização não vê o formulário', async ({ page, api }) => {
    api.login('estabelecimento');
    await page.goto('/lugares/2');
    await expect(page.getByText('Contas de organização não avaliam lugares. Avalie com a sua conta pessoal.')).toBeVisible();
    await expect(page.getByRole('form', { name: 'Avaliar este lugar' })).toHaveCount(0);
  });

  test('lugar 18+: 401 para visitante com botão de entrar, 404 para menor', async ({ page, api }) => {
    await page.goto('/lugares/18');
    await expect(page.getByRole('heading', { level: 1, name: 'Conteúdo para maiores de 18 anos' })).toBeVisible();
    await expect(page.getByText('Entre na sua conta e verifique a idade para ver: conteúdo de bebida alcoólica é só para maiores de 18.')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Entrar' }).last()).toHaveAttribute('href', '/entrar?voltar=%2Flugares%2F18');

    api.login('comensal', { minor: true });
    await page.reload();
    await expect(page.getByRole('heading', { level: 1, name: 'Lugar não encontrado' })).toBeVisible();
  });

  test('sem violações graves de acessibilidade (visitante e com formulário)', async ({ page, api }) => {
    await page.goto('/lugares/1');
    await expect(page.getByTestId('lista-avaliacoes')).toBeVisible();
    await expectAccessibleInBothThemes(page, 'lugar RATED (visitante)');
    api.login();
    await page.reload();
    await expect(page.getByRole('form', { name: 'Avaliar este lugar' })).toBeVisible();
    await page.getByRole('button', { name: 'Publicar avaliação' }).click();
    await expect(page.getByTestId('error-summary')).toBeVisible();
    await expectAccessibleInBothThemes(page, 'lugar RATED (formulário com erros)');
  });
});

import { expect, expectAccessibleInBothThemes, test } from './support/test';

test.describe('Estúdio de moldura (RF16)', () => {
  test.beforeEach(async ({ page, api }) => {
    api.login();
    await page.goto('/publicar');
    await page.getByRole('radio', { name: /Receita/ }).check();
  });

  test('uma arte por vez: escolher outra tira a anterior e avisa', async ({ page }) => {
    const estudio = page.getByTestId('estudio');
    await estudio.getByRole('button', { name: 'Berinjela' }).click();
    await expect(estudio.getByRole('button', { name: 'Berinjela' })).toHaveAttribute('aria-pressed', 'true');
    await estudio.getByRole('button', { name: 'Açaí' }).click();
    await expect(estudio.getByRole('button', { name: 'Berinjela' })).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByTestId('aviso-estudio')).toHaveText('Uma arte por vez: a cor Berinjela saiu.');
    await expect(page.getByTestId('previa').getByTestId('card-post-moldura')).toHaveAttribute('data-art', 'GRADIENT');
  });

  test('cartela da época: rascunho no modal com Aplicar e Cancelar; automática pela data', async ({ page }) => {
    const estudio = page.getByTestId('estudio');
    await estudio.getByRole('button', { name: 'Escolher cartela…' }).click();
    let modal = page.getByRole('dialog', { name: 'Cartela da época' });
    await modal.getByRole('radio', { name: /Ceia de Natal/ }).check();
    await modal.getByRole('button', { name: 'Cancelar' }).click();
    await expect(modal).toBeHidden();
    await expect(estudio.getByTestId('cartela-atual')).toHaveText(/^Hoje: Primavera/);
    await expect(estudio.getByRole('button', { name: 'Escolher cartela…' })).toBeFocused();

    await estudio.getByRole('button', { name: 'Escolher cartela…' }).click();
    modal = page.getByRole('dialog', { name: 'Cartela da época' });
    await expect(modal.getByRole('radio', { name: /Automática pela data/ })).toBeChecked();
    await expect(modal.getByRole('radio', { name: /Black Friday/ })).toBeAttached();
    await expect(modal).toContainText('nunca entra no modo automático');
    await expectAccessibleInBothThemes(page, 'Modal da cartela');
    await modal.getByRole('button', { name: 'Aplicar' }).click();
    await expect(estudio.getByTestId('cartela-atual')).toHaveText(/Primavera.*automática pela data/);
    await expect(page.getByTestId('previa').getByTestId('card-post-moldura')).toHaveAttribute('data-art', 'SEASONAL');
  });

  test('Cardápio limpa a arte e o material, com uma frase explicando', async ({ page }) => {
    const estudio = page.getByTestId('estudio');
    await estudio.getByRole('tab', { name: 'Padrão e material' }).click();
    await estudio.getByRole('button', { name: /^Toalha xadrez/ }).click();
    await estudio.getByRole('button', { name: /^Mosaico/ }).click();
    await estudio.getByRole('button', { name: 'Linho', exact: true }).click();
    await expect(page.getByTestId('previa').locator('[data-mosaic="true"]')).toBeAttached();
    await estudio.getByRole('tab', { name: 'Layout' }).click();
    await estudio.getByRole('button', { name: 'Cardápio (lousa)' }).click();
    await expect(page.getByTestId('aviso-estudio')).toHaveText('Cardápio (lousa) já traz arte própria: o padrão Toalha xadrez e o material Linho saíram.');
    await expect(page.getByTestId('previa').getByTestId('card-post-moldura')).toHaveAttribute('data-layout', 'CARDAPIO');
    await expect(page.getByTestId('previa').locator('[data-material]')).toHaveCount(0);
    await estudio.getByRole('tab', { name: 'Padrão e material' }).click();
    await expect(estudio.getByText('já traz arte própria (a lousa)')).toBeVisible();
    await estudio.getByRole('button', { name: 'Voltar para Clássico' }).click();
    await expect(estudio.getByRole('button', { name: /^Toalha xadrez/ })).toHaveAttribute('aria-pressed', 'false');
  });

  test('contraste baixo avisa (role=alert) e sugere a tinta que passa; o servidor não recebe', async ({ page, api }) => {
    const estudio = page.getByTestId('estudio');
    await expect(estudio.getByTestId('contraste')).toContainText('13,6:1');
    await estudio.getByRole('tab', { name: 'Container' }).click();
    await estudio.getByRole('button', { name: 'Farinha' }).click();
    await expect(estudio.getByTestId('contraste')).toContainText('16,0:1');
    await estudio.getByRole('button', { name: 'Clara' }).click();
    const alerta = estudio.getByTestId('contraste').getByRole('alert');
    await expect(alerta).toContainText('Texto com contraste 1,0:1: abaixo de 4,5:1');
    await expect(alerta).toContainText('Sugestão: tinta escura (16,0:1)');

    await page.getByRole('textbox', { name: /^Título/ }).fill('Bolo de fubá');
    await page.getByLabel('Ingredientes').fill('2 xícaras de fubá');
    await page.getByLabel('Modo de preparo').fill('Misture e asse');
    await page.getByRole('button', { name: 'Publicar', exact: true }).click();
    await expect(page.getByTestId('error-summary')).toContainText('Texto com contraste 1,0:1 sobre o container');
    expect(api.find('POST', /^\/api\/v1\/posts$/)).toHaveLength(0);

    await alerta.getByRole('button', { name: 'Usar a tinta sugerida' }).click();
    await expect(estudio.getByTestId('contraste').getByRole('status')).toContainText('16,0:1');
    await page.getByRole('button', { name: 'Publicar', exact: true }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Bolo de fubá' })).toBeVisible();
    expect(api.last('POST', /^\/api\/v1\/posts$/)?.body).toMatchObject({ background: { v: 1, container: { color: '#F5EDE3' } } });
  });

  test('alerta sanitário: estúdio desligado', async ({ page, api }) => {
    api.login('estabelecimento', { category: { code: 'OFICIAL', label: 'Oficial', color: '#0C0A07' }, categoryVerification: 'VERIFIED' });
    await page.goto('/publicar');
    await page.getByRole('radio', { name: /Alerta sanitário/ }).check();
    await expect(page.getByText('Alerta sanitário não tem moldura.')).toBeVisible();
    await expect(page.getByTestId('estudio')).toHaveCount(0);
    await expect(page.getByLabel('Fonte oficial (https)')).toBeVisible();
  });

  test('sem violações graves em cada aba', async ({ page }) => {
    const estudio = page.getByTestId('estudio');
    for (const aba of ['Padrão e material', 'Layout', 'Container']) {
      await estudio.getByRole('tab', { name: aba }).click();
      await expectAccessibleInBothThemes(page, `Estúdio · ${aba}`);
    }
  });
});

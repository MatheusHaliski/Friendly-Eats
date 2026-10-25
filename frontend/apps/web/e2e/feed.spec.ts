import { expect, expectAccessibleInBothThemes, test } from './support/test';

test.describe('Feed (RF14 · RF15 · RF10)', () => {
  test('aba Posts: cards na moldura, segmento por tipo na URL e o voltar do navegador', async ({ page, api }) => {
    await page.goto('/feed');
    await expect(page.getByRole('heading', { level: 1, name: 'Feed' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Posts' })).toHaveAttribute('aria-selected', 'true');
    const lista = page.getByTestId('lista-posts');
    // visitante: o post 18+ (chope) não aparece; o encerrado e os alertas também não
    await expect(lista.getByRole('article')).toHaveCount(6);
    await expect(lista.getByRole('article', { name: 'Chope cremoso no Boteco do Zé' })).toHaveCount(0);

    const pf = lista.getByRole('article', { name: 'PF caprichado no Sabor Mineiro' });
    await expect(pf.getByTestId('tipo-post')).toHaveText(/Foto de prato/);
    await expect(pf.getByTestId('tipo-conta')).toContainText('Comensal');
    await expect(pf.getByRole('img', { name: /Prato feito visto de cima/ })).toBeVisible();
    await expect(pf.getByTestId('preco-post')).toContainText('R$ 32,90');
    await expect(pf.getByTestId('alergenicos')).toContainText('Contém: Ovos');
    await expect(pf.getByTestId('alergenicos')).toContainText('Pode conter: Leite (de todos os mamíferos)');
    // a arte fica na faixa externa (aria-hidden) e o texto no container opaco
    const moldura = page.getByTestId('card-post-moldura').first();
    await expect(moldura).toHaveAttribute('data-art', 'AURA');
    await expect(moldura.locator('.fe-frame-art')).toHaveAttribute('aria-hidden', 'true');
    await expect(lista.getByRole('article', { name: 'Rodízio de massas com 20% de desconto' }).getByTestId('chip-publicidade')).toHaveText('Publicidade');

    // segmento por tipo de post: vai para a URL e para a API
    const segmento = page.getByTestId('segmento-tipo');
    await expect(segmento.getByRole('button', { name: 'Todos' })).toHaveAttribute('aria-pressed', 'true');
    await segmento.getByRole('button', { name: 'Receita' }).click();
    await expect(page).toHaveURL(/\/feed\?tipo=RECEITA$/);
    await expect(segmento.getByRole('button', { name: 'Receita' })).toHaveAttribute('aria-pressed', 'true');
    await expect(lista.getByRole('article')).toHaveCount(1);
    await expect(lista.getByRole('article', { name: 'Pão de queijo de polvilho azedo' })).toBeVisible();
    expect(api.last('GET', /^\/api\/v1\/posts$/)?.search.get('kind')).toBe('RECEITA');
    await page.goBack();
    await expect(page).toHaveURL(/\/feed$/);
    await expect(lista.getByRole('article')).toHaveCount(6);

    // "Carregar mais" pagina por cursor
    expect(api.last('GET', /^\/api\/v1\/posts$/)?.search.get('size')).toBe('10');
  });

  test('"Em alta" filtra pelo tipo de conta do autor (category=) e guarda na URL', async ({ page, api }) => {
    await page.goto('/feed');
    const alta = page.getByTestId('em-alta');
    await expect(alta.getByRole('list', { name: 'Posts em alta' }).getByRole('listitem').first()).toContainText('Pão de queijo de polvilho azedo');
    await alta.getByRole('button', { name: 'Comunidade' }).click();
    await expect(page).toHaveURL(/conta=COMUNIDADE/);
    await expect(alta.getByRole('heading')).toHaveText('Em alta: Comunidade');
    await expect(alta.getByRole('listitem')).toHaveCount(1);
    await expect(alta).toContainText('Feira do pão artesanal no sábado');
    expect(api.last('GET', /\/posts\/trending$/)?.search.get('category')).toBe('COMUNIDADE');
  });

  test('aba Alertas sanitários: faixa hazard, fonte oficial e sem moldura', async ({ page }) => {
    await page.goto('/feed');
    await page.getByRole('tab', { name: 'Alertas sanitários' }).click();
    await expect(page).toHaveURL(/aba=alertas/);
    const lista = page.getByTestId('lista-alertas');
    await expect(lista.getByRole('article')).toHaveCount(2);
    const azeite = lista.getByRole('article', { name: 'Recolhimento de azeite de oliva por fraude' });
    await expect(azeite.getByTestId('faixa-alerta')).toContainText('Alerta sanitário');
    await expect(azeite.getByTestId('faixa-alerta')).toContainText('Fonte: Anvisa (www.gov.br)');
    await expect(azeite.getByTestId('dados-alerta')).toContainText('L2391, L2392, L2405');
    await expect(azeite.getByRole('link', { name: /www\.gov\.br/ })).toHaveAttribute('href', /^https:\/\/www\.gov\.br\/anvisa/);
    await expect(page.getByTestId('card-post-moldura')).toHaveCount(0);
  });

  test('aba Perfis: filtros de tipo de conta, cidade e nome', async ({ page, api }) => {
    await page.goto('/feed?aba=perfis');
    const lista = page.getByTestId('lista-perfis');
    await expect(lista.getByRole('article')).toHaveCount(7); // o perfil privado não entra
    await page.getByLabel('Tipo de conta').selectOption('CHEF');
    await page.getByLabel('Cidade').fill('belo horizonte');
    await page.getByRole('button', { name: 'Filtrar' }).click();
    await expect(page).toHaveURL(/tipoConta=CHEF/);
    await expect(lista.getByRole('article')).toHaveCount(1);
    await expect(lista.getByRole('article', { name: 'Rafael Lima' })).toContainText('Belo Horizonte – MG');
    const req = api.last('GET', /^\/api\/v1\/users$/)!;
    expect(req.search.get('type')).toBe('CHEF');
    expect(req.search.get('city')).toBe('belo horizonte');
    await lista.getByRole('link', { name: 'Rafael Lima' }).click();
    await expect(page).toHaveURL(/\/u\/310$/);
  });

  test('abas pelo teclado (setas) e sem violações graves nos dois temas', async ({ page }) => {
    await page.goto('/feed');
    await expect(page.getByTestId('lista-posts').getByRole('article').first()).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Feed · Posts');
    await page.getByRole('tab', { name: 'Posts' }).focus();
    await page.keyboard.press('ArrowRight');
    await expect(page.getByRole('tab', { name: 'Alertas sanitários' })).toBeFocused();
    await expect(page).toHaveURL(/aba=alertas/);
    await expect(page.getByTestId('lista-alertas').getByRole('article').first()).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Feed · Alertas');
    await page.keyboard.press('ArrowRight');
    await expect(page.getByTestId('lista-perfis')).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Feed · Perfis');
  });
});

test.describe('Aba Restaurantes e modal do lugar (RF10)', () => {
  test('busca com os filtros do Explorar; o cartão abre o modal pelo teclado; Esc fecha e o foco volta', async ({ page, api }) => {
    await page.goto('/feed?aba=restaurantes');
    const lista = page.getByTestId('lista-lugares');
    await expect(lista.getByRole('article')).toHaveCount(5);
    await page.getByRole('radio', { name: 'Brasileira' }).check();
    await expect(page).toHaveURL(/\/feed\?aba=restaurantes&chip=BRASILEIRA$/);
    await expect.poll(() => api.last('GET', /^\/api\/v1\/establishments$/)?.search.get('chip')).toBe('BRASILEIRA');

    const link = lista.getByRole('link', { name: 'Sabor Mineiro' });
    await expect(link).toHaveAttribute('href', '/lugares/1');
    await link.focus();
    await page.keyboard.press('Enter');
    const modal = page.getByRole('dialog', { name: 'Sabor Mineiro' });
    await expect(modal).toBeVisible();
    await expect(page).toHaveURL(/aba=restaurantes/);
    await expect(modal).toContainText('Restaurante (à la carte)');
    await expect(modal).toContainText('Mineira · Prato feito / marmita / executivo');
    await expect(modal.getByRole('img', { name: /Faixa de preço: moderado/ })).toBeVisible();
    await expect(modal).toContainText('escala nacional');
    await expect(modal).toContainText('Média de 6 avaliações');
    await expect(modal).toContainText('Opções sem glúten');
    await expect(modal).toContainText('Rua Pernambuco, 1000');
    await expect(modal.getByText('Conta verificada').first()).toBeAttached();
    await expect(modal.getByTestId('motivos')).toContainText('Culinária do grupo Brasileira.');
    await expect(modal.getByRole('link', { name: /Como chegar/ })).toHaveAttribute('href', /google\.com\/maps\/dir\/\?api=1&destination=Rua%20Pernambuco/);
    await expectAccessibleInBothThemes(page, 'Modal do lugar');

    await page.keyboard.press('Escape');
    await expect(modal).toBeHidden();
    await expect(link).toBeFocused();

    await link.click();
    await page.getByRole('dialog').getByRole('link', { name: 'Ver página do lugar' }).click();
    await expect(page).toHaveURL(/\/lugares\/1$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Sabor Mineiro' })).toBeVisible();
  });

  test('endereço aproximado: sem "Como chegar"', async ({ page }) => {
    await page.goto('/feed?aba=restaurantes');
    await page.getByTestId('lista-lugares').getByRole('link', { name: 'Acarajé da Dinha' }).click();
    const modal = page.getByRole('dialog', { name: 'Acarajé da Dinha' });
    await expect(modal).toContainText('Endereço aproximado: bairro e cidade');
    await expect(modal.getByRole('link', { name: /Como chegar/ })).toHaveCount(0);
    await expect(modal.getByTestId('motivos')).toContainText('Aparece na lista geral, sem filtros.');
  });
});

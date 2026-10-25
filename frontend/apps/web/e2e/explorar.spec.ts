import { expect, expectAccessibleInBothThemes, test } from './support/test';

test.describe('Explorar (RF10)', () => {
  test('lista os cards com chip, preço, nota e local', async ({ page, api }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1, name: 'Explorar' })).toBeVisible();
    const lista = page.getByTestId('lista-lugares');
    await expect(lista.getByRole('article')).toHaveCount(5); // o Boteco (18+) não aparece para visitante
    await expect(page.getByTestId('contagem')).toHaveText('36 lugares encontrados');

    const card = page.getByRole('article', { name: 'Sabor Mineiro' });
    await expect(card.getByText('Brasileira')).toBeVisible();
    await expect(card.getByRole('img', { name: 'Nota 4,3 de 5, com base em 6 avaliações' })).toBeVisible();
    await expect(card.getByRole('img', { name: /Faixa de preço: moderado, R\$ 40 a R\$ 80 por pessoa/ })).toBeVisible();
    await expect(card.locator('[data-dollar="cheio"]')).toHaveCount(2);
    await expect(card.locator('[data-dollar="vazado"]')).toHaveCount(2);
    await expect(page.getByRole('article', { name: 'Cantina Nonna Rosa' })).toContainText('3 avaliações, ainda sem nota média');
    const doce = page.getByRole('article', { name: 'Doce Sonho Confeitaria' });
    await expect(doce).toContainText('Sem avaliações ainda');
    await expect(doce).toContainText('Preço não informado');
    await expect(doce.locator('[data-dollar]')).toHaveCount(0);
    await expect(page.getByRole('article', { name: 'Acarajé da Dinha' })).toContainText('Localização aproximada');
    await expect(page.getByRole('article', { name: 'Raiz Vegana' })).toContainText('Fechado');
    expect(api.last('GET', /^\/api\/v1\/establishments$/)?.search.toString()).toBe('page=0&size=20&sort=RELEVANCE');
  });

  test('filtros vão para a URL e para a API', async ({ page, api }) => {
    await page.goto('/');
    await expect(page.getByTestId('lista-lugares')).toBeVisible();

    await page.getByRole('radio', { name: 'Américas' }).check();
    await expect(page).toHaveURL(/\?chip=AMERICAS$/);
    await expect.poll(() => api.last('GET', /^\/api\/v1\/establishments$/)?.search.getAll('chip')).toEqual(['AMERICAS']);

    await page.getByRole('checkbox', { name: /Moderado/ }).check();
    await page.getByRole('radio', { name: '4 ou mais' }).check();
    await page.getByRole('checkbox', { name: 'Vegano' }).check();
    await page.getByLabel('Tipo de comida').selectOption('PIZZA');
    await expect(page).toHaveURL(/chip=AMERICAS&format=PIZZA&price=PRICE_2&minRating=4&dietary=VEGANO/);
    await expect
      .poll(() => api.last('GET', /^\/api\/v1\/establishments$/)?.search.toString())
      .toBe('chip=AMERICAS&format=PIZZA&dietary=VEGANO&price=PRICE_2&minRating=4&page=0&size=20&sort=RELEVANCE');

    await page.getByRole('button', { name: 'Limpar filtros' }).first().click();
    await expect(page).toHaveURL(/\/$/);
  });

  test('um link com filtros abre a busca já filtrada', async ({ page, api }) => {
    await page.goto('/?q=sabor&chip=LANCHES_PIZZA&price=PRICE_1&minRating=4.5&cuisine=MINEIRA');
    await expect(page.getByRole('radio', { name: 'Lanches & pizza' })).toBeChecked();
    await expect(page.getByRole('checkbox', { name: /Econômico/ })).toBeChecked();
    await expect(page.getByRole('radio', { name: '4,5 ou mais' })).toBeChecked();
    await expect(page.getByLabel('Culinária específica')).toHaveValue('MINEIRA');
    await expect(page.getByRole('searchbox', { name: 'Nome do lugar' })).toHaveValue('sabor');
    await expect.poll(() => api.last('GET', /^\/api\/v1\/establishments$/)?.search.getAll('chip')).toEqual(['LANCHES_PIZZA']);
    const s = api.last('GET', /^\/api\/v1\/establishments$/)!.search;
    expect(s.get('q')).toBe('sabor');
    expect(s.getAll('cuisine')).toEqual(['MINEIRA']);
    expect(s.get('minRating')).toBe('4.5');
  });

  test('busca por nome', async ({ page, api }) => {
    await page.goto('/');
    await page.getByRole('searchbox', { name: 'Nome do lugar' }).fill('cantina');
    await page.getByRole('button', { name: 'Buscar', exact: true }).click();
    await expect(page).toHaveURL(/\?q=cantina$/);
    await expect(page.getByTestId('lista-lugares').getByRole('article')).toHaveCount(1);
    expect(api.last('GET', /^\/api\/v1\/establishments$/)?.search.get('q')).toBe('cantina');
  });

  test('perto de mim usa a localização só depois do clique e não a põe na URL', async ({ page, context, api }) => {
    await context.grantPermissions(['geolocation']);
    await context.setGeolocation({ latitude: -19.93612, longitude: -43.93456 });
    await page.goto('/?page=1');
    await expect(page.getByText('A sua localização é usada só nesta busca e não é guardada.')).toBeVisible();
    await expect(page.getByTestId('lista-lugares')).toBeVisible();
    expect(api.find('GET', /^\/api\/v1\/establishments$/).every((r) => !r.search.has('lat'))).toBe(true);

    await page.getByRole('button', { name: 'Usar minha localização' }).click();
    await expect(page.getByText('Usando a sua localização')).toBeVisible();
    // vinha da página 2: "perto de mim" recomeça da primeira (a última busca tem lat e page=0)
    await expect
      .poll(() => {
        const r = api.last('GET', /^\/api\/v1\/establishments$/)?.search;
        return r ? `${r.get('lat')}|${r.get('page')}` : null;
      })
      .toBe('-19.936|0');
    let s = api.last('GET', /^\/api\/v1\/establishments$/)!.search;
    expect(s.get('lng')).toBe('-43.935');
    expect(page.url()).not.toMatch(/page=/);
    expect(s.get('radiusKm')).toBe('5');
    await expect(page.getByTestId('contagem')).toContainText('num raio de 5 km');
    await expect(page.getByRole('article', { name: 'Sabor Mineiro' })).toContainText('a 400 m');

    await page.getByLabel('Raio').selectOption('10');
    await page.getByLabel('Ordenar por').selectOption('DISTANCE');
    await expect(page).toHaveURL(/radiusKm=10&sort=DISTANCE/);
    expect(page.url()).not.toMatch(/lat|lng/);
    await expect.poll(() => api.last('GET', /^\/api\/v1\/establishments$/)?.search.get('sort')).toBe('DISTANCE');
    s = api.last('GET', /^\/api\/v1\/establishments$/)!.search;
    expect(s.get('radiusKm')).toBe('10');

    await page.getByRole('button', { name: 'Parar de usar a localização' }).click();
    await expect.poll(() => api.last('GET', /^\/api\/v1\/establishments$/)?.search.has('lat')).toBe(false);
  });

  test('perto de mim sem permissão explica o que fazer', async ({ page }) => {
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'geolocation', {
        value: {
          getCurrentPosition: (_ok: unknown, fail: (e: { code: number; PERMISSION_DENIED: number; TIMEOUT: number }) => void) =>
            fail({ code: 1, PERMISSION_DENIED: 1, TIMEOUT: 3 }),
        },
      });
    });
    await page.goto('/');
    await page.getByRole('button', { name: 'Usar minha localização' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Você não permitiu o uso da localização' })).toBeVisible();
  });

  test('sem resultados oferece limpar os filtros; paginação é um link', async ({ page, api }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: 'Próxima' })).toHaveAttribute('href', '/?page=1');
    api.on('GET', /^\/api\/v1\/establishments$/, { status: 200, body: { items: [], page: 0, size: 20, total: 0, radiusKm: null } });
    await page.getByRole('checkbox', { name: 'Kosher' }).check();
    await expect(page.getByText('Nenhum lugar encontrado com esses filtros.')).toBeVisible();
  });

  test('sem violações graves de acessibilidade', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('lista-lugares')).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Explorar');
  });
});

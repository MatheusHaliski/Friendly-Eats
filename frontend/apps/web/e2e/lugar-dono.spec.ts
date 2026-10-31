import type { EstablishmentDraft } from '@friendly-eats/api';
import { problem } from './support/mock-api';
import { expect, expectAccessibleInBothThemes, test } from './support/test';

test.describe('Formulário do dono: cadastrar e editar o lugar (RF6 · RF11)', () => {
  test('Meus lugares leva ao cadastro; o POST leva tudo com CSRF e a página nasce fora do ar', async ({ page, api }) => {
    api.login('estabelecimento');
    await page.goto('/conta');
    await page.getByRole('link', { name: 'Cadastrar lugar' }).click();
    await expect(page).toHaveURL(/\/lugares\/novo$/);
    const form = page.getByRole('form', { name: 'Cadastrar lugar' });

    await form.getByLabel('Nome do lugar').fill('Cantina da Rita');
    await form.getByLabel('Tipo principal').selectOption('RESTAURANTE_A_LA_CARTE');
    await form.getByLabel('Culinária principal').selectOption('MINEIRA');
    await form.getByRole('combobox', { name: 'UF (obrigatório)' }).selectOption('MG');
    await form.getByLabel('Código IBGE do município').fill('3106200');
    await form.getByLabel('Cidade (obrigatório)', { exact: true }).fill('Belo Horizonte');
    await form.getByLabel('Rua', { exact: true }).fill('Rua Sergipe');
    await form.getByLabel('Número', { exact: true }).fill('100');
    // pino: clique no mapa (ou as coordenadas digitadas, sem arrastar)
    await form.getByTestId('mapa-lugar').click();
    await expect(form.getByLabel('Latitude', { exact: true })).not.toHaveValue('');
    await form.getByLabel('Latitude', { exact: true }).fill('-19.937');
    await form.getByLabel('Longitude', { exact: true }).fill('-43.933');
    await form.getByLabel('De (R$)').fill('45');

    await form.getByLabel('Informar o horário da semana').check();
    await form.getByRole('button', { name: 'Adicionar faixa em sexta' }).click();
    await form.getByRole('group', { name: 'Sexta' }).getByLabel('Abre (1ª faixa)').fill('18:00');
    await form.getByRole('group', { name: 'Sexta' }).getByLabel('Fecha (1ª faixa)').fill('02:00');

    await form.getByLabel('Taxa de serviço (%)').fill('10');
    await form.getByLabel('Wi-Fi para clientes').selectOption('SIM');
    await expect(form.getByText('Cão-guia ou de assistência sempre permitido')).toBeVisible();
    await form.getByLabel('Link do iFood').fill('https://www.ifood.com.br/delivery/bh/cantina');
    await form.getByLabel('App de delivery preferido (aparece primeiro)').selectOption('IFOOD');
    await form.getByLabel('WhatsApp do estabelecimento').fill('(31) 99999-8888');
    await form.getByRole('button', { name: 'Cadastrar lugar' }).click();

    await expect(page).toHaveURL(/\/lugares\/50$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Cantina da Rita' })).toBeVisible();
    await expect(page.getByText('Esta página está fora do ar: só você a vê.')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Editar a página' })).toHaveAttribute('href', '/lugares/50/editar');
    await expect(page.getByRole('region', { name: 'Cobranças' })).toContainText('Taxa de serviço de 10% (opcional)');

    const post = api.last('POST', /\/api\/v1\/establishments$/)!;
    expect(post.headers['x-xsrf-token']).toBe(api.csrfToken);
    const body = post.body as EstablishmentDraft;
    expect(body).toMatchObject({
      name: 'Cantina da Rita',
      primaryType: 'RESTAURANTE_A_LA_CARTE',
      location: { uf: 'MG', municipalityIbge: '3106200', city: 'Belo Horizonte', latitude: -19.937, longitude: -43.933, addressVisibility: 'ADDR_PUBLIC_FULL' },
      price: { minCents: 4500, maxCents: null, notApplicable: false },
      hours: { weekly: { FRIDAY: [{ opens: '18:00', closes: '02:00' }] }, special: [] },
      temporaryClosure: { closed: false, reopensOn: null },
      charges: { serviceFeePercent: 10, couvert: null, paymentAdjustments: [] },
      facets: { WIFI: 'SIM' },
      actions: { links: [{ provider: 'IFOOD', url: 'https://www.ifood.com.br/delivery/bh/cantina', preferred: true }], whatsapp: '(31) 99999-8888' },
    });
    expect(body.status).toBeUndefined();
  });

  test('erros locais focam o resumo; o 422 da API vai para o campo certo', async ({ page, api }) => {
    api.login('estabelecimento');
    await page.goto('/lugares/novo');
    const form = page.getByRole('form', { name: 'Cadastrar lugar' });
    await form.getByRole('button', { name: 'Cadastrar lugar' }).click();
    const resumo = page.getByTestId('error-summary');
    await expect(resumo).toBeFocused();
    await expect(resumo).toContainText('Informe o nome do lugar.');
    await expect(resumo).toContainText('Escolha o tipo principal.');
    await expect(form.getByLabel('Nome do lugar')).toHaveAttribute('aria-invalid', 'true');
    expect(api.find('POST', /\/api\/v1\/establishments$/)).toHaveLength(0);

    await form.getByLabel('Nome do lugar').fill('Cantina da Rita');
    await form.getByLabel('Tipo principal').selectOption('RESTAURANTE_A_LA_CARTE');
    await form.getByRole('combobox', { name: 'UF (obrigatório)' }).selectOption('MG');
    await form.getByLabel('Código IBGE do município').fill('3106200');
    await form.getByLabel('Cidade (obrigatório)', { exact: true }).fill('Belo Horizonte');
    await form.getByLabel('Link do iFood').fill('https://ifood.golpe.example/x');
    api.on(
      'POST',
      /\/api\/v1\/establishments$/,
      problem(422, 'Revise os dados', {
        errors: [
          'Link do iFood: endereço HTTPS de ifood.com.br (ex.: https://www.ifood.com.br/...).',
          'Consumação mínima não é aceita: os Procons a consideram cobrança abusiva (CDC art. 39). Cobre só o que for consumido.',
        ],
      }),
    );
    await form.getByRole('button', { name: 'Cadastrar lugar' }).click();
    await expect(resumo).toBeFocused();
    await expect(resumo).toContainText('Link do iFood: endereço HTTPS de ifood.com.br');
    await expect(form.getByRole('group', { name: 'Links' })).toHaveAccessibleDescription(/Link do iFood: endereço HTTPS de ifood\.com\.br/);
    await expect(form.getByLabel('Taxa de serviço (%)')).toHaveAccessibleDescription(/Consumação mínima não é aceita/);
    await expectAccessibleInBothThemes(page, 'formulário do lugar com erros');
  });

  test('editar: campos preenchidos, pausa com data de volta e situação no PATCH', async ({ page, api }) => {
    api.login('estabelecimento');
    await page.goto('/conta');
    await page.getByRole('link', { name: 'Editar Sabor Mineiro – Pampulha' }).click();
    await expect(page).toHaveURL(/\/lugares\/7\/editar$/);
    const form = page.getByRole('form', { name: 'Editar Sabor Mineiro – Pampulha' });
    await expect(form.getByLabel('Nome do lugar')).toHaveValue('Sabor Mineiro – Pampulha');
    await form.getByLabel('Fechado temporariamente (reforma, férias)').check();
    await form.getByLabel('Data de volta (opcional)').fill('2026-12-01');
    await form.getByRole('button', { name: 'Salvar alterações' }).click();
    await expect(page).toHaveURL(/\/lugares\/7$/);
    await expect(page.getByTestId('aberto-agora').first()).toHaveText('Fechado temporariamente');
    const patch = api.last('PATCH', /\/api\/v1\/establishments\/7$/)!;
    expect(patch.headers['x-xsrf-token']).toBe(api.csrfToken);
    expect(patch.body).toMatchObject({ name: 'Sabor Mineiro – Pampulha', status: 'ACTIVE', temporaryClosure: { closed: true, reopensOn: '2026-12-01' } });
  });

  test('só conta de Estabelecimento ou Varejo cadastra; quem não cuida da página não edita', async ({ page, api }) => {
    api.login('comensal');
    await page.goto('/lugares/novo');
    await expect(page.getByText('Só contas de Estabelecimento ou de Varejo & Marca cadastram lugares.')).toBeVisible();
    await expect(page.getByRole('form')).toHaveCount(0);

    api.login('estabelecimento');
    await page.goto('/lugares/2/editar');
    await expect(page.getByRole('heading', { level: 1, name: 'Só quem cuida da página edita o lugar' })).toBeVisible();
  });

  test('visitante vai para o login com voltar', async ({ page }) => {
    await page.goto('/lugares/novo');
    await expect(page).toHaveURL(/\/entrar\?voltar=%2Flugares%2Fnovo$/);
  });
});

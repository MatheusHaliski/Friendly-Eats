import { expect, expectAccessibleInBothThemes, test } from './support/test';

test.describe('Página do lugar: horários, cobranças, facetas e botões (RF11)', () => {
  test('aberto agora, semana com virada da meia-noite, datas especiais e fuso', async ({ page }) => {
    await page.goto('/lugares/1');
    await expect(page.getByRole('banner').or(page.locator('header')).getByTestId('aberto-agora').first()).toHaveText('Aberto agora · fecha às 15:00');
    const horarios = page.getByRole('region', { name: 'Horários' });
    await expect(horarios.getByTestId('horarios')).toContainText('Segunda');
    const sexta = horarios.getByTestId('horarios').locator('div').filter({ hasText: 'Sexta' });
    await expect(sexta).toContainText('Sexta (hoje)');
    await expect(sexta).toContainText('11:30–15:00 e 18:00–02:00 (passa da meia-noite)');
    await expect(horarios.getByTestId('horarios').locator('div').filter({ hasText: 'Segunda' })).toContainText('Fechado');
    await expect(horarios.getByTestId('datas-especiais')).toHaveText('15/11 (dom) · Proclamação da República: fechado');
    await expect(horarios).toContainText('Horários no horário de Brasília');
  });

  test('cobranças com os textos fixos, a data da declaração e o lembrete do aviso na porta', async ({ page }) => {
    await page.goto('/lugares/1');
    const cobrancas = page.getByRole('region', { name: 'Cobranças' });
    await expect(cobrancas).toContainText('Taxa de serviço de 10% (opcional)');
    await expect(cobrancas).toContainText('É gorjeta: você paga só se quiser (Lei 13.419/2017).');
    await expect(cobrancas).toContainText('Couvert artístico de R$ 15 por pessoa, só nos dias com apresentação ao vivo (sexta, sábado)');
    await expect(cobrancas).toContainText('5% de desconto no Pix');
    await expect(cobrancas).toContainText('Declarado pelo estabelecimento em 20/10/2026.');
    await expect(cobrancas).toContainText('Decreto 5.903/2006, art. 8º, § 2º');
  });

  test('facetas com rótulo, valor em texto e origem; cão de assistência sempre permitido', async ({ page }) => {
    await page.goto('/lugares/1');
    const comodidades = page.getByRole('region', { name: 'Comodidades, acessibilidade e pagamento' });
    await expect(comodidades.getByTestId('faceta-CAO_ASSISTENCIA')).toContainText('Cão-guia ou de assistência sempre permitido: Sempre permitido (Lei 11.126/2005)');
    await expect(comodidades.getByTestId('faceta-CAO_ASSISTENCIA')).toContainText('Garantido por lei');
    await expect(comodidades.getByTestId('faceta-PET_FRIENDLY')).toContainText('Aceita pets: Aceita pets só na área externa');
    await expect(comodidades.getByTestId('faceta-ENTRADA_ACESSIVEL')).toContainText('Parcial');
    await expect(comodidades.getByTestId('faceta-ALMOCO')).toContainText('Pelo horário de funcionamento');
    await expect(comodidades.getByTestId('faceta-DELIVERY_APP')).toContainText('Pelos links de delivery');
    await expect(comodidades.getByRole('heading', { name: 'Acessibilidade', exact: true })).toBeVisible();
  });

  test('botões de ação só abrem links, em outra aba e sem referência', async ({ page }) => {
    await page.goto('/lugares/1');
    const botoes = page.getByTestId('botoes-acao');
    const ifood = botoes.getByRole('link', { name: /Pedir pelo iFood/ });
    await expect(ifood).toHaveAttribute('href', 'https://www.ifood.com.br/delivery/belo-horizonte-mg/sabor-mineiro');
    await expect(ifood).toHaveAttribute('target', '_blank');
    await expect(ifood).toHaveAttribute('rel', 'noopener noreferrer nofollow');
    await expect(ifood).toContainText('preferido');
    await expect(ifood).toHaveAccessibleDescription('Você vai sair do Friendly Eats. Pedido e pagamento são com iFood.');
    await expect(botoes.getByRole('link', { name: /Conversar no WhatsApp/ })).toHaveAttribute('href', 'https://wa.me/5531999998888');
    await expect(botoes.getByRole('link', { name: /Reservar pelo TheFork/ })).toHaveAttribute('rel', 'noopener noreferrer nofollow');
    await expect(page.getByRole('link', { name: 'Editar a página' })).toHaveCount(0);
  });

  test('mapa com atribuição do OpenStreetMap, como chegar só com endereço completo', async ({ page }) => {
    await page.goto('/lugares/1');
    const local = page.getByRole('region', { name: 'Local' });
    await expect(local.getByRole('group', { name: 'Mapa com o local de Sabor Mineiro' })).toBeVisible();
    await expect(local.getByRole('link', { name: 'colaboradores do OpenStreetMap' })).toHaveAttribute('href', 'https://www.openstreetmap.org/copyright');
    await expect(local.getByRole('link', { name: /Como chegar/ })).toHaveAttribute('href', /google\.com\/maps\/dir/);
    await expect(local.getByRole('link', { name: /Ver no OpenStreetMap/ })).toHaveAttribute('href', 'https://www.openstreetmap.org/?mlat=-19.936&mlon=-43.934#map=17/-19.936/-43.934');

    await page.goto('/lugares/3');
    const aproximado = page.getByRole('region', { name: 'Local' });
    await expect(aproximado.getByText('Localização aproximada')).toBeVisible();
    await expect(aproximado.getByRole('link', { name: /Como chegar/ })).toHaveCount(0);
  });

  test('sem horário diz "Horário não informado"; pausa mostra a data de volta', async ({ page }) => {
    await page.goto('/lugares/2');
    await expect(page.getByTestId('aberto-agora').first()).toHaveText('Horário não informado');
    await expect(page.getByRole('region', { name: 'Horários' })).toContainText('Horário não informado.');
    await expect(page.getByTestId('cobrancas')).toContainText('não declarou taxa de serviço');
    await expect(page.getByTestId('botoes-acao')).toHaveCount(0);

    await page.goto('/lugares/3');
    await expect(page.getByTestId('aberto-agora').first()).toHaveText('Fechado temporariamente · volta em 20/11/2026');
    await expect(page.getByRole('region', { name: 'Horários' })).toContainText('com volta prevista para 20/11/2026');
  });

  test('modal do Explorar mostra o aberto agora e as cobranças resumidas', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('lista-lugares').getByRole('link', { name: 'Sabor Mineiro' }).click();
    const modal = page.getByTestId('modal-lugar');
    await expect(modal.getByTestId('aberto-agora')).toHaveText('Aberto agora · fecha às 15:00');
    await expect(modal.getByTestId('cobrancas-resumo')).toContainText('Taxa de serviço de 10% (opcional)');
  });

  test('sem violações graves de acessibilidade na página completa', async ({ page }) => {
    await page.goto('/lugares/1');
    await expect(page.getByTestId('mapa-lugar').locator('.leaflet-tile').first()).toBeAttached();
    await expectAccessibleInBothThemes(page, 'lugar com RF11 completo');
  });
});

// RF17 (idade verificada e conta do responsável) e RF18 (encarregado e "Seus dados") no app web.
import { SENHA_CERTA } from './support/mock-api';
import { CONVITE_VALIDO } from './support/mock-titular';
import { expect, expectAccessibleInBothThemes, test } from './support/test';

const AVISO = 'Conteúdo de bebida alcoólica aparece só depois de verificar a idade';

test.describe('Conteúdo de bebida oculto até verificar a idade (RF17)', () => {
  test('visitante vê o aviso no feed e na busca, com o caminho para entrar e verificar', async ({ page }) => {
    await page.goto('/feed');
    const aviso = page.getByTestId('aviso-bebida');
    await expect(aviso).toContainText(AVISO);
    await expect(aviso.getByRole('link', { name: 'Verificar a idade' })).toHaveAttribute('href', '/entrar?voltar=%2Fconfiguracoes%2Fidade');
    await page.goto('/');
    await expect(page.getByTestId('aviso-bebida')).toContainText(AVISO);
  });

  test('adulto sem verificação: aviso, bar fora da busca e página do lugar pede a verificação', async ({ page, api }) => {
    api.login();
    api.titular.setAge('UNVERIFIED');
    await page.goto('/');
    await expect(page.getByTestId('aviso-bebida').getByRole('link', { name: 'Verificar a idade' })).toHaveAttribute('href', '/configuracoes/idade');
    await expect(page.getByTestId('contagem')).toBeVisible();
    await page.goto('/lugares/18');
    await expect(page.getByRole('heading', { level: 1, name: 'Verifique a sua idade para ver' })).toBeVisible();
    await expect(page.getByText(AVISO)).toBeVisible();
    await expectAccessibleInBothThemes(page, 'lugar 18+ sem verificação');
    await page.getByRole('link', { name: 'Verificar a idade' }).click();
    await expect(page).toHaveURL(/\/configuracoes\/idade$/);
  });

  test('adulto verificado não recebe o aviso', async ({ page, api }) => {
    api.login();
    await page.goto('/feed');
    await expect(page.getByTestId('lista-posts')).toBeVisible();
    await expect(page.getByTestId('aviso-bebida')).toHaveCount(0);
  });
});

test.describe('Configurações → Idade e responsável (RF17)', () => {
  test('verificar a idade pelo fornecedor simulado e ver o histórico', async ({ page, api }) => {
    api.login();
    api.titular.setAge('UNVERIFIED');
    await page.goto('/configuracoes/idade');
    await expect(page.getByRole('heading', { level: 1, name: 'Idade e responsável' })).toBeFocused();
    const cartao = page.getByTestId('cartao-idade');
    await expect(cartao.getByTestId('status-idade')).toHaveText('Idade não verificada');
    await expect(cartao).toContainText('nunca a imagem do documento');
    await cartao.getByRole('button', { name: 'Verificar minha idade' }).click();
    const simulado = page.getByRole('group', { name: 'Fornecedor simulado' });
    await expect(simulado).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Idade: fornecedor simulado');
    await simulado.getByRole('button', { name: 'Resultado: maior de 18' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Idade verificada' })).toBeVisible();
    await expect(cartao.getByTestId('status-idade')).toHaveText('Maior de 18 verificado');
    await expect(cartao).toContainText('Aparece para você');
    const done = api.last('POST', /\/age-verification\/complete$/);
    expect(done?.body).toEqual({ reference: 'fake-ref-1', params: { simulate: 'ADULT' } });
    await cartao.getByText('Histórico da verificação').click();
    await expect(cartao.getByRole('listitem').first()).toContainText('Maior de 18 verificado');
  });

  test('a volta do fornecedor conclui a verificação pela referência da URL', async ({ page, api }) => {
    api.login();
    api.titular.setAge('UNVERIFIED');
    await page.goto('/configuracoes/idade');
    await page.getByRole('button', { name: 'Verificar minha idade' }).click();
    await expect(page.getByRole('group', { name: 'Fornecedor simulado' })).toBeVisible();
    await page.goto('/configuracoes/idade?verificacao=fake-ref-1&simulate=ADULT');
    await expect(page.getByTestId('status-idade')).toHaveText('Maior de 18 verificado');
    await expect(page).toHaveURL(/\/configuracoes\/idade$/);
  });

  test('resultado de menor pode ser contestado', async ({ page, api }) => {
    api.login();
    api.titular.setAge('VERIFIED_MINOR');
    await page.goto('/configuracoes/idade');
    const cartao = page.getByTestId('cartao-idade');
    await cartao.getByRole('button', { name: 'Enviar contestação' }).click();
    await expect(cartao.getByText('Explique o motivo em pelo menos 10 caracteres.')).toBeVisible();
    await cartao.getByLabel('Motivo').fill('Tenho 34 anos; a foto do documento saiu borrada.');
    await cartao.getByRole('button', { name: 'Enviar contestação' }).click();
    await expect(cartao.getByTestId('contestacao')).toContainText('Em análise por uma pessoa');
  });

  test('menor de 16: vínculo pendente com aviso, reenviar e pedir só depois do aceite', async ({ page, api }) => {
    api.login('comensal', { minor: true });
    await page.goto('/configuracoes/idade');
    const cartao = page.getByTestId('cartao-responsavel');
    await expect(cartao.getByTestId('status-vinculo')).toHaveText('Convite enviado, aguardando o responsável');
    await expect(cartao.getByTestId('aviso-pendente')).toContainText('sem resposta há 10 dias');
    await expect(page.getByTestId('cartao-idade')).toContainText('você tem menos de 18 anos');
    await expect(cartao.getByRole('button', { name: /Pedir ao responsável/ }).first()).toBeDisabled();
    await expectAccessibleInBothThemes(page, 'Idade: menor com vínculo pendente');
    await cartao.getByRole('button', { name: 'Reenviar convite' }).click();
    await expect(cartao.getByRole('status')).toContainText('Convite enviado ao responsável.');
    await cartao.getByLabel('Convidar outro e-mail').fill('avo@example.com');
    await cartao.getByRole('button', { name: 'Enviar convite', exact: true }).click();
    await expect(cartao).toContainText('avo@example.com');
    expect(api.last('POST', /\/me\/guardian\/invite$/)?.body).toEqual({ guardianEmail: 'avo@example.com' });
  });

  test('menor com vínculo ativo pede ao responsável', async ({ page, api }) => {
    api.login('comensal', { minor: true });
    api.titular.guardian = { ...api.titular.guardian!, status: 'ACTIVE', guardian: { id: 9, name: 'Marta Souza', handle: 'marta' }, overdue: false };
    await page.goto('/configuracoes/idade');
    const cartao = page.getByTestId('cartao-responsavel');
    await expect(cartao.getByTestId('status-vinculo')).toHaveText('Supervisão ativa: Marta Souza (@marta)');
    await cartao.getByRole('button', { name: /Pedir ao responsável.*Perfil público/ }).click();
    await expect(cartao.getByRole('status')).toContainText('Pedido enviado ao responsável: Perfil público.');
    await expect(cartao.getByText('Pedido enviado', { exact: true })).toBeVisible();
  });

  test('responsável: o convite exige idade verificada, depois o painel aprova e muda as proteções', async ({ page, api }) => {
    api.login();
    api.titular.setAge('UNVERIFIED');
    await page.goto(`/configuracoes/idade?convite=${CONVITE_VALIDO}`);
    const convite = page.getByTestId('cartao-convite');
    await convite.getByRole('button', { name: 'Aceitar o vínculo' }).click();
    await expect(convite.getByRole('alert')).toContainText('verificar a idade');
    api.titular.setAge('VERIFIED_ADULT');
    await convite.getByRole('button', { name: 'Aceitar o vínculo' }).click();
    await expect(convite.getByRole('status')).toContainText('Agora você supervisiona a conta de Duda Andrade (@duda)');
    await page.getByTestId('cartao-supervisao').getByRole('link', { name: /Duda Andrade/ }).click();
    await expect(page).toHaveURL(/\/configuracoes\/idade\/menor\/77$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Painel de supervisão' })).toBeFocused();
    await expect(page.getByRole('navigation', { name: 'Seções das configurações' }).getByRole('link', { name: 'Idade e responsável' })).toHaveAttribute('aria-current', 'page');
    await expectAccessibleInBothThemes(page, 'Painel de supervisão');

    await page.getByTestId('pedidos').getByRole('button', { name: /Aprovar.*Perfil público/ }).click();
    await expect(page.getByRole('status')).toContainText('Pedido aprovado: Perfil público.');
    await expect(page.getByRole('switch', { name: 'Perfil público' })).toHaveAttribute('aria-checked', 'false');
    await page.getByRole('switch', { name: 'Comentários de todo mundo' }).click();
    await expect(page.getByRole('status')).toContainText('Comentários de todo mundo: liberado.');
    expect(api.last('PUT', /\/protections\/COMMENTS_FROM_EVERYONE$/)?.body).toEqual({ allowed: true });
    await page.getByRole('switch', { name: 'Consinto que guarde os estilos alimentares' }).click();
    await expect(page.getByRole('status')).toContainText('Consentimento registrado.');
    await page.getByRole('button', { name: 'Desfazer o vínculo' }).click();
    await expect(page).toHaveURL(/\/configuracoes\/idade$/);
    await expect(page.getByTestId('cartao-supervisao')).toHaveCount(0);
  });
});

test.describe('Seus dados e Privacidade (RF18)', () => {
  test('pedir a exportação com a senha e baixar quando fica pronta', async ({ page, api }) => {
    api.login();
    await page.goto('/configuracoes/conta');
    const card = page.getByTestId('seus-dados');
    await card.getByRole('button', { name: 'Pedir uma cópia…' }).click();
    const dialogo = page.getByRole('dialog', { name: 'Pedir uma cópia dos seus dados' });
    await expect(dialogo.getByRole('button', { name: 'Cancelar' })).toBeFocused();
    await expectAccessibleInBothThemes(page, 'Seus dados: diálogo');
    await dialogo.getByRole('button', { name: 'Pedir a cópia' }).click();
    await expect(dialogo.getByTestId('error-summary')).toContainText('Digite a sua senha atual.');
    await dialogo.getByLabel('Senha atual').fill('errada');
    await dialogo.getByRole('button', { name: 'Pedir a cópia' }).click();
    await expect(dialogo.getByTestId('error-summary')).toContainText('Senha incorreta.');
    await dialogo.getByLabel('Senha atual').fill(SENHA_CERTA);
    await dialogo.getByRole('button', { name: 'Pedir a cópia' }).click();
    await expect(dialogo).toBeHidden();
    await expect(card.getByRole('status')).toContainText('Pedido registrado. Avisaremos por e-mail');
    await expect(card.getByTestId('pedido-exportacao')).toContainText('Pedido recebido');
    await expect(card.getByRole('button', { name: 'Pedir uma cópia…' })).toBeDisabled();

    api.titular.finishExport();
    await expect(card.getByRole('link', { name: 'Baixar ZIP' })).toHaveAttribute('href', /exports\/42\/.*X-Amz-Signature/, { timeout: 12_000 });
    await expect(card.getByTestId('pedido-exportacao')).toContainText('Pronto para baixar');
    await expectAccessibleInBothThemes(page, 'Seus dados com o ZIP pronto');
  });

  test('/privacidade é pública: encarregado, direitos e o e-mail do pedido do titular', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('contentinfo').getByRole('link', { name: 'Privacidade e seus dados' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Privacidade e seus dados' })).toBeFocused();
    const enc = page.getByTestId('encarregado');
    await expect(enc).toContainText('Encarregada de Exemplo');
    await expect(enc.getByRole('link', { name: 'privacidade@friendlyeats.example' })).toHaveAttribute('href', 'mailto:privacidade@friendlyeats.example');
    await expect(page.getByRole('listitem').filter({ hasText: 'Acessar e receber uma cópia' })).toBeVisible();
    await expectAccessibleInBothThemes(page, '/privacidade');

    const form = page.getByTestId('pedido-titular');
    await form.getByRole('button', { name: 'Preparar o e-mail' }).click();
    await expect(form.getByTestId('error-summary')).toBeFocused();
    await form.getByLabel('Seu nome').fill('Rafael Lima');
    await form.getByLabel('E-mail para a resposta').fill('rafa@example.com');
    await form.getByLabel('Qual direito').selectOption('Corrigir dados');
    await form.getByLabel('Seu pedido').fill('Meu nome aparece errado numa avaliação antiga.');
    await form.getByRole('button', { name: 'Preparar o e-mail' }).click();
    const link = form.getByTestId('abrir-email');
    await expect(link).toHaveAttribute('href', /^mailto:privacidade%40friendlyeats\.example\?subject=/);
    expect(decodeURIComponent((await link.getAttribute('href')) ?? '')).toContain('Direito: Corrigir dados');
  });
});

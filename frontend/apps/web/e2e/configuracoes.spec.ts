import { fileURLToPath } from 'node:url';
import { CODIGO_2FA, fixture, problem, SENHA_CERTA } from './support/mock-api';
import { expect, expectAccessibleInBothThemes, test } from './support/test';

const AVATAR = fileURLToPath(new URL('./fixtures/avatar.png', import.meta.url));
const SECOES = ['perfil', 'exibicao', 'privacidade', 'seguranca', 'denuncias', 'conta'] as const;

test.describe('Configurações: casca', () => {
  test('sem sessão, cada seção vai para o login com voltar', async ({ page }) => {
    await page.goto('/configuracoes/perfil');
    await expect(page).toHaveURL(/\/entrar\?voltar=%2Fconfiguracoes%2Fperfil$/);
    await page.goto('/configuracoes');
    await expect(page).toHaveURL(/\/entrar\?voltar=%2Fconfiguracoes$/);
  });

  test('cabeçalho leva às Configurações; lista, menu lateral e foco no título da seção', async ({ page, api }) => {
    api.login();
    await page.goto('/');
    await page.getByTestId('link-configuracoes').click();
    await expect(page).toHaveURL(/\/configuracoes$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Configurações' })).toBeFocused();
    const lista = page.getByRole('main').getByRole('navigation', { name: 'Seções das configurações' });
    await expect(lista.getByRole('link')).toHaveText([/^Perfil/, /^Exibição/, /^Privacidade/, /^Senha e segurança/, /^Denúncias e decisões/, /^Conta/]);
    await expectAccessibleInBothThemes(page, 'Configurações (lista)');

    await lista.getByRole('link', { name: /^Privacidade/ }).click();
    await expect(page).toHaveURL(/\/configuracoes\/privacidade$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Privacidade' })).toBeFocused();
    const menu = page.getByRole('navigation', { name: 'Seções das configurações' });
    await expect(menu.getByRole('link', { name: 'Privacidade' })).toHaveAttribute('aria-current', 'page');
    await menu.getByRole('link', { name: 'Senha e segurança' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Senha e segurança' })).toBeFocused();
  });

  test('celular: a lista é o menu e cada seção tem "voltar"', async ({ page, api }) => {
    api.login();
    await page.setViewportSize({ width: 360, height: 780 });
    await page.goto('/configuracoes');
    await page.getByRole('link', { name: /^Exibição/ }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Exibição' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Seções das configurações' })).toBeHidden();
    for (const secao of SECOES) {
      await page.goto(`/configuracoes/${secao}`);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await page.waitForLoadState('networkidle');
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `rolagem horizontal em ${secao}`).toBeLessThanOrEqual(0);
    }
    await page.getByRole('link', { name: 'Configurações', exact: true }).first().click();
    await expect(page).toHaveURL(/\/configuracoes$/);
  });

  test('/conta continua e aponta para as configurações', async ({ page, api }) => {
    api.login();
    await page.goto('/conta');
    const atalhos = page.getByRole('region', { name: 'Configurações' });
    await expect(atalhos.getByRole('link')).toHaveCount(6);
    await atalhos.getByRole('link', { name: /^Conta/ }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Conta' })).toBeVisible();
  });

  for (const secao of SECOES) {
    test(`axe sem violações graves: ${secao}`, async ({ page, api }) => {
      api.login();
      await page.goto(`/configuracoes/${secao}`);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await page.waitForLoadState('networkidle');
      await expect(page.getByText('Carregando…')).toHaveCount(0);
      await expectAccessibleInBothThemes(page, `Configurações → ${secao}`);
    });
  }
});

test.describe('Perfil (RF2)', () => {
  test('trocar a foto: URL pré-assinada, PUT direto no bucket e PUT /me/photo; remover volta às iniciais', async ({ page, api }) => {
    api.login();
    await page.goto('/configuracoes/perfil');
    await expect(page.getByTestId('iniciais')).toContainText('JA');
    await page.getByLabel('Escolher foto').setInputFiles(AVATAR);
    await expect(page.getByRole('status').filter({ hasText: 'Foto atualizada.' })).toBeVisible();
    await expect(page.getByRole('img', { name: 'Sua foto de perfil' })).toBeVisible();
    await expect.poll(() => page.getByRole('img', { name: 'Sua foto de perfil' }).evaluate((img: HTMLImageElement) => img.naturalWidth)).toBe(128);

    const ticket = api.last('POST', /\/media\/uploads$/)!;
    expect(ticket.body).toEqual({ kind: 'IMAGE', contentType: 'image/png', sizeBytes: 474 });
    expect(ticket.headers['x-xsrf-token']).toMatch(/^csrf-/);
    const put = api.account.bucketPuts[0]!;
    expect(put.method).toBe('PUT');
    expect(put.url).toContain('X-Amz-Signature=teste');
    expect(put.headers['content-type']).toBe('image/png');
    expect(put.headers['x-xsrf-token']).toBeUndefined();
    expect(put.headers.cookie).toBeUndefined();
    expect(put.size).toBe(474);
    expect(api.last('PUT', /\/me\/photo$/)!.body).toEqual({ objectKey: 'uploads/42/01991.png' });

    await page.getByRole('button', { name: 'Remover foto' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Foto removida' })).toBeVisible();
    await expect(page.getByTestId('iniciais')).toBeVisible();
    expect(api.find('DELETE', /\/me\/photo$/)).toHaveLength(1);
  });

  test('foto em formato errado: erro no campo, sem pedir URL', async ({ page, api }) => {
    api.login();
    await page.goto('/configuracoes/perfil');
    await page.getByLabel('Escolher foto').setInputFiles({ name: 'eu.gif', mimeType: 'image/gif', buffer: Buffer.from('GIF89a') });
    await expect(page.getByLabel('Escolher foto')).toHaveAccessibleDescription(/Envie a foto em JPEG ou PNG\./);
    expect(api.find('POST', /\/media\/uploads$/)).toHaveLength(0);
  });

  test('salva só o que mudou; 422 e 409 aparecem no campo', async ({ page, api }) => {
    api.login();
    await page.goto('/configuracoes/perfil');
    await page.getByLabel('Bio').fill('Pão de queijo, café coado e feira de domingo.');
    await expect(page.getByText('45 de 150 caracteres')).toBeVisible();
    await page.getByLabel('Adicionar culinária').selectOption('ITALIANA');
    await expect(page.getByTestId('contagem-culinarias')).toHaveText('3 de 5 escolhidas');
    await page.getByRole('button', { name: 'Remover Mineira' }).click();
    await page.getByRole('button', { name: 'Salvar perfil' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Perfil salvo.' })).toBeVisible();
    expect(api.last('PATCH', /\/me\/profile$/)!.body).toEqual({
      bio: 'Pão de queijo, café coado e feira de domingo.',
      favoriteCuisines: ['JAPONESA', 'ITALIANA'],
    });

    await page.getByLabel('@usuário').fill('emuso');
    await page.getByRole('button', { name: 'Salvar perfil' }).click();
    await expect(page.getByTestId('error-summary')).toBeFocused();
    await expect(page.getByLabel('@usuário')).toHaveAccessibleDescription(/Esse @usuário já está em uso\. Tente outro\./);

    api.on('PATCH', /\/me\/profile$/, problem(422, 'Revise os campos.', { errors: ['Culinária desconhecida: XYZ.', 'A bio tem no máximo 150 caracteres.'] }));
    await page.getByLabel('@usuário').fill('julia.andrade');
    await page.getByRole('button', { name: 'Salvar perfil' }).click();
    await expect(page.getByLabel('Adicionar culinária')).toHaveAccessibleDescription(/Culinária desconhecida: XYZ\./);
    await expect(page.getByLabel('Bio')).toHaveAttribute('aria-invalid', 'true');
    await page.getByTestId('error-summary').getByRole('link', { name: 'A bio tem no máximo 150 caracteres.' }).click();
    await expect(page.getByLabel('Bio')).toBeFocused();
  });

  test('ligar o consentimento sensível e depois salvar os estilos; revogar apaga os estilos', async ({ page, api }) => {
    api.login();
    await page.goto('/configuracoes/perfil');
    const estilos = page.getByTestId('estilos-alimentares');
    await expect(estilos).toHaveAttribute('disabled', '');
    await expect(estilos).toContainText('Estilos alimentares podem revelar saúde ou religião (LGPD Art. 11).');
    await expect(page.getByRole('checkbox', { name: 'Vegano' })).toBeDisabled();
    await estilos.getByRole('link', { name: 'Privacidade' }).click();

    await expect(page).toHaveURL(/\/configuracoes\/privacidade#consentimentos$/);
    const sensivel = page.getByRole('switch', { name: 'Dados sensíveis do perfil' });
    await expect(sensivel).toHaveAttribute('aria-checked', 'false');
    await sensivel.click();
    await expect(sensivel).toHaveAttribute('aria-checked', 'true');
    await expect(page.getByRole('status').filter({ hasText: 'Dados sensíveis do perfil: ligado.' })).toBeVisible();
    expect(api.last('PUT', /\/me\/consents\/SENSITIVE_PROFILE_DATA$/)!.body).toEqual({ granted: true });
    await expect(sensivel).toHaveAccessibleDescription(/Desligar apaga os estilos alimentares/);

    await page.getByRole('navigation', { name: 'Seções das configurações' }).getByRole('link', { name: 'Perfil' }).click();
    await expect(page.getByRole('checkbox', { name: 'Vegano' })).toBeEnabled();
    await page.getByRole('checkbox', { name: 'Vegano' }).check();
    await page.getByRole('checkbox', { name: 'Sem glúten' }).check();
    await page.getByRole('button', { name: 'Salvar perfil' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Perfil salvo.' })).toBeVisible();
    expect(api.last('PATCH', /\/me\/profile$/)!.body).toEqual({ dietaryStyles: ['VEGANO', 'SEM_GLUTEN'] });
    expect(api.account.profile.dietaryStyles).toEqual(['VEGANO', 'SEM_GLUTEN']);

    await page.goto('/configuracoes/privacidade');
    await page.getByRole('switch', { name: 'Dados sensíveis do perfil' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Os estilos alimentares do seu perfil foram apagados.' })).toBeVisible();
    await page.getByRole('navigation', { name: 'Seções das configurações' }).getByRole('link', { name: 'Perfil' }).click();
    await expect(page.getByRole('checkbox', { name: 'Vegano' })).not.toBeChecked();
    await expect(page.getByRole('checkbox', { name: 'Vegano' })).toBeDisabled();
  });
});

test.describe('Exibição (RF2)', () => {
  test('tema ligado ao app, fonte e notificações', async ({ page, api }) => {
    api.login();
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/configuracoes/exibicao');
    await page.getByRole('radio', { name: 'Escuro' }).check();
    await page.getByLabel('Tamanho do texto').fill('120');
    await expect(page.getByTestId('tamanho-fonte')).toHaveText('120%');
    await page.getByRole('radio', { name: 'Explorar' }).check();
    await page.getByLabel('Alertas de comida').uncheck();
    await page.getByRole('button', { name: 'Salvar exibição' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Exibição salva.' })).toBeVisible();
    expect(api.last('PATCH', /\/me\/preferences$/)!.body).toMatchObject({
      theme: 'DARK',
      fontScale: 120,
      homeTab: 'EXPLORE',
      notifications: { likes: true, comments: true, shares: true, foodAlerts: false },
    });
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    // o tamanho do texto vale no app inteiro, também depois de recarregar
    await expect(page.locator('html')).toHaveAttribute('style', /font-size: 120%/);
    await expect(page.getByTestId('alternar-tema')).toHaveAttribute('aria-pressed', 'true');
    expect(await page.evaluate(() => localStorage.getItem('fe:tema'))).toBe('dark');

    await page.getByRole('radio', { name: 'Sistema' }).check();
    await page.getByRole('button', { name: 'Salvar exibição' }).click();
    await expect(page.locator('html')).not.toHaveAttribute('data-theme');
    expect(await page.evaluate(() => localStorage.getItem('fe:tema'))).toBeNull();
  });
});

test.describe('Menor de 16 (RF2, RF3)', () => {
  test('proteções travadas e o aviso de que só o responsável altera', async ({ page, api }) => {
    api.login('comensal', { minor: true });
    await page.goto('/configuracoes/privacidade');
    await expect(page.getByTestId('aviso-menor')).toContainText('só o responsável pode alterá-las');
    await expect(page.getByLabel('Perfil privado')).toBeChecked();
    await expect(page.getByLabel('Perfil privado')).toBeDisabled();
    await expect(page.getByRole('radio', { name: /Todo mundo/ })).toBeDisabled();
    await expect(page.getByRole('radio', { name: /Mostrar tudo/ })).toBeDisabled();
    const local = page.getByRole('switch', { name: 'Salvar minha localização' });
    await expect(local).toBeDisabled();
    await expect(local).toHaveAccessibleDescription(/Disponível a partir dos 16 anos/);
    await expect(page.getByRole('switch', { name: 'Dados sensíveis do perfil' })).toHaveAccessibleDescription(/Disponível a partir dos 18 anos/);
    await expect(page.getByRole('switch', { name: 'Compartilhar com parceiros' })).toBeEnabled();
    await expectAccessibleInBothThemes(page, 'Privacidade (menor)');

    await page.goto('/configuracoes/exibicao');
    await expect(page.getByTestId('aviso-menor')).toBeVisible();
    await expect(page.getByLabel('Lembrete de tempo de uso')).toBeDisabled();
    await expect(page.getByLabel('Lembrete de tempo de uso')).toHaveAccessibleDescription(/só o responsável altera/);

    await page.goto('/configuracoes/perfil');
    await expect(page.getByTestId('estilos-alimentares')).toContainText('Disponível a partir dos 18 anos');
  });
});

test.describe('Senha e segurança (RF4)', () => {
  test('ativar o 2FA de ponta a ponta: senha, QR, código e códigos de recuperação uma vez', async ({ page, api, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    api.login();
    await page.goto('/configuracoes/seguranca');
    const painel = page.getByTestId('painel-2fa');
    await expect(painel.getByTestId('status-2fa')).toHaveText('Desativada');
    await painel.getByRole('button', { name: 'Ativar' }).click();
    await expect(painel.getByRole('heading', { name: 'Ativar a verificação em duas etapas' })).toBeFocused();

    await painel.getByLabel('Senha atual').fill('errada');
    await painel.getByRole('button', { name: 'Continuar' }).click();
    await expect(painel.getByLabel('Senha atual')).toHaveAccessibleDescription('Senha incorreta.');
    await painel.getByLabel('Senha atual').fill(SENHA_CERTA);
    await painel.getByRole('button', { name: 'Continuar' }).click();
    expect(api.last('POST', /\/mfa\/totp$/)!.body).toEqual({ password: SENHA_CERTA, code: null });

    await expect(painel.getByRole('heading', { name: 'Leia o QR code no app autenticador' })).toBeFocused();
    await expect(painel.getByRole('img', { name: /QR code para adicionar Friendly Eats \(julia@example\.com\)/ })).toBeVisible();
    await expect(painel.getByTestId('chave-2fa')).toHaveText('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP');
    await painel.getByLabel('Código de 6 dígitos').fill('000000');
    await painel.getByRole('button', { name: 'Confirmar e ativar' }).click();
    await expect(painel.getByLabel('Código de 6 dígitos')).toHaveAccessibleDescription(/Código incorreto/);
    await painel.getByLabel('Código de 6 dígitos').fill(CODIGO_2FA);
    await painel.getByRole('button', { name: 'Confirmar e ativar' }).click();

    await expect(painel.getByRole('heading', { name: 'Seus códigos de recuperação' })).toBeFocused();
    const codigos = painel.getByRole('list', { name: 'Códigos de recuperação' }).getByRole('listitem');
    await expect(codigos).toHaveCount(10);
    await expectAccessibleInBothThemes(page, 'Códigos de recuperação');
    await painel.getByRole('button', { name: 'Copiar códigos' }).click();
    await expect(painel.getByRole('status').filter({ hasText: 'Códigos copiados.' })).toBeVisible();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toContain(api.account.mfa.codes[0]!);
    const [download] = await Promise.all([page.waitForEvent('download'), painel.getByRole('button', { name: 'Baixar .txt' }).click()]);
    expect(download.suggestedFilename()).toBe('friendly-eats-codigos-de-recuperacao.txt');
    await expect(painel.getByRole('button', { name: 'Concluir' })).toBeDisabled();
    await painel.getByLabel('Guardei os códigos em lugar seguro').check();
    await painel.getByRole('button', { name: 'Concluir' }).click();

    await expect(painel.getByTestId('status-2fa')).toHaveText('Ativada');
    await expect(painel).toContainText('10 de 10 códigos de recuperação');
    await expect(painel.getByRole('list', { name: 'Códigos de recuperação' })).toHaveCount(0);
    // com 2FA, trocar a senha passa a pedir o código
    await expect(page.getByRole('form', { name: 'Trocar a senha' }).getByLabel('Código do app')).toBeVisible();

    await painel.getByRole('button', { name: 'Desativar' }).click();
    await painel.getByLabel('Senha atual').fill(SENHA_CERTA);
    await painel.getByLabel('Código do app').fill(CODIGO_2FA);
    await painel.getByRole('button', { name: 'Desativar o 2FA' }).click();
    await expect(painel.getByRole('status').filter({ hasText: 'Verificação em duas etapas desativada.' })).toBeVisible();
    await expect(painel.getByTestId('status-2fa')).toHaveText('Desativada');
    expect(api.last('POST', /\/mfa\/disable$/)!.body).toEqual({ password: SENHA_CERTA, code: CODIGO_2FA });
  });

  test('gerar novos códigos pede senha e código', async ({ page, api }) => {
    api.login('comensal', { mfaEnabled: true });
    await page.goto('/configuracoes/seguranca');
    const painel = page.getByTestId('painel-2fa');
    await expect(painel).toContainText('1 de 10 códigos de recuperação');
    await expect(painel).toContainText('Restam poucos códigos');
    await painel.getByRole('button', { name: 'Gerar novos códigos' }).click();
    await painel.getByLabel('Senha atual').fill(SENHA_CERTA);
    await painel.getByRole('button', { name: 'Gerar novos códigos' }).click();
    await expect(painel.getByLabel('Código do app')).toHaveAccessibleDescription(/Digite o código de 6 dígitos/);
    await painel.getByLabel('Código do app').fill(CODIGO_2FA);
    await painel.getByRole('button', { name: 'Gerar novos códigos' }).click();
    await expect(painel.getByRole('list', { name: 'Códigos de recuperação' }).getByRole('listitem')).toHaveCount(10);
  });

  test('trocar a senha, encerrar uma sessão e sair de todos os outros aparelhos', async ({ page, api }) => {
    api.login();
    await page.goto('/configuracoes/seguranca');
    const sessoes = page.getByTestId('lista-sessoes').getByRole('listitem');
    await expect(sessoes).toHaveCount(3);
    await expect(sessoes.first()).toContainText('Esta sessão');
    await expect(sessoes.first().getByRole('button')).toHaveCount(0);
    await page.getByRole('button', { name: 'Sair de Safari no iOS' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Sessão encerrada: Safari no iOS.' })).toBeVisible();
    await expect(sessoes).toHaveCount(2);
    expect(api.find('DELETE', /\/me\/sessions\/0f1e2d3c4b5a69788796a5b4$/)).toHaveLength(1);
    await page.getByRole('button', { name: 'Sair de todos os outros aparelhos' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Você saiu de 1 outro aparelho.' })).toBeVisible();
    await expect(sessoes).toHaveCount(1);

    const form = page.getByRole('form', { name: 'Trocar a senha' });
    await form.getByLabel('Senha atual').fill(SENHA_CERTA);
    await form.getByLabel(/^Senha nova/).fill('curta');
    await form.getByLabel('Repita a senha nova').fill('outra');
    await form.getByRole('button', { name: 'Trocar a senha' }).click();
    await expect(form.getByLabel(/^Senha nova/)).toHaveAccessibleDescription(/pelo menos 12 caracteres/);
    await expect(form.getByLabel('Repita a senha nova')).toHaveAccessibleDescription('A confirmação não é igual à senha nova.');
    await form.getByLabel(/^Senha nova/).fill('uma frase nova e comprida');
    await form.getByLabel('Repita a senha nova').fill('uma frase nova e comprida');
    await form.getByRole('button', { name: 'Trocar a senha' }).click();
    await expect(form.getByRole('status')).toHaveText('Senha trocada.');
    expect(api.last('POST', /\/me\/password$/)!.body).toEqual({ currentPassword: SENHA_CERTA, code: null, newPassword: 'uma frase nova e comprida' });
  });

  test('alertas de login: interruptor salva na hora', async ({ page, api }) => {
    api.login();
    await page.goto('/configuracoes/seguranca');
    const chave = page.getByRole('switch', { name: 'Alertas de login' });
    await expect(chave).toHaveAttribute('aria-checked', 'true');
    await chave.click();
    await expect(chave).toHaveAttribute('aria-checked', 'false');
    expect(api.last('PATCH', /\/me\/preferences$/)!.body).toEqual({ loginAlerts: false });
  });
});

test.describe('Excluir conta (RF5)', () => {
  const dataFinal = () =>
    new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'America/Sao_Paulo' }).format(
      new Date(Date.now() + 30 * 86_400_000),
    );

  test('alertdialog com consequências, data final, "Entendo" e senha; depois sai da conta', async ({ page, api }) => {
    api.login();
    await page.goto('/configuracoes/conta');
    const abrir = page.getByRole('button', { name: 'Excluir minha conta…' });
    await abrir.click();
    const dialogo = page.getByRole('alertdialog', { name: 'Excluir sua conta do Friendly Eats?' });
    await expect(dialogo).toBeVisible();
    await expect(dialogo).toHaveAccessibleDescription(new RegExp(`Sua conta será excluída em 30 dias \\(em ${dataFinal()}\\)`));
    await expect(dialogo.getByRole('button', { name: 'Cancelar' })).toBeFocused();
    await expect(dialogo).toContainText('Depois do prazo, vamos apagar');
    await expect(dialogo).toContainText('registros de acesso (data, hora e IP) por 6 meses');
    await expect(dialogo.getByLabel('Código do app')).toHaveCount(0);

    // foco preso: dar a volta com Tab não sai do diálogo
    for (let i = 0; i < 8; i++) {
      await page.keyboard.press('Tab');
      expect(await dialogo.evaluate((d) => d.contains(document.activeElement))).toBe(true);
    }
    await expectAccessibleInBothThemes(page, 'Excluir conta (diálogo)');
    await page.keyboard.press('Escape');
    await expect(dialogo).toBeHidden();
    await expect(abrir).toBeFocused();

    await abrir.click();
    await dialogo.getByRole('button', { name: 'Excluir minha conta' }).click();
    await expect(dialogo.getByLabel(/Entendo que, passados 30 dias/)).toHaveAccessibleDescription('Marque a caixa para confirmar que entendeu.');
    await expect(dialogo.getByLabel('Senha')).toHaveAccessibleDescription('Digite a sua senha atual.');
    await dialogo.getByLabel(/Entendo que, passados 30 dias/).check();
    await dialogo.getByLabel('Senha').fill('errada');
    await dialogo.getByRole('button', { name: 'Excluir minha conta' }).click();
    await expect(dialogo.getByLabel('Senha')).toHaveAccessibleDescription('Senha incorreta.');
    await dialogo.getByLabel('Senha').fill(SENHA_CERTA);
    await dialogo.getByRole('button', { name: 'Excluir minha conta' }).click();

    const feito = page.getByRole('alertdialog', { name: 'Pedido de exclusão registrado' });
    await expect(feito).toContainText(`A sua conta será excluída em ${dataFinal()}`);
    await expect(feito.getByRole('button', { name: 'Ir para o início' })).toBeFocused();
    expect(api.last('POST', /\/me\/deletion$/)!.body).toEqual({ password: SENHA_CERTA, code: null, confirm: true });
    await feito.getByRole('button', { name: 'Ir para o início' }).click();
    await expect(page).toHaveURL(/127\.0\.0\.1:\d+\/$/);
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();
  });

  test('com 2FA ativo pede também o código do app', async ({ page, api }) => {
    api.login('comensal', { mfaEnabled: true });
    await page.goto('/configuracoes/conta');
    await page.getByRole('button', { name: 'Excluir minha conta…' }).click();
    const dialogo = page.getByRole('alertdialog');
    await dialogo.getByLabel(/Entendo que/).check();
    await dialogo.getByLabel('Senha').fill(SENHA_CERTA);
    await dialogo.getByLabel('Código do app').fill('111111');
    await dialogo.getByRole('button', { name: 'Excluir minha conta' }).click();
    await expect(dialogo.getByLabel('Código do app')).toHaveAccessibleDescription(/Senha ou código do app autenticador incorretos\./);
    await dialogo.getByLabel('Código do app').fill(CODIGO_2FA);
    await dialogo.getByRole('button', { name: 'Excluir minha conta' }).click();
    await expect(page.getByRole('alertdialog', { name: 'Pedido de exclusão registrado' })).toBeVisible();
    expect(api.last('POST', /\/me\/deletion$/)!.body).toEqual({ password: SENHA_CERTA, code: CODIGO_2FA, confirm: true });
  });
});

test('fixtures das Configurações seguem os records Java', () => {
  const perfil = fixture<Record<string, unknown>>('perfil-comensal');
  expect(Object.keys(perfil).sort()).toEqual(
    [
      'id', 'name', 'handle', 'email', 'recoveryEmail', 'cellphoneNumber', 'dateOfBirth', 'bio', 'pronouns', 'links', 'countryOfResidence',
      'stateOfResidence', 'cityOfResidence', 'category', 'categoryVerification', 'typeDetails', 'avatarObjectKey', 'avatarUrl',
      'favoriteCuisines', 'dietaryStyles',
    ].sort(),
  );
  const prefs = fixture<Record<string, unknown>>('preferencias');
  expect(Object.keys(prefs).sort()).toEqual(
    [
      'theme', 'appBackground', 'language', 'fontScale', 'reduceMotion', 'profilePrivate', 'whoCanComment', 'showBirthYear', 'homeTab',
      'sensitiveContent', 'usageReminderMinutes', 'notifications', 'loginAlerts', 'minorProtectionsLocked',
    ].sort(),
  );
  const consent = fixture<{ items: Array<Record<string, unknown>> }>('consentimentos').items;
  expect(consent.map((c) => c.purpose).sort()).toEqual(['LOCATION_TRACKING', 'MARKETING', 'PERSONALIZED_FEED', 'SENSITIVE_PROFILE_DATA', 'THIRD_PARTY_SHARING']);
  expect(consent.every((c) => c.granted === false)).toBe(true);
  expect(Object.keys(consent[0]!).sort()).toEqual(['purpose', 'label', 'description', 'legalBasis', 'sensitive', 'granted', 'updatedAt', 'lockedForMinor'].sort());
});

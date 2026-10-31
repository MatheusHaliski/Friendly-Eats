import { expect, expectAccessibleInBothThemes, test } from './support/test';

const POST = '0199a1b2-c3d4-7e5f-8a9b-000000000001';
const POST_RAFAEL = '0199a1b2-c3d4-7e5f-8a9b-000000000003';

test.describe('Denunciar (RF13)', () => {
  test('post: menu "…", motivo fechado, recibo com protocolo e "Minhas denúncias"', async ({ page, api }) => {
    api.login();
    await page.goto(`/posts/${POST_RAFAEL}`);
    await page.getByRole('button', { name: /^Mais ações: o post/ }).click();
    await page.getByRole('button', { name: 'Denunciar' }).click();
    const dialog = page.getByRole('dialog', { name: /Denunciar o post/ });
    await expect(dialog).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Diálogo de denúncia');
    // sem motivo: erro escrito
    await dialog.getByRole('button', { name: 'Enviar denúncia' }).click();
    await expect(dialog.getByText('Escolha um motivo.')).toBeVisible();
    // motivos do tipo do alvo (conflito de interesse vale para post; dado pessoal também)
    await dialog.getByRole('radio', { name: /Assédio ou cyberbullying/ }).check();
    await dialog.getByLabel(/Conte mais/).fill('Ataca uma pessoa nos comentários.');
    await dialog.getByRole('button', { name: 'Enviar denúncia' }).click();
    await expect(dialog.getByText(/Protocolo DEN-2026-/)).toBeVisible();
    const sent = api.last('POST', /\/api\/v1\/reports$/);
    expect(sent?.body).toEqual({ targetType: 'POST', targetId: POST_RAFAEL, reason: 'ASSEDIO', details: 'Ataca uma pessoa nos comentários.' });
    await dialog.getByRole('button', { name: 'Fechar' }).click();
    await expect(dialog).toBeHidden();
    // o foco volta para o botão "…"
    await expect(page.getByRole('button', { name: /^Mais ações: o post/ })).toBeFocused();

    await page.goto('/configuracoes/denuncias');
    await expect(page.getByTestId('minha-denuncia').first()).toContainText('Recebida, em análise');
  });

  test('comentário: criança em risco pede a marcação de abuso e mostra os canais de emergência', async ({ page, api }) => {
    api.login();
    await page.goto(`/posts/${POST}`);
    await page.getByRole('button', { name: 'Mais ações: o comentário de Bruno Costa' }).click();
    await page.getByRole('button', { name: 'Denunciar' }).click();
    const dialog = page.getByRole('dialog', { name: 'Denunciar o comentário de Bruno Costa' });
    await dialog.getByRole('radio', { name: /Criança ou adolescente em risco/ }).check();
    await expect(dialog.getByText(/ligue 190/)).toBeVisible();
    await dialog.getByRole('checkbox', { name: /exploração ou abuso sexual/ }).check();
    await dialog.getByRole('button', { name: 'Enviar denúncia' }).click();
    await expect(dialog.getByText(/saiu do ar na hora/)).toBeVisible();
    expect(api.last('POST', /\/api\/v1\/reports$/)?.body).toMatchObject({ targetType: 'COMMENT', targetId: '11', childSexualAbuse: true });
  });

  test('sem conta: o diálogo leva ao formulário público, que exige nome e e-mail', async ({ page, api }) => {
    await page.goto(`/posts/${POST_RAFAEL}`);
    await page.getByRole('button', { name: /^Mais ações: o post/ }).click();
    await page.getByRole('button', { name: 'Denunciar' }).click();
    await page.getByRole('link', { name: 'Denunciar sem conta' }).click();
    await expect(page).toHaveURL(/\/denunciar\?tipo=POST&id=/);
    await expect(page.getByRole('heading', { level: 1, name: 'Denunciar sem conta' })).toBeVisible();
    await page.getByRole('button', { name: 'Enviar denúncia' }).click();
    await expect(page.getByTestId('error-summary')).toContainText('não pode ser anônima');
    await page.getByLabel(/Seu nome/).fill('Maria Visitante');
    await page.getByLabel(/Seu e-mail/).fill('maria@example.com');
    await page.getByRole('radio', { name: /Golpe ou fraude/ }).check();
    await expectAccessibleInBothThemes(page, 'Denunciar sem conta');
    await page.getByRole('button', { name: 'Enviar denúncia' }).click();
    await expect(page.getByText(/Protocolo DEN-2026-/)).toBeVisible();
    expect(api.last('POST', /\/reports\/public$/)?.body).toMatchObject({ name: 'Maria Visitante', email: 'maria@example.com', targetType: 'POST', reason: 'GOLPE_FRAUDE' });
  });

  test('/seguranca: política contra intimidação, apoio e denúncia sem conta', async ({ page }) => {
    await page.goto('/seguranca');
    await expect(page.getByRole('heading', { level: 1, name: 'Segurança e denúncias' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Sofreu intimidação?' })).toBeVisible();
    await expect(page.getByText(/ligue 188/)).toBeVisible();
    await expect(page.getByRole('link', { name: 'Denunciar sem conta' })).toHaveAttribute('href', '/denunciar');
    await expect(page.getByRole('contentinfo').getByRole('link', { name: 'Segurança e denúncias' })).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Segurança e denúncias');
  });
});

test.describe('Integridade das avaliações (RF13)', () => {
  test('alerta ao consumidor e congelamento na página do lugar', async ({ page, api }) => {
    api.moderation.freeze('1');
    api.login();
    await page.goto('/lugares/1');
    await expect(page.getByTestId('alerta-consumidor')).toContainText('Alerta ao consumidor: Atividade fora do padrão');
    await expect(page.getByTestId('aviso-congelamento')).toContainText('Novas avaliações pausadas até');
    await expect(page.getByTestId('avaliar-congelado')).toBeVisible();
    await expect(page.getByRole('form', { name: 'Avaliar este lugar' })).toHaveCount(0);
    await page.getByText('Como verificamos as avaliações').click();
    await expectAccessibleInBothThemes(page, 'Lugar com alerta ao consumidor');
  });

  test('o dono contesta uma avaliação só pelos motivos da política', async ({ page, api }) => {
    api.login('estabelecimento');
    await page.goto('/lugares/1');
    await page.getByRole('button', { name: 'Mais ações: a avaliação de Ana Lima' }).click();
    await expect(page.getByRole('button', { name: 'Denunciar' })).toHaveCount(0);
    await page.getByRole('button', { name: 'Contestar avaliação' }).click();
    const dialog = page.getByRole('dialog', { name: 'Contestar a avaliação de Ana Lima' });
    await expect(dialog.getByText('Discordar da nota não é motivo de contestação.', { exact: false })).toBeVisible();
    await expect(dialog.getByRole('radio', { name: /Spam/ })).toHaveCount(0);
    await dialog.getByRole('radio', { name: /Avaliação falsa ou com incentivo/ }).check();
    await dialog.getByLabel(/Explique/).fill('Nesse dia estávamos fechados para reforma.');
    await expectAccessibleInBothThemes(page, 'Contestar avaliação');
    await dialog.getByRole('button', { name: 'Enviar contestação' }).click();
    await expect(dialog.getByText(/A avaliação continua no ar até a decisão/)).toBeVisible();
    expect(api.last('POST', /\/reviews\/11\/contest$/)?.body).toEqual({ reason: 'AVALIACAO_FALSA', details: 'Nesse dia estávamos fechados para reforma.' });
  });

  test('quem avalia declara o vínculo com o lugar', async ({ page, api }) => {
    api.login();
    await page.goto('/lugares/2');
    const form = page.getByRole('form', { name: 'Avaliar este lugar' });
    await form.getByRole('radio', { name: '4 – Bom' }).check();
    await form.getByRole('radio', { name: 'No local' }).check();
    await form.getByRole('radio', { name: 'Recebi cortesia' }).check();
    await form.getByRole('button', { name: 'Publicar avaliação' }).click();
    await expect(page.getByText('Sua avaliação foi publicada.')).toBeVisible();
    expect((api.last('PUT', /\/reviews\/me$/)?.body as { disclosure?: string }).disclosure).toBe('COURTESY');
  });
});

test.describe('Recurso do autor (RF13)', () => {
  test('a decisão traz motivo, origem e prazo; "Contestar decisão" registra o recurso', async ({ page, api }) => {
    api.login();
    api.moderation.myDecisions = [
      {
        caseId: 3,
        protocol: 'CASO-000003',
        target: { type: 'POST', typeLabel: 'Post', id: POST, path: null, excerpt: 'PF caprichado no Sabor Mineiro' },
        decision: {
          id: 1,
          stage: 'FIRST',
          action: { code: 'HIDE', label: 'Ocultar', group: null },
          policy: { code: 'SPAM', label: 'Spam', group: null },
          statement: 'O post repete a mesma propaganda várias vezes.',
          automated: false,
          origin: 'Decisão de uma pessoa da moderação',
          moderatorId: null,
          createdAt: '2026-10-30T12:00:00Z',
        },
        canAppeal: true,
        appealDeadline: '2026-11-29T12:00:00Z',
        appealResponseDays: 7,
        howToAppeal: 'Recorra em até 30 dias: outra pessoa da moderação revê a decisão e responde em até 7 dias.',
        appeal: null,
        appealResult: null,
      },
    ];
    await page.goto('/configuracoes/denuncias');
    const card = page.getByTestId('decisao');
    await expect(card).toContainText('Ocultar · Spam');
    await expect(card).toContainText('Decisão de uma pessoa da moderação');
    await card.getByRole('button', { name: 'Contestar decisão' }).click();
    const dialog = page.getByRole('dialog', { name: /Contestar decisão/ });
    await dialog.getByLabel(/Seu recurso/).fill('Foi uma única publicação, sem repetição.');
    await expectAccessibleInBothThemes(page, 'Contestar decisão');
    await dialog.getByRole('button', { name: 'Enviar recurso' }).click();
    await expect(page.getByTestId('status-recurso')).toContainText('Recurso registrado no caso CASO-000003');
    await expect(card).toContainText('Recurso: em análise');
  });
});

test.describe('Área da moderação (RF13)', () => {
  test('sem o papel: aviso, sem link no cabeçalho', async ({ page, api }) => {
    api.login();
    await page.goto('/moderacao');
    await expect(page.getByText('Só a equipe de moderação acessa esta área.', { exact: false })).toBeVisible();
    await expect(page.getByTestId('link-moderacao')).toHaveCount(0);
  });

  test('fila por prioridade, detalhe, decisão motivada, autoridades e relatório', async ({ page, api }) => {
    api.login('comensal', { id: 77, name: 'Moderação Um', handle: 'moderacao' });
    api.moderation.moderator = true;
    api.moderation.seedCases();
    await page.goto('/feed');
    await page.getByTestId('link-moderacao').click();
    await expect(page.getByRole('heading', { level: 1, name: 'Moderação' })).toBeVisible();
    const fila = page.getByTestId('fila');
    await expect(fila.getByRole('button').first()).toContainText('Criança ou adolescente em risco');
    await fila.getByRole('button', { name: /CASO-000002/ }).click();
    const caso = page.getByTestId('caso');
    await expect(caso.getByRole('heading', { name: 'CASO-000002 · Assédio ou cyberbullying' })).toBeVisible();
    await expectAccessibleInBothThemes(page, 'Moderação: detalhe do caso');
    await caso.getByRole('radio', { name: 'Ocultar', exact: true }).check();
    await caso.getByRole('button', { name: 'Registrar decisão' }).click();
    await expect(caso.getByText(/pelo menos 20 caracteres/)).toBeVisible();
    await caso.getByLabel(/Fundamentação/).fill('O comentário humilha uma pessoa identificada.');
    await caso.getByRole('button', { name: 'Registrar decisão' }).click();
    await expect(caso.getByTestId('historico')).toContainText('Decisão · Ocultar · Assédio ou cyberbullying');
    expect(api.last('POST', /\/cases\/2\/decision$/)?.body).toEqual({
      action: 'HIDE',
      policyReason: 'ASSEDIO',
      statement: 'O comentário humilha uma pessoa identificada.',
    });

    await fila.getByRole('button', { name: /CASO-000001/ }).click();
    await caso.getByLabel('Protocolo ou referência').fill('PF-CNT 2026/123');
    await caso.getByRole('button', { name: 'Registrar comunicação' }).click();
    await expect(caso.getByText(/Comunicado às autoridades em/)).toBeVisible();

    await page.getByRole('tab', { name: 'Relatório' }).click();
    await expect(page.getByTestId('relatorio')).toContainText('Assédio ou cyberbullying');
    await expectAccessibleInBothThemes(page, 'Moderação: relatório');
  });
});

test.describe('Segurança entre usuários (RF19)', () => {
  test('perfil: bloquear com confirmação, silenciar e desfazer', async ({ page, api }) => {
    api.login();
    await page.goto('/u/310');
    const menu = page.getByRole('button', { name: 'Mais ações: o perfil de Rafael Lima' });
    await menu.click();
    await page.getByRole('button', { name: 'Silenciar' }).click();
    await expect(page.getByTestId('status-relacao')).toContainText('Rafael Lima foi silenciado');
    await menu.click();
    await page.getByRole('button', { name: 'Bloquear' }).click();
    const confirm = page.getByRole('alertdialog', { name: 'Bloquear Rafael Lima?' });
    await expect(confirm.getByRole('button', { name: 'Cancelar' })).toBeFocused();
    await expectAccessibleInBothThemes(page, 'Confirmar bloqueio');
    await confirm.getByRole('button', { name: 'Bloquear' }).click();
    await expect(page.getByTestId('status-relacao')).toContainText('Rafael Lima foi bloqueado');
    expect(api.find('PUT', /\/me\/blocks\/310$/)).toHaveLength(1);
    expect(api.find('PUT', /\/me\/mutes\/310$/)).toHaveLength(1);
    await menu.click();
    await expect(page.getByRole('button', { name: 'Desbloquear' })).toBeVisible();
  });

  test('Configurações → Privacidade: listas, filtro de ofensas e palavras', async ({ page, api }) => {
    api.login();
    api.moderation.safety = {
      ...api.moderation.safety,
      blocked: [
        {
          user: { id: 205, name: 'Bruno Costa', handle: 'brunocosta', category: null, verified: false, avatarUrl: null, profilePath: '/api/v1/users/205/profile', available: true },
          since: '2026-10-20T12:00:00Z',
        },
      ],
    };
    await page.goto('/configuracoes/privacidade');
    const area = page.getByTestId('seguranca-usuarios');
    await expect(area.getByRole('heading', { name: 'Bloqueados, silenciados e palavras' })).toBeVisible();
    await expect(area.getByRole('link', { name: 'Bruno Costa' })).toBeVisible();
    await area.getByRole('button', { name: 'Desbloquear Bruno Costa' }).click();
    await expect(area.getByText('Bruno Costa foi desbloqueada.')).toBeVisible();
    expect(api.find('DELETE', /\/me\/blocks\/205$/)).toHaveLength(1);

    // o interruptor só muda depois da resposta da API
    await area.getByLabel(/Filtro de ofensas/).click();
    await expect(area.getByText('Filtro de ofensas ligado.')).toBeVisible();
    await expect(area.getByLabel(/Filtro de ofensas/)).toBeChecked();
    await area.getByLabel('Suas palavras').fill('Idiota\nlixo, x');
    await area.getByRole('button', { name: 'Salvar palavras' }).click();
    await expect(area.getByText(/"x": cada palavra/)).toBeVisible();
    await area.getByLabel('Suas palavras').fill('Idiota\nlixo');
    await area.getByRole('button', { name: 'Salvar palavras' }).click();
    await expect(area.getByText('Palavras filtradas salvas (2).')).toBeVisible();
    expect(api.last('PUT', /\/me\/filtered-words$/)?.body).toEqual({ words: ['Idiota', 'lixo'] });
  });

  test('menor de 16: filtro de ofensas travado ligado', async ({ page, api }) => {
    api.login('comensal', { minor: true });
    api.moderation.safety = { ...api.moderation.safety, defaultFilter: { enabled: true, locked: true, size: 18 } };
    await page.goto('/configuracoes/privacidade');
    const filtro = page.getByTestId('seguranca-usuarios').getByLabel(/Filtro de ofensas/);
    await expect(filtro).toBeChecked();
    await expect(filtro).toBeDisabled();
  });
});

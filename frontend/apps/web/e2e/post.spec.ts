import { expect, expectAccessibleInBothThemes, test } from './support/test';

const PF = '/posts/0199a1b2-c3d4-7e5f-8a9b-000000000001';
const RECEITA = '/posts/0199a1b2-c3d4-7e5f-8a9b-000000000003';
const CHOPE = '/posts/0199a1b2-c3d4-7e5f-8a9b-000000000007';
const ALERTA = '/posts/0199a1b2-c3d4-7e5f-8a9b-000000000020';

test.describe('Página do post (RF14) e interações (RF12)', () => {
  test('detalhe com moldura, alergênicos com o aviso e autor que leva ao perfil', async ({ page }) => {
    await page.goto(PF);
    await expect(page.getByRole('heading', { level: 1, name: 'PF caprichado no Sabor Mineiro' })).toBeVisible();
    await expect(page).toHaveTitle('PF caprichado no Sabor Mineiro · Friendly Eats');
    const post = page.getByTestId('post');
    await expect(page.getByTestId('post-moldura')).toHaveAttribute('data-art', 'AURA');
    await expect(post.getByTestId('alergenicos')).toContainText('Contém: Ovos');
    await expect(post.getByTestId('alergenicos')).toContainText('Informação declarada por quem publicou');
    await expect(post.getByRole('link', { name: 'Sabor Mineiro' })).toHaveAttribute('href', '/lugares/1');
    await post.getByRole('link', { name: 'Júlia Andrade' }).click();
    await expect(page).toHaveURL(/\/u\/42$/);
  });

  test('receita no layout Cardápio: ingredientes, passos, rendimento', async ({ page }) => {
    await page.goto(RECEITA);
    const receita = page.getByTestId('receita');
    await expect(receita).toContainText('Rende 30 porções · Preparo: 60 min');
    await expect(receita.getByRole('list').first().getByRole('listitem')).toHaveCount(6);
    await expect(receita.getByRole('list').last().getByRole('listitem')).toHaveCount(5);
    await expect(page.getByTestId('post-moldura')).toHaveAttribute('data-layout', 'CARDAPIO');
  });

  test('curtir, comentar, compartilhar e salvar, com aria-live', async ({ page, api, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    api.login();
    await page.goto(PF);
    const curtir = page.getByTestId('curtir');
    await expect(curtir).toHaveAccessibleName(/Curtir.*12 curtidas/);
    await expect(curtir).toHaveAttribute('aria-pressed', 'false');
    await curtir.click();
    await expect(curtir).toHaveAttribute('aria-pressed', 'true');
    await expect(curtir).toHaveAccessibleName(/Curtido.*13 curtidas/);
    await expect(page.getByTestId('status-interacao')).toHaveText('Você curtiu. 13 curtidas.');
    const put = api.last('PUT', /\/like$/)!;
    expect(put.headers['x-xsrf-token']).toBe(api.csrfToken);
    await curtir.click();
    await expect(page.getByTestId('status-interacao')).toHaveText('Curtida desfeita. 12 curtidas.');
    expect(api.find('DELETE', /\/like$/)).toHaveLength(1);

    await page.getByTestId('salvar').click();
    await expect(page.getByTestId('salvar')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('status-interacao')).toContainText('Post salvo');

    // comentários: lista, enviar e apagar o próprio
    const comentarios = page.getByTestId('comentarios');
    await expect(comentarios.getByTestId('comentario')).toHaveCount(3);
    await comentarios.getByLabel('Seu comentário').fill('Vou amanhã cedo!');
    await comentarios.getByRole('button', { name: 'Enviar comentário' }).click();
    await expect(page.getByTestId('status-comentarios')).toHaveText('Comentário publicado. 4 comentários.');
    await expect(comentarios.getByTestId('comentario')).toHaveCount(4);
    expect(api.last('POST', /\/comments$/)?.body).toEqual({ body: 'Vou amanhã cedo!' });
    await expect(page.getByTestId('ir-comentarios')).toHaveAccessibleName(/4 comentários/);
    await comentarios.getByRole('button', { name: 'Apagar o comentário de Júlia Andrade' }).click();
    const confirma = page.getByRole('alertdialog', { name: 'Apagar o comentário?' });
    await expect(confirma.getByRole('button', { name: 'Cancelar' })).toBeFocused();
    await confirma.getByRole('button', { name: 'Sim, apagar' }).click();
    await expect(page.getByTestId('status-comentarios')).toHaveText('Comentário apagado.');
    await expect(comentarios.getByTestId('comentario')).toHaveCount(3);

    // compartilhar: canais do vocabulário, copiar link e contar uma vez por canal
    await page.getByTestId('compartilhar').click();
    const folha = page.getByTestId('folha-compartilhar');
    await expect(folha.getByRole('link', { name: /WhatsApp/ })).toHaveAttribute('href', /^https:\/\/wa\.me\/\?text=/);
    await expect(folha.getByRole('link', { name: /Telegram/ })).toHaveAttribute('href', /t\.me\/share\/url/);
    await expect(folha.getByRole('link', { name: /E-mail/ })).toHaveAttribute('href', /^mailto:/);
    await folha.getByRole('button', { name: 'Copiar link' }).click();
    await expect(page.getByTestId('status-interacao')).toHaveText('Link copiado.');
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('https://friendlyeats.app/p/0199a1b2-c3d4-7e5f-8a9b-000000000001');
    await expect.poll(() => api.last('POST', /\/shares$/)?.body).toEqual({ channel: 'COPY_LINK' });
    await expect(page.getByTestId('compartilhar')).toHaveAccessibleName(/3 compartilhamentos/);
    await expect(folha.getByLabel('Link do post')).toHaveValue(/\/p\/0199a1b2/);

    await expectAccessibleInBothThemes(page, 'Página do post (logado)');
  });

  test('visitante: curtir leva ao login e o comentário mostra o convite', async ({ page }) => {
    await page.goto(PF);
    await expect(page.getByTestId('aviso-comentarios')).toHaveText('Entre na sua conta para comentar.');
    await expect(page.getByRole('link', { name: 'Entrar para comentar' })).toHaveAttribute('href', `/entrar?voltar=${encodeURIComponent(PF)}`);
    await page.getByTestId('curtir').click();
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
  });

  test('comentar com páginas ainda por carregar não duplica: o novo chega no fim, na sua vez', async ({ page, api }) => {
    api.login();
    // a API real devolve os comentários em ordem crescente de id, de 1 em 1 aqui para haver páginas pendentes
    api.on('GET', /\/posts\/[^/]+\/comments$/, (req) => {
      const id = [...api.posts.comments.keys()].find((k) => k.endsWith('000000000001'))!;
      const all = api.posts.comments.get(id) ?? [];
      const after = Number(req.search.get('after') ?? 0);
      const items = all.filter((c) => c.id > after).slice(0, 1);
      const last = items.at(-1);
      const more = last ? all.some((c) => c.id > last.id) : false;
      return { status: 200, body: { items, nextAfter: more ? last!.id : null, total: all.length } };
    });
    await page.goto(PF);
    const comentarios = page.getByTestId('comentarios');
    await expect(comentarios.getByTestId('comentario')).toHaveCount(1);
    await comentarios.getByLabel('Seu comentário').fill('Chego no fim da fila');
    await comentarios.getByRole('button', { name: 'Enviar comentário' }).click();
    await expect(page.getByTestId('status-comentarios')).toHaveText('Comentário publicado. 4 comentários.');
    // ainda há páginas por carregar: o novo não entra fora de ordem
    await expect(comentarios.getByTestId('comentario')).toHaveCount(1);
    const mais = comentarios.getByRole('button', { name: 'Carregar mais comentários' });
    for (const n of [2, 3, 4]) {
      await mais.click();
      await expect(comentarios.getByTestId('comentario')).toHaveCount(n);
    }
    await expect(mais).toHaveCount(0);
    await expect(comentarios.getByTestId('comentario').last()).toContainText('Chego no fim da fila');
    await expect(comentarios.getByText('Chego no fim da fila')).toHaveCount(1);
  });

  test('alerta sanitário: sem curtir nem comentar, só compartilhar e salvar; sem moldura', async ({ page, api }) => {
    api.login();
    await page.goto(ALERTA);
    await expect(page.getByTestId('faixa-alerta')).toContainText('Alerta sanitário');
    await expect(page.getByTestId('post-moldura')).toHaveCount(0);
    await expect(page.getByTestId('curtir')).toHaveCount(0);
    await expect(page.getByTestId('ir-comentarios')).toHaveCount(0);
    await expect(page.getByTestId('compartilhar')).toBeVisible();
    await expect(page.getByTestId('salvar')).toBeVisible();
    await expect(page.getByTestId('aviso-alerta')).toHaveText('Alerta sanitário não recebe curtidas nem comentários: compartilhe ou salve.');
    await expect(page.getByTestId('comentarios')).toHaveCount(0);
    await expect(page.getByTestId('dados-alerta')).toContainText('Mistura com óleos vegetais');
    await expectAccessibleInBothThemes(page, 'Alerta sanitário');
  });

  test('post 18+ sem login convida a entrar; inexistente dá 404', async ({ page }) => {
    await page.goto(CHOPE);
    await expect(page.getByRole('heading', { level: 1, name: 'Conteúdo para maiores de 18 anos' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Entrar' }).last()).toHaveAttribute('href', `/entrar?voltar=${encodeURIComponent(CHOPE)}`);
    await page.goto('/posts/0199a1b2-c3d4-7e5f-8a9b-999999999999');
    await expect(page.getByRole('heading', { level: 1, name: 'Post não encontrado' })).toBeVisible();
  });

  test('autor: arquivar, restaurar e excluir com confirmação', async ({ page, api }) => {
    api.login();
    await page.goto(PF);
    const acoes = page.getByTestId('acoes-autor');
    await expect(acoes.getByRole('link', { name: 'Editar' })).toHaveAttribute('href', `${PF}/editar`);
    await acoes.getByRole('button', { name: 'Arquivar' }).click();
    await expect(acoes.getByRole('status')).toContainText('Post arquivado');
    await expect(page.getByTestId('chip-arquivado')).toBeVisible();
    await expect(page.getByTestId('curtir')).toHaveCount(0);
    await expect(page.getByTestId('aviso-comentarios')).toHaveText('Este post está arquivado e não recebe mais comentários.');
    await acoes.getByRole('button', { name: 'Restaurar' }).click();
    await expect(acoes.getByRole('status')).toContainText('Post restaurado');

    await acoes.getByRole('button', { name: 'Excluir…' }).click();
    const dialogo = page.getByRole('alertdialog', { name: 'Excluir o post?' });
    await expect(dialogo.getByRole('button', { name: 'Cancelar' })).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(dialogo).toBeHidden();
    await acoes.getByRole('button', { name: 'Excluir…' }).click();
    await dialogo.getByRole('button', { name: 'Sim, excluir' }).click();
    await expect(page).toHaveURL(/\/feed$/);
    expect(api.find('DELETE', /\/posts\/[0-9a-f-]+$/)).toHaveLength(1);
    await page.goto(PF);
    await expect(page.getByRole('heading', { level: 1, name: 'Post não encontrado' })).toBeVisible();
  });

  test('link público /p/{id} abre a página do post', async ({ page }) => {
    await page.goto('/p/0199a1b2-c3d4-7e5f-8a9b-000000000003');
    await expect(page).toHaveURL(new RegExp(`${RECEITA}$`));
    await expect(page.getByRole('heading', { level: 1, name: 'Pão de queijo de polvilho azedo' })).toBeVisible();
  });

  test('"Posts deste lugar" na página do lugar', async ({ page }) => {
    await page.goto('/lugares/1');
    const secao = page.getByRole('region', { name: 'Posts deste lugar' });
    await expect(secao.getByRole('article')).toHaveCount(2);
    await expect(secao.getByRole('article', { name: 'Hoje tem feijoada completa' })).toBeVisible();
    expect(page.url()).toContain('/lugares/1');
  });
});

import { describe, expect, it } from 'vitest';
import { mapPostErrors, mapProfileErrors, mapRegisterErrors, mapReviewErrors, mapSudoError } from './field-errors';

describe('erros do 422 por campo', () => {
  it('cadastro: frases do RegistrationService e do UserTypeDetailsValidator', () => {
    const out = mapRegisterErrors(
      [
        'Para criar a conta, leia e aceite os Termos de Uso do Friendly Eats (versão 2026-10-25.1).',
        'O @usuário deve ter 3 a 30 caracteres: letras minúsculas, números, ponto ou sublinhado.',
        'A senha não pode conter seu e-mail ou @usuário.',
        'Preencha o campo "cnae" da categoria Estabelecimento.',
        'CPF inválido. Confira os 11 dígitos.',
        'Sem CNPJ? Ambulante, food truck e cozinha caseira se cadastram com "informalFormat" (AMBULANTE, COZINHA_CASEIRA, FOOD_TRUCK) e o CPF do titular.',
        'O Friendly Eats é para maiores de 13 anos.',
        'Campo "foo" não se aplica à categoria Comensal.',
      ],
      { cnae: 'CNAE principal', cnpj: 'CNPJ' },
    );
    expect(out.fields.acceptTerms).toHaveLength(1);
    expect(out.fields.handle).toHaveLength(1);
    // a senha cita o @usuário, mas é erro de senha
    expect(out.fields.password).toEqual(['A senha não pode conter seu e-mail ou @usuário.']);
    expect(out.fields.cnae).toEqual(['Preencha o campo “CNAE principal” da categoria Estabelecimento.']);
    expect(out.fields.cpf).toHaveLength(1);
    expect(out.fields.cnpj?.[0]).toMatch(/^Sem CNPJ\?/);
    expect(out.fields.dateOfBirth).toHaveLength(1);
    expect(out.general).toEqual(['Campo "foo" não se aplica à categoria Comensal.']);
  });

  it('avaliação: nota, modo, dimensão, gasto e texto', () => {
    const out = mapReviewErrors(
      [
        'Dê uma nota inteira de 1 (Péssimo) a 5 (Excelente).',
        'Preencha "visitMode".',
        'Ambiente não se avalia em "Entrega".',
        'Comida: nota inteira de 1 a 5.',
        'Gasto por pessoa entre R$ 0 e R$ 5.000.',
        '"body" passa de 2000 caracteres.',
        'Dimensão desconhecida: "SABOR".',
      ],
      { AMBIENTE: 'Ambiente', COMIDA: 'Comida' },
    );
    expect(out.fields.rating).toHaveLength(1);
    expect(out.fields.visitMode).toEqual(['Escolha como foi a visita.']);
    expect(out.fields['dim-AMBIENTE']).toHaveLength(1);
    expect(out.fields['dim-COMIDA']).toHaveLength(1);
    expect(out.fields.spent).toHaveLength(1);
    expect(out.fields.body).toEqual(['“Texto da avaliação” passa de 2000 caracteres.']);
    expect(out.general).toEqual(['Dimensão desconhecida: "SABOR".']);
  });
});

describe('mapProfileErrors', () => {
  it('cada frase do ProfileService vai para o seu campo', () => {
    const out = mapProfileErrors(
      [
        'A bio tem no máximo 150 caracteres.',
        'Até 5 links, cada um começando com https://',
        'Celular no formato internacional, ex.: +5541999990000.',
        'País no formato ISO de 2 letras, ex.: BR.',
        'Preencha o campo "website" da categoria Criador.',
        'Escolha até 5 culinárias favoritas.',
        'Algo novo que a tela não conhece.',
      ],
      { website: 'Site ou perfil do canal' },
    );
    expect(Object.keys(out.fields).sort()).toEqual(['bio', 'cellphoneNumber', 'countryOfResidence', 'favoriteCuisines', 'links', 'website'].sort());
    expect(out.fields.website).toEqual(['Preencha o campo “Site ou perfil do canal” da categoria Criador.']);
    expect(out.general).toEqual(['Algo novo que a tela não conhece.']);
  });

  it('modo sudo: o código só vai para o campo do código quando há 2FA', () => {
    expect(mapSudoError('Senha incorreta.', false).fields).toEqual({ password: ['Senha incorreta.'] });
    expect(mapSudoError('Senha ou código do app autenticador incorretos.', true).fields).toHaveProperty('code');
  });
});

describe('mapPostErrors (RF9 · RF16)', () => {
  it('cada frase do PostRules vai para o seu campo, com o rótulo da tela', () => {
    const r = mapPostErrors([
      'Dê um título ao post.',
      'Descreva a foto em "altText" (texto alternativo para quem usa leitor de tela).',
      'Foto de prato precisa da foto: envie antes (POST /api/v1/media/uploads) e informe "mediaObjectKey".',
      '"discountPercent" não se aplica a Foto de prato.',
      'No "de/por", o preço "de" precisa ser maior que o "por".',
      'Informe o preço do prato do dia em centavos ("priceCents"): o preço exibido é o cobrado (CDC art. 31).',
      'Liste ao menos um ingrediente (com a quantidade, ex.: "2 xícaras de farinha").',
      'A fonte do alerta precisa ser um endereço https, ex.: https://www.gov.br/anvisa/….',
      'Gergelim ainda não está na lista vigente da Anvisa (proposta de 2025).',
      'Menores de 18 anos não publicam conteúdo de bebida alcoólica (18+).',
      'Texto com contraste 1,4:1 sobre o container (mínimo 4,5:1). Sugestão: tinta #17120D (11,1:1).',
      'Informe o lugar ("establishmentId"): prato do dia do dono é sobre um lugar dele.',
      'Algo inesperado.',
    ]);
    expect(r.fields).toEqual({
      title: ['Dê um título ao post.'],
      altText: ['Descreva a foto em “Texto alternativo” (texto alternativo para quem usa leitor de tela).'],
      photo: ['Foto de prato precisa da foto: envie antes (POST /api/v1/media/uploads) e informe “Foto”.'],
      discount: ['“Desconto” não se aplica a Foto de prato.'],
      priceTo: ['No "de/por", o preço "de" precisa ser maior que o "por".'],
      price: ['Informe o preço do prato do dia em centavos (“Preço”): o preço exibido é o cobrado (CDC art. 31).'],
      ingredients: ['Liste ao menos um ingrediente (com a quantidade, ex.: "2 xícaras de farinha").'],
      alertSourceUrl: ['A fonte do alerta precisa ser um endereço https, ex.: https://www.gov.br/anvisa/….'],
      allergens: ['Gergelim ainda não está na lista vigente da Anvisa (proposta de 2025).'],
      ageRating: ['Menores de 18 anos não publicam conteúdo de bebida alcoólica (18+).'],
      background: ['Texto com contraste 1,4:1 sobre o container (mínimo 4,5:1). Sugestão: tinta #17120D (11,1:1).'],
      place: ['Informe o lugar (“Lugar”): prato do dia do dono é sobre um lugar dele.'],
    });
    expect(r.general).toEqual(['Algo inesperado.']);
  });

  it('campo que não está na tela vai para o resumo', () => {
    const r = mapPostErrors(['"recipe" não se aplica a Foto de prato.'], new Set(['title']));
    expect(r.fields).toEqual({});
    expect(r.general).toEqual(['“Receita” não se aplica a Foto de prato.']);
  });
});

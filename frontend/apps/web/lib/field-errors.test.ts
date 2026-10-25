import { describe, expect, it } from 'vitest';
import { mapRegisterErrors, mapReviewErrors } from './field-errors';

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

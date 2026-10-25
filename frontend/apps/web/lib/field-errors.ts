// O 422 da API traz errors[] como frases em pt-BR (sem nome de campo estruturado). Aqui cada frase é
// associada ao campo do formulário pelo texto; o que não casar aparece no resumo do topo.

export interface MappedErrors {
  fields: Record<string, string[]>;
  general: string[];
}

type Rule = { test: RegExp; field: string };

function add(out: MappedErrors, field: string | null, msg: string) {
  if (!field) {
    out.general.push(msg);
    return;
  }
  (out.fields[field] ??= []).push(msg);
}

/** Troca "chave" pelo rótulo da tela: 'Preencha o campo "cnpj"' → 'Preencha o campo “CNPJ”'. */
function humanize(msg: string, labels: Record<string, string>): string {
  return msg.replace(/"(\w+)"/g, (m, key: string) => (labels[key] ? `“${labels[key]}”` : m));
}

const REGISTER_RULES: Rule[] = [
  // a senha vem antes: "A senha não pode conter seu e-mail ou @usuário" é erro de senha
  { test: /senha/i, field: 'password' },
  { test: /Termos de Uso/, field: 'acceptTerms' },
  { test: /nome de exibição/i, field: 'name' },
  { test: /@usuário/, field: 'handle' },
  { test: /^Informe um e-mail válido/, field: 'email' },
  { test: /^Não foi possível usar este e-mail/, field: 'email' },
  { test: /tipo de usuário/i, field: 'userType' },
  { test: /data de nascimento|para maiores de|responsável maior de 18/i, field: 'dateOfBirth' },
  { test: /^Sem CNPJ\?|CNPJ inválido/, field: 'cnpj' },
  { test: /CPF inválido/, field: 'cpf' },
  { test: /site completo/, field: 'website' },
  { test: /CNAE/, field: 'cnae' },
];

/**
 * Cadastro (RF1): mensagens do RegistrationService, PasswordPolicy e UserTypeDetailsValidator.
 * {@code detailKeys} são os campos de type_details visíveis, com o rótulo de cada um.
 */
export function mapRegisterErrors(errors: string[], detailLabels: Record<string, string>): MappedErrors {
  const out: MappedErrors = { fields: {}, general: [] };
  for (const raw of errors) {
    const quoted = /"(\w+)"/.exec(raw)?.[1];
    if (quoted && detailLabels[quoted] && !/não se aplica/.test(raw)) {
      add(out, quoted, humanize(raw, detailLabels));
      continue;
    }
    const rule = REGISTER_RULES.find((r) => r.test.test(raw));
    add(out, rule?.field ?? null, humanize(raw, detailLabels));
  }
  return out;
}

/** Avaliação (RF8): mensagens do EstablishmentRules.review. */
export function mapReviewErrors(errors: string[], dimensionLabels: Record<string, string>): MappedErrors {
  const out: MappedErrors = { fields: {}, general: [] };
  const labels: Record<string, string> = { visitMode: 'Como foi a visita', body: 'Texto da avaliação', ...dimensionLabels };
  for (const raw of errors) {
    const msg = humanize(raw, labels);
    if (/^Dê uma nota/.test(raw)) {
      add(out, 'rating', msg);
    } else if (/"visitMode"/.test(raw)) {
      add(out, 'visitMode', /^Preencha/.test(raw) ? 'Escolha como foi a visita.' : msg);
    } else if (/^Gasto por pessoa/.test(raw)) {
      add(out, 'spent', msg);
    } else if (/"body"/.test(raw)) {
      add(out, 'body', msg);
    } else {
      const dim = Object.entries(dimensionLabels).find(([, label]) => raw.startsWith(`${label}:`) || raw.startsWith(`${label} não`));
      add(out, dim ? `dim-${dim[0]}` : null, msg);
    }
  }
  return out;
}

// Seções de /configuracoes (RF2 · RF3 · RF4 · RF5 · RF13), na ordem do menu.
export type SettingsSectionSlug = 'perfil' | 'exibicao' | 'privacidade' | 'seguranca' | 'denuncias' | 'conta';

export interface SettingsSection {
  slug: SettingsSectionSlug;
  href: `/configuracoes/${SettingsSectionSlug}`;
  title: string;
  description: string;
  /** Base legal mostrada na lista, quando houver. */
  law?: string;
  rf: 'RF2' | 'RF3' | 'RF4' | 'RF5' | 'RF13';
}

export const SETTINGS_SECTIONS: readonly SettingsSection[] = [
  {
    slug: 'perfil',
    href: '/configuracoes/perfil',
    title: 'Perfil',
    description: 'Nome, @usuário, foto, bio, cidade, campos do seu tipo de conta e o seu gosto',
    law: 'LGPD · Art. 18, III — correção',
    rf: 'RF2',
  },
  {
    slug: 'exibicao',
    href: '/configuracoes/exibicao',
    title: 'Exibição',
    description: 'Tema, fundo, tamanho do texto, aba inicial, idioma e notificações',
    rf: 'RF2',
  },
  {
    slug: 'privacidade',
    href: '/configuracoes/privacidade',
    title: 'Privacidade',
    description: 'Quem vê e comenta, conteúdo sensível, bloqueados, silenciados, palavras filtradas e os usos opcionais dos seus dados',
    law: 'Art. 7º, I e Art. 11 — consentimento',
    rf: 'RF3',
  },
  {
    slug: 'seguranca',
    href: '/configuracoes/seguranca',
    title: 'Senha e segurança',
    description: 'Senha, aparelhos conectados e verificação em duas etapas',
    rf: 'RF4',
  },
  {
    slug: 'denuncias',
    href: '/configuracoes/denuncias',
    title: 'Denúncias e decisões',
    description: 'Minhas denúncias, decisões da moderação sobre o meu conteúdo e recursos',
    law: 'ECA Digital · Art. 30 — contestação',
    rf: 'RF13',
  },
  {
    slug: 'conta',
    href: '/configuracoes/conta',
    title: 'Conta',
    description: 'Sair e excluir a conta, com 30 dias para reativar',
    law: 'Art. 18, VI — eliminação',
    rf: 'RF5',
  },
];

export function sectionBySlug(slug: SettingsSectionSlug): SettingsSection {
  return SETTINGS_SECTIONS.find((s) => s.slug === slug)!;
}

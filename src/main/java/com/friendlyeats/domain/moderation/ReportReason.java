package com.friendlyeats.domain.moderation;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Motivos fechados de denúncia (RF13 · RC-SEG-01), com código estável. Os mesmos códigos são as regras da política
 * publicada citadas na decisão. {@code ownerContest} marca os motivos que o dono pode usar para contestar uma
 * avaliação (RC-SEG-08): "discordo da nota" não é motivo.
 */
public enum ReportReason {
    CRIANCA_EM_RISCO("Criança ou adolescente em risco",
            "Exploração, abuso, aliciamento ou exposição de criança ou adolescente.", ModerationPriority.CRITICA,
            all(), false),
    DESINFORMACAO_SANITARIA("Desinformação sanitária",
            "Alerta sanitário falso ou que contradiz a fonte oficial.", ModerationPriority.URGENTE,
            EnumSet.of(TargetType.POST, TargetType.COMMENT, TargetType.REVIEW), false),
    ASSEDIO("Assédio ou cyberbullying", "Ataques repetidos, humilhação, ameaça ou intimidação de uma pessoa.",
            ModerationPriority.ALTA, all(), false),
    ODIO("Discurso de ódio ou discriminação", "Ataque por raça, religião, origem, gênero, orientação ou deficiência.",
            ModerationPriority.ALTA, all(), false),
    CONTEUDO_SEXUAL("Nudez ou conteúdo sexual", "Nudez, conteúdo sexual ou imagem íntima sem consentimento.",
            ModerationPriority.ALTA, all(), false),
    DADO_PESSOAL("Dado pessoal exposto", "Telefone, endereço, documento ou foto de alguém sem autorização.",
            ModerationPriority.ALTA, all(), true),
    GOLPE_FRAUDE("Golpe ou fraude", "Promoção falsa, cobrança indevida, link enganoso ou perfil que se passa por outro.",
            ModerationPriority.ALTA, all(), false),
    AVALIACAO_FALSA("Avaliação falsa ou com incentivo",
            "Não é experiência própria, foi paga ou trocada por vantagem, ou o lugar oferece vantagem em troca de nota.",
            ModerationPriority.NORMAL, EnumSet.of(TargetType.REVIEW, TargetType.ESTABLISHMENT, TargetType.POST), true),
    CONFLITO_INTERESSE("Conflito de interesse", "Quem escreveu é dono, funcionário, parente ou concorrente do lugar.",
            ModerationPriority.NORMAL, EnumSet.of(TargetType.REVIEW, TargetType.POST), true),
    OFENSA_OU_CRIME("Ofensa ou acusação de crime", "Xingamento ou acusação de crime contra pessoa ou empresa.",
            ModerationPriority.NORMAL, EnumSet.of(TargetType.REVIEW, TargetType.POST, TargetType.COMMENT), true),
    DIREITO_AUTORAL("Direito autoral", "Foto, receita ou texto de outra pessoa usado sem permissão.",
            ModerationPriority.NORMAL, EnumSet.of(TargetType.POST, TargetType.PROFILE, TargetType.ESTABLISHMENT), false),
    SPAM("Spam", "Propaganda repetida, correntes ou conteúdo automático.", ModerationPriority.BAIXA, all(), false),
    FORA_DO_TEMA("Fora do tema ou lugar errado", "Não fala de comida ou do lugar avaliado.", ModerationPriority.BAIXA,
            EnumSet.of(TargetType.REVIEW, TargetType.POST, TargetType.COMMENT), true),
    OUTRO("Outro motivo", "Algo que fere os Termos de Uso e não está na lista. Conte o que aconteceu.",
            ModerationPriority.NORMAL, all(), false);

    private final String label;
    private final String description;
    private final ModerationPriority priority;
    private final Set<TargetType> targets;
    private final boolean ownerContest;

    ReportReason(String label, String description, ModerationPriority priority, Set<TargetType> targets,
                 boolean ownerContest) {
        this.label = label;
        this.description = description;
        this.priority = priority;
        this.targets = targets;
        this.ownerContest = ownerContest;
    }

    private static Set<TargetType> all() {
        return EnumSet.allOf(TargetType.class);
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public ModerationPriority priority() {
        return priority;
    }

    public Set<TargetType> targets() {
        return targets;
    }

    public boolean appliesTo(TargetType type) {
        return targets.contains(type);
    }

    public boolean ownerContest() {
        return ownerContest;
    }

    public static Optional<ReportReason> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

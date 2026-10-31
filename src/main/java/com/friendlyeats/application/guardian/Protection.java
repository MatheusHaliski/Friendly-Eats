package com.friendlyeats.application.guardian;

import com.friendlyeats.domain.model.UserPreference;

import java.util.Arrays;
import java.util.Optional;

/**
 * Proteções da conta de menor de 16 (ECA Digital, art. 17, §4º) que só o responsável vinculado afrouxa (RF17).
 * {@link #loosened} lê o estado atual; {@link #apply} afrouxa ({@code allowed = true}) ou religa a proteção.
 */
public enum Protection {
    PUBLIC_PROFILE("Perfil público", "Qualquer pessoa vê as publicações no perfil."),
    SENSITIVE_CONTENT("Mostrar conteúdo sensível", "Fotos e textos que podem incomodar aparecem sem aviso."),
    COMMENTS_FROM_EVERYONE("Comentários de todo mundo", "Qualquer conta comenta, não só quem segue."),
    NO_USAGE_REMINDER("Sem lembrete de tempo de uso", "O lembrete de 60 minutos de uso fica desligado.");

    static final short MINOR_USAGE_REMINDER = 60;

    private final String label;
    private final String description;

    Protection(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean loosened(UserPreference p) {
        return switch (this) {
            case PUBLIC_PROFILE -> !p.isProfilePrivate();
            case SENSITIVE_CONTENT -> p.getSensitiveContent() == UserPreference.SensitiveContent.ALLOW;
            case COMMENTS_FROM_EVERYONE -> p.getWhoCanComment() == UserPreference.CommentAudience.EVERYONE;
            case NO_USAGE_REMINDER -> p.getUsageReminderMinutes() == null;
        };
    }

    public void apply(UserPreference p, boolean allowed) {
        switch (this) {
            case PUBLIC_PROFILE -> p.setProfilePrivate(!allowed);
            case SENSITIVE_CONTENT -> p.setSensitiveContent(allowed
                    ? UserPreference.SensitiveContent.ALLOW : UserPreference.SensitiveContent.LIMIT);
            case COMMENTS_FROM_EVERYONE -> p.setWhoCanComment(allowed
                    ? UserPreference.CommentAudience.EVERYONE : UserPreference.CommentAudience.FOLLOWERS);
            case NO_USAGE_REMINDER -> p.setUsageReminderMinutes(allowed ? null : MINOR_USAGE_REMINDER);
        }
    }

    public static Optional<Protection> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.name().equals(code)).findFirst();
    }
}

package com.friendlyeats.application.moderation;

/**
 * Avisos fora do app (e-mail) da moderação (RF13). Dentro do app, o autor vê as decisões em "Decisões sobre meu
 * conteúdo" e quem denunciou acompanha em "Minhas denúncias". A implementação de produção (e-mail transacional)
 * entra com o provedor; até lá {@link LoggingModerationNotifier}.
 */
public interface ModerationNotifier {

    /** Ao autor: decisão com medida (ECA Digital, art. 30), sem dizer quem denunciou. */
    void decisionToAuthor(long authorId, String protocol, String action, String policy, boolean automated);

    /** A quem denunciou: o resultado (procedente ou improcedente). {@code email} só no formulário público. */
    void outcomeToReporter(Long reporterId, String email, String protocol, boolean upheld);

    /** Ao recorrente: o resultado do recurso. */
    void appealResult(long appellantId, String protocol, boolean reversed);
}

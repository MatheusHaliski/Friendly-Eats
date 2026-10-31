package com.friendlyeats.application.account;

import com.friendlyeats.domain.model.UserAccount;

import java.time.Instant;

/**
 * Avisos ao titular por e-mail (fora de banda): código de verificação e mudanças de segurança. A implementação
 * de produção (provedor de e-mail transacional) entra depois; até lá {@link LoggingAccountNotifier}.
 */
public interface AccountNotifier {

    void verificationCode(UserAccount user, String code);

    void loginAlert(UserAccount user, String device);

    void passwordChanged(UserAccount user);

    void mfaChanged(UserAccount user, boolean enabled);

    void deletionScheduled(UserAccount user, Instant deletionAt);

    void accountReactivated(UserAccount user);

    // ------------------------------------------------------------ RF17 · idade e responsável

    /** Convite ao responsável (ECA Digital, art. 24): o link leva o token de uso único. */
    void guardianInvite(String guardianEmail, UserAccount minor, String token, Instant expiresAt);

    /** Ao menor: a supervisão do responsável está ativa (art. 17, III). */
    void guardianLinked(UserAccount minor, UserAccount guardian);

    /** Ao menor: o convite ao responsável segue sem resposta há {@code days} dias. */
    void guardianLinkPending(UserAccount minor, long days);

    /** Ao responsável: o menor pediu para afrouxar uma proteção. */
    void guardianRequest(UserAccount guardian, UserAccount minor, String protectionLabel);

    /** Contestação da idade aferida decidida por uma pessoa (Decreto 12.880, art. 27). */
    void ageAppealDecided(UserAccount user, boolean accepted);

    // ------------------------------------------------------------ RF18 · central do titular

    /** Pedido de exportação registrado: aviso de segurança, para o caso de sessão sequestrada. */
    void dataExportRequested(UserAccount user, Instant dueAt);

    /** Exportação pronta para baixar até {@code expiresAt}. */
    void dataExportReady(UserAccount user, Instant expiresAt);
}

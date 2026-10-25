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
}

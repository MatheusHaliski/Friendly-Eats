package com.friendlyeats.application.account;

import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.security.LoginThrottle;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Modo sudo (OWASP/NIST): ações sensíveis — trocar senha, mexer no 2FA, excluir a conta — pedem a senha de novo
 * e, com 2FA ativo, um código. Erros contam no mesmo limite de tentativas do login.
 */
@Component
public class StepUp {

    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final MfaService mfa;
    private final LoginThrottle throttle;

    public StepUp(UserAccountRepository users, PasswordEncoder passwords, MfaService mfa, LoginThrottle throttle) {
        this.users = users;
        this.passwords = passwords;
        this.mfa = mfa;
        this.throttle = throttle;
    }

    public UserAccount confirm(long userId, String password, String code, String ipHash) {
        String account = "sudo:" + userId;
        throttle.beforeAttempt(account, ipHash);
        UserAccount user = users.findById(userId).orElseThrow();
        boolean ok = password != null && passwords.matches(password, user.getPasswordHash())
                && (!mfa.isEnabled(userId) || mfa.verify(userId, code));
        if (!ok) {
            throttle.onFailure(account);
            throw new AccountExceptions.Forbidden(mfa.isEnabled(userId)
                    ? "Senha ou código do app autenticador incorretos."
                    : "Senha incorreta.");
        }
        throttle.onSuccess(account);
        return user;
    }
}

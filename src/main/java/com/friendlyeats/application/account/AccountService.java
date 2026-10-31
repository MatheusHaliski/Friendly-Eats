package com.friendlyeats.application.account;

import com.friendlyeats.application.account.AccountViews.DeletionView;
import com.friendlyeats.application.media.MediaJanitor;
import com.friendlyeats.application.post.PostContentEraser;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.persistence.postgres.EstablishmentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.RecoveryCodeRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ReviewRepository;
import com.friendlyeats.infrastructure.persistence.postgres.SafetyRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserMfaRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserPreferenceRepository;
import com.friendlyeats.security.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Segurança da conta: trocar senha e excluir conta com 30 dias para reativar (RF5).
 * Ambas encerram as outras sessões; a exclusão encerra todas, inclusive a atual.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserAccountRepository users;
    private final UserMfaRepository mfa;
    private final RecoveryCodeRepository recovery;
    private final UserPreferenceRepository preferences;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    private final MediaJanitor media;
    private final AccountNotifier notifier;
    private final Clock clock;
    private final Duration grace;
    private final ReviewRepository reviews;
    private final EstablishmentRepository establishments;
    private final PostContentEraser postEraser;
    private final SafetyRepository safety;

    public AccountService(UserAccountRepository users, UserMfaRepository mfa, RecoveryCodeRepository recovery,
                          UserPreferenceRepository preferences, PasswordEncoder passwords, SessionService sessions,
                          MediaJanitor media, AccountNotifier notifier, Clock clock,
                          @Value("${friendly-eats.account.deletion-grace:30d}") Duration grace,
                          ReviewRepository reviews, EstablishmentRepository establishments,
                          PostContentEraser postEraser, SafetyRepository safety) {
        this.users = users;
        this.mfa = mfa;
        this.recovery = recovery;
        this.preferences = preferences;
        this.passwords = passwords;
        this.sessions = sessions;
        this.media = media;
        this.notifier = notifier;
        this.clock = clock;
        this.grace = grace;
        this.reviews = reviews;
        this.establishments = establishments;
        this.postEraser = postEraser;
        this.safety = safety;
    }

    /** Senha nova (já com o modo sudo confirmado): sai de todos os outros aparelhos. */
    @Transactional
    public int changePassword(UserAccount user, String newPassword, String currentSessionId) {
        List<String> problems = PasswordPolicy.problems(newPassword, user.getEmail(), user.getHandle());
        if (!problems.isEmpty()) {
            throw new AccountExceptions.Invalid(problems);
        }
        if (passwords.matches(newPassword, user.getPasswordHash())) {
            throw new AccountExceptions.Invalid("A nova senha precisa ser diferente da atual.");
        }
        user.setPasswordHash(passwords.encode(newPassword));
        users.save(user);
        notifier.passwordChanged(user);
        return sessions.revokeAll(user.getId(), currentSessionId);
    }

    /**
     * "Excluir conta" confirmado no modal (senha + 2FA já conferidos): a conta some na hora, todas as sessões
     * caem e o titular tem {@code grace} (30 dias) para entrar de novo e reativar.
     */
    @Transactional
    public DeletionView requestDeletion(UserAccount user) {
        Instant when = user.requestDeletion(clock.instant(), grace);
        users.save(user);
        sessions.revokeAll(user.getId(), null);
        notifier.deletionScheduled(user, when);
        return new DeletionView(user.getAccountStatus(), when, (int) grace.toDays());
    }

    /** Contas cujo prazo de reativação venceu (o job chama {@link #eliminate(long)} uma a uma). */
    @Transactional(readOnly = true)
    public List<Long> dueForElimination() {
        return users.findByAccountStatusAndDeletionScheduledForBefore(AccountStatus.PENDING_DELETION, clock.instant())
                .stream().map(UserAccount::getId).toList();
    }

    @Transactional
    public void eliminate(long userId) {
        UserAccount user = users.findById(userId).orElseThrow();
        if (user.getAccountStatus() != AccountStatus.PENDING_DELETION || user.canReactivate(clock.instant())) {
            return;
        }
        String photoKey = user.getPhotoObjectKey();
        // avaliações escritas (RF8) e estabelecimentos da conta (RF6, com as avaliações deles) saem por inteiro
        reviews.deleteAllWrittenBy(userId);
        // posts e comentários viram REMOVED sem conteúdo, curtidas saem e as fotos dos posts saem do bucket (RF9, RF12)
        postEraser.eraseContentOf(userId);
        establishments.deleteAllOwnedBy(userId);
        mfa.deleteById(userId);
        recovery.deleteAllOf(userId);
        preferences.deleteById(userId);
        // bloqueios, silenciados e palavras filtradas (RF19), nos dois sentidos
        safety.deleteAllOf(userId);
        user.anonymizeForLgpdDeletion(passwords.encode(UUID.randomUUID().toString()));
        users.save(user);
        sessions.revokeAll(userId, null);
        // a foto de perfil sai do bucket só depois do commit (o banco já não aponta para ela)
        media.deleteAfterCommit(Collections.singletonList(photoKey));   // nulo é ignorado
        // Próximos passos da saga: Cassandra e DEK.
        log.info("Conta {} eliminada", userId);
    }
}

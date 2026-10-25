package com.friendlyeats.application.post;

import com.friendlyeats.application.account.AgePolicy;
import com.friendlyeats.application.common.PublicLinks;
import com.friendlyeats.application.post.PostViews.AuthorView;
import com.friendlyeats.application.profile.AccountBadge;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserPreference;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserPreferenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quem é o dono de posts e comentários na visão pública (RF14, RF15): foto, nome, @, bolinha do tipo de conta, selo e
 * link do perfil, e as preferências que mudam quem vê (perfil privado) e quem comenta.
 *
 * <p>Sem linha em {@code user_preference} (contas antigas ou de exemplo), valem os padrões do cadastro: perfil
 * privado e comentários só de seguidores para menor de 16 (ECA Digital).
 */
@Component
public class AuthorDirectory {

    static final String DELETED_NAME = "Conta excluída";

    private final UserAccountRepository users;
    private final UserPreferenceRepository preferences;
    private final PublicLinks links;
    private final AgePolicy age;
    private final Clock clock;
    private final int protectedUntil;

    public AuthorDirectory(UserAccountRepository users, UserPreferenceRepository preferences, PublicLinks links,
                           AgePolicy age, Clock clock,
                           @Value("${friendly-eats.registration.protected-until-age:16}") int protectedUntil) {
        this.users = users;
        this.preferences = preferences;
        this.links = links;
        this.age = age;
        this.clock = clock;
        this.protectedUntil = protectedUntil;
    }

    public Optional<UserAccount> find(Long userId) {
        return userId == null ? Optional.empty() : users.findById(userId);
    }

    /** Uma consulta para todos os autores da página (evita N+1 nos cards e na lista de comentários). */
    public Map<Long, UserAccount> findAll(Collection<Long> userIds) {
        return users.findAllById(userIds).stream().collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    }

    public static boolean isActive(UserAccount user) {
        return user != null && user.getAccountStatus() == AccountStatus.ACTIVE;
    }

    public boolean adult(UserAccount user) {
        return user != null && age.adult(user.getUserDateOfBirth());
    }

    /** Preferências gravadas ou, sem linha, os padrões do cadastro (não grava nada). */
    public UserPreference preferenceOf(UserAccount user) {
        return preferences.findById(user.getId())
                .orElseGet(() -> UserPreference.defaultsFor(user.getId(), age.protectedMinor(user.getUserDateOfBirth())));
    }

    public boolean isPrivate(UserAccount user) {
        return preferenceOf(user).isProfilePrivate();
    }

    /** Quem nasceu depois desta data é menor protegido (para o SQL do feed, que não chama o {@link AgePolicy}). */
    public LocalDate minorBirthAfter() {
        return LocalDate.now(clock.withZone(PostRules.BRAZIL)).minusYears(protectedUntil);
    }

    /**
     * Conta ativa: nome, foto e link do perfil. Conta eliminada (LGPD): "Conta excluída", sem foto, sem bolinha e sem
     * link. Suspensa ou em exclusão: nome sem link (o perfil não abre enquanto durar).
     */
    public AuthorView view(UserAccount user) {
        if (user == null || user.getAccountStatus() == AccountStatus.ANONYMOUS_LGPD_DELETED) {
            return new AuthorView(user == null ? null : user.getId(), DELETED_NAME, null, null, false, null, null, false);
        }
        boolean available = isActive(user);
        return new AuthorView(user.getId(), user.getName(), user.getHandle(), AccountBadge.of(user.getUserType()),
                user.getCategoryVerification() == CategoryVerification.VERIFIED, links.mediaUrl(user.getPhotoObjectKey()),
                available ? PostViews.profilePath(user.getId()) : null, available);
    }
}

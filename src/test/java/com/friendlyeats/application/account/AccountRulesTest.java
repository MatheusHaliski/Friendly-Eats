package com.friendlyeats.application.account;

import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.Cnpj;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.domain.model.UserAccount;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRulesTest {

    @Test
    void cnpjAcceptsNumericAndTheNewAlphanumericFormat() {
        assertThat(Cnpj.isValid("11.222.333/0001-81")).isTrue();
        assertThat(Cnpj.isValid("12.ABC.345/01DE-35")).isTrue();     // exemplo da Receita (IN RFB 2.229/2024)
        assertThat(Cnpj.isValid("12abc34501de35")).isTrue();
        assertThat(Cnpj.isValid("12.ABC.345/01DE-36")).isFalse();
        assertThat(Cnpj.isValid("11111111111111")).isFalse();
        assertThat(Cnpj.isValid("12.ABC.345/01DE-3X")).isFalse();
    }

    @Test
    void passwordPolicyIsAboutLengthAndGuessability() {
        assertThat(PasswordPolicy.problems("uma frase longa e boa", "ana@example.com", "ana")).isEmpty();
        assertThat(PasswordPolicy.problems("curta", "ana@example.com", "ana")).hasSize(1);
        assertThat(PasswordPolicy.problems("password1234", "ana@example.com", "ana")).isNotEmpty();
        assertThat(PasswordPolicy.problems("anaribeiro-2026!", "anaribeiro@example.com", "ana")).isNotEmpty();
    }

    @Test
    void typeDetailsAreValidatedPerCategoryAndPrivateFieldsStayPrivate() {
        assertThatThrownBy(() -> UserTypeDetailsValidator.validate(UserCategoryEnum.ESTABELECIMENTO,
                Map.of("cnpj", "11.222.333/0001-81", "cnae", "5611201")))
                .isInstanceOf(AccountExceptions.Invalid.class)
                .satisfies(e -> assertThat(((AccountExceptions.Invalid) e).errors())
                        .anyMatch(m -> m.contains("legalName")).anyMatch(m -> m.contains("CNAE")));
        assertThatThrownBy(() -> UserTypeDetailsValidator.validate(UserCategoryEnum.COMENSAL, Map.of("religion", "x")))
                .isInstanceOf(AccountExceptions.Invalid.class);

        Map<String, Object> place = UserTypeDetailsValidator.validate(UserCategoryEnum.ESTABELECIMENTO,
                Map.of("cnpj", "11222333000181", "legalName", "Sabor Mineiro Ltda", "tradeName", "Sabor Mineiro",
                        "cnae", "5611-2/01", "city", "Belo Horizonte", "state", "MG", "representativeName", "Rita",
                        "representativeRole", "Sócia", "representativeEmail", "rita@sabormineiro.example"));
        assertThat(UserTypeDetailsValidator.initialVerification(UserCategoryEnum.ESTABELECIMENTO, place))
                .isEqualTo(CategoryVerification.PENDING);
        assertThat(UserTypeDetailsValidator.initialVerification(UserCategoryEnum.CHEF, Map.of("role", "CHEF")))
                .isEqualTo(CategoryVerification.NOT_REQUIRED);
        assertThat(UserTypeDetailsValidator.publicView(place)).containsKeys("tradeName", "cnae")
                .doesNotContainKeys("representativeName", "representativeEmail");

        Map<String, Object> community = UserTypeDetailsValidator.validate(UserCategoryEnum.COMUNIDADE,
                Map.of("communityType", "CONFRARIA", "city", "Curitiba", "state", "PR",
                        "contactName", "Joana", "contactEmail", "joana@example.com"));
        assertThat(UserTypeDetailsValidator.publicView(community)).containsKeys("communityType", "city")
                .doesNotContainKeys("contactName", "contactEmail");
    }

    @Test
    void deletionGraceAllowsReactivationOnlyWithinTheWindow() {
        UserAccount user = new UserAccount("Gil", " GIL@Example.com ", "hash", UserCategoryEnum.COMENSAL);
        assertThat(user.getEmail()).isEqualTo("gil@example.com");
        user.markEmailVerified(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        Instant now = Instant.parse("2026-10-17T12:00:00Z");
        Instant when = user.requestDeletion(now, Duration.ofDays(30));
        assertThat(when).isEqualTo(Instant.parse("2026-11-16T12:00:00Z"));
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING_DELETION);
        assertThat(user.canReactivate(Instant.parse("2026-11-16T11:59:00Z"))).isTrue();
        assertThat(user.canReactivate(Instant.parse("2026-11-16T12:00:01Z"))).isFalse();

        user.reactivate(Instant.parse("2026-10-20T00:00:00Z"));
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getDeletionScheduledFor()).isNull();

        user.requestDeletion(now, Duration.ofDays(30));
        assertThatThrownBy(() -> user.reactivate(Instant.parse("2026-12-01T00:00:00Z"))).isInstanceOf(IllegalStateException.class);
    }
}

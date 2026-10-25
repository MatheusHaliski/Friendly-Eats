package com.friendlyeats.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelTest {

    @Test
    void accountTypesHaveCodesAndOnlyBusinessesNeedTheSeal() {
        assertThat(UserCategoryEnum.values()).extracting(UserCategoryEnum::getCode)
                .containsExactly('A', 'B', 'C', 'D', 'E', 'F', 'G');
        assertThat(UserCategoryEnum.fromCode('d')).isEqualTo(UserCategoryEnum.ESTABELECIMENTO);
        assertThat(UserCategoryEnum.values()).filteredOn(UserCategoryEnum::needsSeal)
                .containsExactly(UserCategoryEnum.ESTABELECIMENTO, UserCategoryEnum.VAREJO, UserCategoryEnum.OFICIAL);
        assertThat(UserCategoryEnum.values()).filteredOn(UserCategoryEnum::canPublishFoodAlerts)
                .containsExactly(UserCategoryEnum.OFICIAL);
        assertThat(UserCategoryEnum.values()).allSatisfy(t -> assertThat(t.getHexColor()).matches("#[0-9A-F]{6}"));
    }

    @Test
    void lgpdDeletionIsTerminalAndErasesPersonalData() {
        UserAccount user = new UserAccount("Ana", "ana@example.com", "hash", UserCategoryEnum.COMENSAL);
        user.setDietaryStyles(List.of("SEM_GLUTEN"));
        user.setFavoriteCuisines(List.of("MINEIRA"));
        user.setLastSavedLocation(new GeoLocation(-25.42, -49.27, Instant.now()));
        user.activate();

        user.anonymizeForLgpdDeletion("anon-hash");

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ANONYMOUS_LGPD_DELETED);
        assertThat(user.getDietaryStyles()).isEmpty();
        assertThat(user.getFavoriteCuisines()).isEmpty();
        assertThat(user.getLastSavedLocation()).isNull();
        assertThat(user.getEmail()).doesNotContain("ana");
        assertThat(user.getDeletedAt()).isNotNull();
        assertThatThrownBy(user::activate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pendingUserCannotBeSuspended() {
        UserAccount user = new UserAccount("Ana", "ana@example.com", "hash", UserCategoryEnum.COMENSAL);
        assertThatThrownBy(user::suspend).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void geoLocationRejectsInvalidCoordinates() {
        assertThatThrownBy(() -> new GeoLocation(91, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

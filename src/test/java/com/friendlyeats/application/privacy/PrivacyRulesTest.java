package com.friendlyeats.application.privacy;

import com.friendlyeats.application.guardian.Protection;
import com.friendlyeats.domain.model.UserPreference;
import com.friendlyeats.security.AccessLogCipher;
import com.friendlyeats.security.AppSecrets;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras sem banco do RF17 e do RF18: prazo de 3 dias úteis, cifra do IP e as proteções do menor. */
class PrivacyRulesTest {

    @Test
    void communicationDeadlineSkipsTheWeekend() {
        // quarta 10h → fim do dia na segunda seguinte (qui, sex, seg)
        assertThat(SecurityIncidentService.communicationDeadline(Instant.parse("2026-10-28T13:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-03T02:59:59Z"));
        // sábado → quarta
        assertThat(SecurityIncidentService.communicationDeadline(Instant.parse("2026-10-31T15:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-05T02:59:59Z"));
        // segunda 23h30 em Brasília (terça em UTC) conta a partir da segunda
        assertThat(SecurityIncidentService.communicationDeadline(Instant.parse("2026-10-27T02:30:00Z")))
                .isEqualTo(Instant.parse("2026-10-30T02:59:59Z"));
    }

    @Test
    void accessLogCipherRecoversTheIpOnlyForTheSameUser() {
        AppSecrets secrets = new AppSecrets(Base64.getEncoder().encodeToString(new byte[32]), null);
        AccessLogCipher cipher = new AccessLogCipher(secrets);
        byte[] sealed = cipher.seal("2001:db8::1", 51234, 7L);
        assertThat(cipher.open(sealed, 7L)).containsExactly("2001:db8::1", "51234");
        assertThat(new String(sealed, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("2001:db8");
        assertThatThrownBy(() -> cipher.open(sealed, 8L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void protectionsLoosenAndRestore() {
        UserPreference p = UserPreference.defaultsFor(1L, true);
        for (Protection protection : Protection.values()) {
            assertThat(protection.loosened(p)).as(protection.name()).isFalse();
            protection.apply(p, true);
            assertThat(protection.loosened(p)).as(protection.name()).isTrue();
            protection.apply(p, false);
            assertThat(protection.loosened(p)).as(protection.name()).isFalse();
        }
        assertThat(p.getUsageReminderMinutes()).isEqualTo((short) 60);
    }
}

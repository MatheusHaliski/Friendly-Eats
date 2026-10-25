package com.friendlyeats.support;

import com.friendlyeats.application.account.AccountNotifier;
import com.friendlyeats.domain.model.UserAccount;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Guarda os avisos que iriam por e-mail, para os testes lerem o código de verificação e os alertas. */
@TestConfiguration(proxyBeanMethods = false)
public class CapturingNotifier {

    @Bean
    @Primary
    Captured capturedNotifier() {
        return new Captured();
    }

    public static class Captured implements AccountNotifier {
        public final Map<String, String> codes = new ConcurrentHashMap<>();
        public final List<String> events = new CopyOnWriteArrayList<>();

        @Override
        public void verificationCode(UserAccount user, String code) {
            codes.put(user.getEmail(), code);
        }

        @Override
        public void loginAlert(UserAccount user, String device) {
            events.add("login:" + user.getEmail());
        }

        @Override
        public void passwordChanged(UserAccount user) {
            events.add("password:" + user.getEmail());
        }

        @Override
        public void mfaChanged(UserAccount user, boolean enabled) {
            events.add("mfa-" + enabled + ":" + user.getEmail());
        }

        @Override
        public void deletionScheduled(UserAccount user, Instant deletionAt) {
            events.add("deletion:" + user.getEmail());
        }

        @Override
        public void accountReactivated(UserAccount user) {
            events.add("reactivated:" + user.getEmail());
        }
    }
}

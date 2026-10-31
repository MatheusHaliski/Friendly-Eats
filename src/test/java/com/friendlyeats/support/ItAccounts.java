package com.friendlyeats.support;

import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contas e "navegadores" logados para os testes de integração do RF17 e do RF18: limpa o banco e o Valkey, cria
 * contas ativas e mantém um {@link ApiClient} (login real) por conta.
 */
public final class ItAccounts {

    public static final String PASSWORD = "uma frase longa de teste";

    private final int port;
    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder passwords;
    private final Map<Long, ApiClient> browsers = new HashMap<>();

    public ItAccounts(int port, UserAccountRepository users, JdbcTemplate jdbc, TransactionTemplate tx,
                      PasswordEncoder passwords, StringRedisTemplate redis) {
        this.port = port;
        this.users = users;
        this.jdbc = jdbc;
        this.tx = tx;
        this.passwords = passwords;
        jdbc.execute("TRUNCATE TABLE user_account, access_log, access_log_query, security_incident RESTART IDENTITY CASCADE");
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
    }

    public UserAccount person(String name, String email, LocalDate birth) {
        UserAccount u = new UserAccount(name, email, passwords.encode(PASSWORD), UserCategoryEnum.COMENSAL);
        u.setUserDateOfBirth(birth);
        u.setHandle(email.substring(0, email.indexOf('@')).replaceAll("[^a-z0-9._]", ""));
        u.activate();
        return tx.execute(s -> users.saveAndFlush(u));
    }

    public UserAccount adult(String name, String email) {
        return person(name, email, LocalDate.of(1990, 1, 1));
    }

    public ApiClient browser(UserAccount who) {
        return browsers.computeIfAbsent(who.getId(), id -> {
            ApiClient b = new ApiClient(port);
            assertThat(b.login(who.getEmail(), PASSWORD).getStatusCode().value()).isEqualTo(200);
            return b;
        });
    }

    public ResponseEntity<Map> send(HttpMethod method, String path, UserAccount who, Object body) {
        return (who == null ? new ApiClient(port) : browser(who)).send(method, path, body);
    }

    public Map<String, Object> ok(HttpMethod method, String path, UserAccount who, Object body) {
        ResponseEntity<Map> r = send(method, path, who, body);
        assertThat(r.getStatusCode().is2xxSuccessful()).as(method + " " + path + " → " + r.getBody()).isTrue();
        return r.getBody();
    }

    public int status(HttpMethod method, String path, UserAccount who, Object body) {
        return send(method, path, who, body).getStatusCode().value();
    }

    public void verifiedAdult(UserAccount who) {
        AgeFixtures.verifiedAdult(jdbc, who.getId());
    }
}

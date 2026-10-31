package com.friendlyeats.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * Autenticação, sessão e controle de acesso (RF4).
 *
 * <ul>
 *   <li>Sessão opaca no Valkey (Spring Session), cookie sem Max-Age: fechar o navegador encerra, e uma aba
 *       anônima — que tem cookies próprios — abre deslogada. Nada de token em localStorage.</li>
 *   <li>Login, 2FA, reativação e logout são endpoints próprios em {@code /api/v1/auth}; não há form login,
 *       HTTP Basic nem "lembrar de mim".</li>
 *   <li>CSRF no padrão SPA: cookie {@code XSRF-TOKEN} + cabeçalho {@code X-XSRF-TOKEN} em toda escrita.</li>
 *   <li>Leitura pública dos documentos legais, da taxonomia, das páginas de estabelecimento com as avaliações, dos
 *       posts com os comentários, do "em alta", dos perfis públicos e do catálogo de molduras; o resto exige sessão.
 *       Regras por estado da conta (ativa, pendente, suspensa), de 18+ e de perfil privado ficam nos serviços.</li>
 *   <li>Moderação (RF13): {@code /api/v1/moderation/**} só com {@code ROLE_MODERATOR}. São públicos o catálogo de
 *       motivos, o formulário de denúncia sem conta (STF: canal para não usuários) e a integridade da página do lugar
 *       (alertas ao consumidor e congelamento).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, Clock clock,
                            @Value("${friendly-eats.security.session-absolute-timeout:24h}") Duration absoluteTimeout)
            throws Exception {
        http
                .csrf(csrf -> csrf.spa())
                .securityContext(c -> c.securityContextRepository(securityContextRepository()))
                .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/legal/**", "/api/v1/taxonomy").permitAll()
                        // páginas e avaliações de estabelecimentos são públicas; a regra de 18+ fica no serviço
                        .requestMatchers(HttpMethod.GET, "/api/v1/establishments", "/api/v1/establishments/*",
                                "/api/v1/establishments/*/reviews")
                        .permitAll()
                        // posts, comentários, "em alta" e perfis públicos (RF14, RF12, RF15); 18+ e perfil privado no serviço
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts", "/api/v1/posts/*", "/api/v1/posts/*/comments",
                                "/api/v1/posts/trending", "/api/v1/establishments/*/posts", "/api/v1/users",
                                "/api/v1/users/*/profile", "/api/v1/users/*/posts")
                        .permitAll()
                        // catálogo e recomendações de moldura do post (RF16)
                        .requestMatchers(HttpMethod.GET, "/api/v1/backgrounds/**").permitAll()
                        // moderação (RF13): fila só para moderador; denúncia sem conta e integridade do lugar públicas
                        .requestMatchers("/api/v1/moderation/**").hasRole("MODERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/reports/reasons", "/api/v1/establishments/*/integrity")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports/public").permitAll()
                        .requestMatchers("/api/v1/auth/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(ProblemResponses.unauthorized())
                        .accessDeniedHandler(ProblemResponses.denied()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .addFilterAfter(new AbsoluteSessionTimeoutFilter(absoluteTimeout, clock), SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Argon2id (m = 19 MiB, t = 2, p = 1 — OWASP) dentro de um DelegatingPasswordEncoder ({argon2}…). */
    @Bean
    PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2));
    }

    /** O login é feito pelo AuthService; este bean só impede o usuário em memória padrão do Spring Boot. */
    @Bean
    UserDetailsService noInMemoryUsers() {
        return username -> {
            throw new UsernameNotFoundException("Login apenas por POST /api/v1/auth/login");
        };
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

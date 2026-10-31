package com.friendlyeats.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Cifra do IP e da porta de origem nos registros de acesso do Marco Civil (RF18; Decreto 8.771/2016, art. 13, IV).
 * Ao contrário do HMAC usado no resto do sistema, aqui o IP precisa ser <b>recuperável</b> para uma ordem judicial;
 * por isso AES-256-GCM com chave própria ({@code access-log-v1}) e o id do usuário como dado associado.
 */
@Component
public class AccessLogCipher {

    private final Aead aead;

    public AccessLogCipher(AppSecrets secrets) {
        this.aead = new Aead(secrets.derive("access-log-v1"));
    }

    public byte[] seal(String ip, int port, long userId) {
        return aead.encrypt((ip + "|" + port).getBytes(StandardCharsets.UTF_8), Aead.longAad(userId));
    }

    /** {@code [ip, porta]}. */
    public String[] open(byte[] blob, long userId) {
        String plain = new String(aead.decrypt(blob, Aead.longAad(userId)), StandardCharsets.UTF_8);
        int bar = plain.lastIndexOf('|');
        return new String[]{plain.substring(0, bar), plain.substring(bar + 1)};
    }
}

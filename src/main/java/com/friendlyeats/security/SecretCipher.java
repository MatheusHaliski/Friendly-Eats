package com.friendlyeats.security;

import org.springframework.stereotype.Component;

/**
 * AES-256-GCM para segredos guardados no banco (segredo TOTP). Formato: versão (1 byte) + IV (12) + cifra + tag.
 * O id do usuário entra como dado associado: o segredo de um usuário não decifra se for copiado para outro.
 */
@Component
public class SecretCipher {

    private final Aead aead;

    public SecretCipher(AppSecrets secrets) {
        this.aead = new Aead(secrets.derive("mfa-totp-v1"));
    }

    public byte[] encrypt(byte[] plain, long userId) {
        return aead.encrypt(plain, Aead.longAad(userId));
    }

    public byte[] decrypt(byte[] blob, long userId) {
        try {
            return aead.decrypt(blob, Aead.longAad(userId));
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Segredo não pôde ser decifrado", e);
        }
    }
}

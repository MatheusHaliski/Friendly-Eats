package com.friendlyeats.application.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Versão vigente dos Termos de Uso do Friendly Eats (empresa dona do Friendly Eats) e onde lê-los.
 * O texto fica em docs/legal/TERMOS_DE_USO.md; publicar uma versão nova = mudar
 * {@code friendly-eats.legal.terms-version} — quem aceitou a anterior aceita de novo no próximo login.
 */
@Component
public class LegalDocuments {

    private final String termsVersion;
    private final String termsUrl;
    private final String privacyVersion;

    public LegalDocuments(@Value("${friendly-eats.legal.terms-version}") String termsVersion,
                          @Value("${friendly-eats.legal.terms-url}") String termsUrl,
                          @Value("${friendly-eats.legal.privacy-version}") String privacyVersion) {
        this.termsVersion = termsVersion;
        this.termsUrl = termsUrl;
        this.privacyVersion = privacyVersion;
    }

    public String termsVersion() {
        return termsVersion;
    }

    public String termsUrl() {
        return termsUrl;
    }

    public String privacyVersion() {
        return privacyVersion;
    }
}

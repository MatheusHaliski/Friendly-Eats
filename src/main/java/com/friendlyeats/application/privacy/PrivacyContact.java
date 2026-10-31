package com.friendlyeats.application.privacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Encarregado (DPO) e canais do titular (RF18; LGPD, art. 41, §1º; Resolução CD/ANPD 18/2024), vindos de
 * {@code friendly-eats.legal.*}. Público em {@code GET /api/v1/legal/privacy-contact} e na página {@code /privacidade}.
 * Campo vazio vira {@code null}, e a página diz "a definir" (precisa ser preenchido antes de abrir ao público).
 */
@Component
public class PrivacyContact {

    public record Right(String code, String label, String article, String how) {
    }

    /** O que a página mostra. {@code requestDeadlineDays}: prazo do art. 19, II. */
    public record PrivacyContactView(String controllerName, String dpoName, String dpoEmail, String dpoSubstituteName,
                                     String privacyEmail, int requestDeadlineDays, int exportLinkHours,
                                     List<Right> rights) {
    }

    static final List<Right> RIGHTS = List.of(
            new Right("CONFIRMATION", "Confirmar se tratamos seus dados", "Art. 18, I",
                    "Na hora, pela conta (Configurações) ou por e-mail ao encarregado."),
            new Right("ACCESS", "Acessar e receber uma cópia", "Art. 18, II e V",
                    "Configurações → Conta → Seus dados → Exportar (arquivo pronto em até 15 dias, em geral no mesmo dia)."),
            new Right("CORRECTION", "Corrigir dados", "Art. 18, III", "Configurações → Perfil."),
            new Right("ANONYMIZATION", "Anonimizar, bloquear ou eliminar o desnecessário", "Art. 18, IV",
                    "Por e-mail ao encarregado."),
            new Right("DELETION", "Eliminar os dados tratados com consentimento", "Art. 18, VI",
                    "Configurações → Conta → Excluir conta (30 dias para reativar)."),
            new Right("SHARING", "Saber com quem compartilhamos", "Art. 18, VII", "Por e-mail ao encarregado."),
            new Right("CONSENT", "Saber o que acontece se não consentir e revogar o consentimento", "Art. 18, VIII e IX",
                    "Configurações → Privacidade: cada uso opcional começa desligado e desliga com um toque."),
            new Right("REVIEW", "Pedir revisão de decisão automatizada", "Art. 20",
                    "Por e-mail ao encarregado; a contestação da idade aferida é revisada por uma pessoa."),
            new Right("COMPLAINT", "Reclamar à ANPD", "Art. 18, §1º", "Pelo site da Autoridade Nacional de Proteção de Dados."));

    private final PrivacyContactView view;

    public PrivacyContact(@Value("${friendly-eats.legal.controller-name:}") String controllerName,
                          @Value("${friendly-eats.legal.dpo-name:}") String dpoName,
                          @Value("${friendly-eats.legal.dpo-email:}") String dpoEmail,
                          @Value("${friendly-eats.legal.dpo-substitute-name:}") String substitute,
                          @Value("${friendly-eats.legal.privacy-email:}") String privacyEmail,
                          @Value("${friendly-eats.privacy.export-due:15d}") java.time.Duration due,
                          @Value("${friendly-eats.privacy.export-link-ttl:72h}") java.time.Duration linkTtl) {
        this.view = new PrivacyContactView(blank(controllerName), blank(dpoName), blank(dpoEmail), blank(substitute),
                blank(privacyEmail) == null ? blank(dpoEmail) : blank(privacyEmail), (int) due.toDays(),
                (int) linkTtl.toHours(), RIGHTS);
    }

    public PrivacyContactView view() {
        return view;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}

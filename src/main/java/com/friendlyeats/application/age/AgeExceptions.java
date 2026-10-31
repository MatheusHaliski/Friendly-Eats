package com.friendlyeats.application.age;

/** Erros da verificação de idade (RF17); o {@code ApiExceptionHandler} os traduz em Problem Details. */
public final class AgeExceptions {

    private AgeExceptions() {
    }

    /**
     * 403 com {@code ageVerificationRequired: true}: conta logada, de maior pela data declarada, que ainda não
     * verificou a idade. A tela mostra o botão "Verificar idade".
     */
    public static class VerificationRequired extends RuntimeException {
        public VerificationRequired() {
            super("Conteúdo de bebida alcoólica aparece só depois de verificar a idade.");
        }
    }

    /** 503: nenhum fornecedor de verificação configurado (produção sem contrato ⚖️). */
    public static class ProviderUnavailable extends RuntimeException {
        public ProviderUnavailable() {
            super("A verificação de idade ainda não está disponível. Enquanto isso, o conteúdo de bebida alcoólica "
                    + "fica oculto.");
        }
    }
}

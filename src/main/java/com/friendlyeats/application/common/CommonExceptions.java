package com.friendlyeats.application.common;

import java.util.List;

/** Erros de negócio comuns; o {@code ApiExceptionHandler} os traduz em Problem Details (RFC 9457) com texto em pt-BR. */
public final class CommonExceptions {

    private CommonExceptions() {
    }

    /** 404: inexistente, removido ou rascunho de outra pessoa — não revela qual dos três. */
    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** 401: ação exige usuário identificado. */
    public static class Unauthenticated extends RuntimeException {
        public Unauthenticated() {
            super("Entre na sua conta para continuar.");
        }
    }

    /** 403: conta sem permissão (não ativa) ou ação sobre conteúdo de outra pessoa. */
    public static class Forbidden extends RuntimeException {
        public Forbidden(String message) {
            super(message);
        }
    }

    /** 422: dados inválidos; todos os problemas vão juntos em {@code errors}. */
    public static class Invalid extends RuntimeException {
        private final List<String> errors;

        public Invalid(List<String> errors) {
            super(errors.isEmpty() ? "Dados inválidos." : errors.getFirst());
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }

    /** 409: transição de estado que o recurso não aceita. */
    public static class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }
}

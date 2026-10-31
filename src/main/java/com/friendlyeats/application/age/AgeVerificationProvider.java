package com.friendlyeats.application.age;

import java.time.Instant;
import java.util.Map;

/**
 * Porta para o fornecedor de verificação de idade (RF17). A verificação precisa ser efetiva e sem autodeclaração
 * (ECA Digital, art. 9º, §1º; Decreto 12.880/2026, arts. 19 e 24), por método aceito pela ANPD.
 *
 * <p>⚖️ Ainda não há fornecedor contratado: em dev e it existe só o {@link FakeAgeVerificationProvider}. Em produção,
 * sem um adaptador real, {@code POST /api/v1/me/age-verification/start} responde 503 e o conteúdo de bebida segue
 * oculto para todos (a "versão sem esse conteúdo" do art. 19). O fornecedor vira operador de dados: o contrato
 * precisa proibir guardar a imagem do documento (art. 24, §3º) e usar o dado para outra finalidade (art. 13).
 *
 * <p>Fluxo: {@link #start} abre uma sessão no fornecedor e devolve para onde mandar a pessoa; ao voltar, o app chama
 * {@code /complete} com a referência e o servidor pergunta o resultado ao fornecedor ({@link #result}), de servidor
 * para servidor. O navegador nunca informa o resultado.
 */
public interface AgeVerificationProvider {

    /** Identificador gravado em {@code age_verification.provider}. */
    String id();

    /** Abre a verificação para {@code userId} no fornecedor. */
    Session start(long userId, String returnUrl);

    /**
     * Resultado da sessão {@code reference}; {@code params} são os parâmetros que o fornecedor devolveu na volta
     * (o fake lê {@code simulate}). Sessão ainda em andamento → {@link Outcome#PENDING}.
     */
    Result result(long userId, String reference, Map<String, String> params);

    /** Só o fake diz que é simulação: a tela mostra os botões de teste em vez de redirecionar. */
    default boolean simulated() {
        return false;
    }

    record Session(String reference, String redirectUrl, String instructions, Instant expiresAt) {
    }

    enum Outcome { ADULT, MINOR, FAILED, PENDING }

    /** Só o atributo aferido: nada de documento, foto ou data de nascimento. */
    record Result(Outcome outcome, String method) {
    }
}

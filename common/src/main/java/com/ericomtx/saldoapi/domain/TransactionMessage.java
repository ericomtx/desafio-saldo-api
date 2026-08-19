package com.ericomtx.saldoapi.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Espelha exatamente o payload publicado pelo message-generator (ver
 * docker-compose.yml). Pontos que não são óbvios à primeira vista:
 *
 * - `account.created_at` vem em snake_case no JSON real (confirmado no
 *   PDF do desafio) — por isso o @JsonProperty explícito abaixo. Sem essa
 *   anotação, o Jackson falha ao desserializar qualquer mensagem real.
 * - `transaction.timestamp` é em MICROSSEGUNDOS (time.Now().UnixMicro() no
 *   gerador Go) — não confundir com `account.createdAt`, que é em segundos.
 * - Quando `transaction.status` é REJECTED, `account.balance` já vem com o
 *   saldo ANTERIOR (a transação não alterou nada de verdade) — então a
 *   lógica de ingestão não precisa de nenhum tratamento especial pra esse
 *   caso: basta sempre persistir o `account.balance` que veio na mensagem,
 *   respeitando a regra de timestamp da ADR 0002. Ver ADR 0002 para
 *   detalhes.
 */
@JsonIgnoreProperties(ignoreUnknown = true) // defensivo: não quebra se o payload ganhar campos novos
public record TransactionMessage(
    TransactionData transaction,
    AccountData account
) {
    public record TransactionData(
        String id,
        String type,
        double amount,
        String currency,
        String status,
        long timestamp // microssegundos
    ) {}

    public record AccountData(
        String id,
        String owner,
        @JsonProperty("created_at") String createdAt, // segundos, snake_case no JSON
        String status,
        BalanceData balance
    ) {}

    public record BalanceData(
        double amount,
        String currency
    ) {}
}

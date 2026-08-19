package com.ericomtx.saldoapi.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload do message-generator (ver docker-compose.yml). Dois detalhes que
 * já me pegaram uma vez: created_at é snake_case no JSON, e timestamp vem
 * em microssegundos, não segundos. REJECTED já vem com o balance antigo,
 * então não precisa tratar status aqui — ver ADR 0002.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
package com.ericomtx.saldoapi.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Formato de resposta exigido pelo desafio: id, owner, balance{amount,currency}, updated_at (ISO8601). */
public record BalanceResponse(
    String id,
    String owner,
    Money balance,
    @JsonProperty("updated_at") Instant updatedAt
) {
    public record Money(double amount, String currency) {}
}

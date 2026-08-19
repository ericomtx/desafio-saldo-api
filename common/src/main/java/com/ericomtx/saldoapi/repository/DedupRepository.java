package com.ericomtx.saldoapi.repository;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.Map;

@Repository
public class DedupRepository {

    private static final String TABLE_NAME = "transacoes-processadas";
    private static final long TTL_HOURS = 24; // ADR 0003: janela suficiente pra cobrir reentrega tardia do SQS

    private final DynamoDbClient dynamoDb;

    public DedupRepository(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    /**
     * Só verifica se essa transação já foi processada — não marca nada.
     * Separado de {@link #markAsProcessed} de propósito: marcar só deve
     * acontecer DEPOIS que a escrita do saldo for confirmada, nunca antes
     * (ver ADR 0003 — marcar antes de escrever "envenena" a transação
     * permanentemente se a escrita falhar no meio do caminho).
     */
    @Retry(name = "dynamoRead")
    public boolean isAlreadyProcessed(String transactionId) {
        var result = dynamoDb.getItem(GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of("transactionId", AttributeValue.fromS(transactionId)))
            .consistentRead(true)
            .build());
        return result.hasItem();
    }

    /**
     * Marca a transação como processada. Só deve ser chamado DEPOIS que a
     * escrita do saldo já foi confirmada com sucesso.
     */
    @Retry(name = "dynamoWrite")
    public void markAsProcessed(String transactionId) {
        long expiresAt = Instant.now().plusSeconds(TTL_HOURS * 3600).getEpochSecond();

        dynamoDb.putItem(PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(Map.of(
                "transactionId", AttributeValue.fromS(transactionId),
                "expiresAt", AttributeValue.fromN(String.valueOf(expiresAt))
            ))
            .build());
    }
}

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
    private static final long TTL_HOURS = 24;

    private final DynamoDbClient dynamoDb;

    public DedupRepository(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    // Não marca nada, só checa. markAsProcessed só é chamado depois que o
    // saldo já foi gravado com sucesso — se marcasse antes e a escrita
    // falhasse, a mensagem reentregue seria ignorada pra sempre.
    @Retry(name = "dynamoRead")
    public boolean isAlreadyProcessed(String transactionId) {
        var result = dynamoDb.getItem(GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of("transactionId", AttributeValue.fromS(transactionId)))
            .consistentRead(true)
            .build());
        return result.hasItem();
    }

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
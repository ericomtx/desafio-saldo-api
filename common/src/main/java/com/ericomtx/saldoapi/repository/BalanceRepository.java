package com.ericomtx.saldoapi.repository;

import com.ericomtx.saldoapi.domain.BalanceResponse;
import com.ericomtx.saldoapi.domain.TransactionMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Repository
public class BalanceRepository {

    private static final Logger LOG = LoggerFactory.getLogger(BalanceRepository.class);
    private static final String TABLE_NAME = "saldos";

    private final DynamoDbClient dynamoDb;

    public BalanceRepository(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    // Só atualiza se o timestamp for mais novo que o que já está salvo — SQS
    // Standard não garante ordem, então isso evita sobrescrever com dado velho.
    @Retry(name = "dynamoWrite")
    @CircuitBreaker(name = "dynamoWrite")
    public void applyIfNewer(TransactionMessage message) {
        var tx = message.transaction();
        var account = message.account();

        try {
            dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("accountId", AttributeValue.fromS(account.id())))
                .updateExpression("SET balanceAmount = :amount, balanceCurrency = :currency, " +
                    "#owner = :owner, updatedAtMicros = :ts")
                .conditionExpression("attribute_not_exists(updatedAtMicros) OR updatedAtMicros < :ts")
                .expressionAttributeNames(Map.of(
                    "#owner", "owner" // owner é reservado no DynamoDB, precisa de alias
                ))
                .expressionAttributeValues(Map.of(
                    ":amount", AttributeValue.fromN(String.valueOf(account.balance().amount())),
                    ":currency", AttributeValue.fromS(account.balance().currency()),
                    ":owner", AttributeValue.fromS(account.owner()),
                    ":ts", AttributeValue.fromN(String.valueOf(tx.timestamp()))
                ))
                .build());
        } catch (ConditionalCheckFailedException e) {
            // mensagem chegou atrasada, ignora
            LOG.debug("Mensagem descartada por estar desatualizada — accountId={}, txTimestamp={}",
                account.id(), tx.timestamp());
        }
    }

    @Retry(name = "dynamoRead")
    public BalanceResponse findByAccountId(String accountId) {
        var result = dynamoDb.getItem(GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of("accountId", AttributeValue.fromS(accountId)))
            .consistentRead(true)
            .build());

        if (!result.hasItem()) {
            return null;
        }

        var item = result.item();
        long updatedAtMicros = Long.parseLong(item.get("updatedAtMicros").n());

        OffsetDateTime updatedAt = Instant.EPOCH.plus(updatedAtMicros, ChronoUnit.MICROS)
            .truncatedTo(ChronoUnit.MILLIS)
            .atOffset(ZoneOffset.of("-03:00"));

        return new BalanceResponse(
            accountId,
            item.get("owner").s(),
            new BalanceResponse.Money(
                Double.parseDouble(item.get("balanceAmount").n()),
                item.get("balanceCurrency").s()
            ),
            updatedAt
        );
    }
}
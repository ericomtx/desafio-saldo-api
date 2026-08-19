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

    /**
     * Grava o saldo respeitando a ADR 0002: só atualiza se o timestamp da
     * transação for mais novo que o que já está salvo. Se a condição
     * falhar, a escrita é descartada como no-op — não é erro, é o
     * comportamento esperado pra uma mensagem que chegou fora de ordem.
     *
     * Retry com jitter + circuit breaker (ADR 0005, config em
     * application.yml — nomes de instância "dynamoWrite"): protege contra
     * falhas passageiras de rede/throttling do DynamoDB sem martelar o
     * serviço durante uma indisponibilidade real.
     */
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
                    "#owner", "owner" // "owner" é palavra reservada no DynamoDB — precisa de alias via ExpressionAttributeNames
                ))
                .expressionAttributeValues(Map.of(
                    ":amount", AttributeValue.fromN(String.valueOf(account.balance().amount())),
                    ":currency", AttributeValue.fromS(account.balance().currency()),
                    ":owner", AttributeValue.fromS(account.owner()),
                    ":ts", AttributeValue.fromN(String.valueOf(tx.timestamp()))
                ))
                .build());
        } catch (ConditionalCheckFailedException e) {
            // Mensagem mais antiga que o que já está salvo — descarta, é esperado (ADR 0002).
            LOG.debug("Mensagem descartada por estar desatualizada — accountId={}, txTimestamp={}",
                account.id(), tx.timestamp());
        }
    }

    @Retry(name = "dynamoRead")
    public BalanceResponse findByAccountId(String accountId) {
        var result = dynamoDb.getItem(GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of("accountId", AttributeValue.fromS(accountId)))
            .consistentRead(true) // consistência forte dentro do DynamoDB — ver ADR 0004
            .build());

        if (!result.hasItem()) {
            return null;
        }

        var item = result.item();
        long updatedAtMicros = Long.parseLong(item.get("updatedAtMicros").n());

        return new BalanceResponse(
            accountId,
            item.get("owner").s(),
            new BalanceResponse.Money(
                Double.parseDouble(item.get("balanceAmount").n()),
                item.get("balanceCurrency").s()
            ),
            Instant.EPOCH.plus(updatedAtMicros, ChronoUnit.MICROS)
        );
    }
}

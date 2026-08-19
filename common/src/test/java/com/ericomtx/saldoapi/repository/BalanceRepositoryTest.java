package com.ericomtx.saldoapi.repository;

import com.ericomtx.saldoapi.domain.TransactionMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BalanceRepositoryTest {

    private DynamoDbClient dynamoDb;
    private BalanceRepository repository;

    @BeforeEach
    void setUp() {
        dynamoDb = mock(DynamoDbClient.class);
        repository = new BalanceRepository(dynamoDb);
    }

    private TransactionMessage messageWith(String accountId, double balance, long timestampMicros) {
        return new TransactionMessage(
            new TransactionMessage.TransactionData(
                "tx-" + timestampMicros, "CREDIT", 10.0, "BRL", "APPROVED", timestampMicros),
            new TransactionMessage.AccountData(
                accountId, "owner-1", "1634874339", "ENABLED",
                new TransactionMessage.BalanceData(balance, "BRL"))
        );
    }

    @Test
    void deveIncluirCondicaoDeTimestampNaEscrita() {
        var message = messageWith("acc-1", 150.0, 1_700_000_000_000_000L);

        repository.applyIfNewer(message);

        var captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDb).updateItem(captor.capture());

        var request = captor.getValue();
        assertThat(request.conditionExpression())
            .contains("attribute_not_exists(updatedAtMicros)")
            .contains("updatedAtMicros < :ts");
        assertThat(request.expressionAttributeValues().get(":ts").n())
            .isEqualTo("1700000000000000");
    }

    @Test
    void deveDescartarSilenciosamenteMensagemForaDeOrdem() {
        // mensagem antiga chegando depois de uma nova — não pode dar erro
        var message = messageWith("acc-1", 100.0, 1_600_000_000_000_000L);

        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
            .thenThrow(ConditionalCheckFailedException.builder().build());

        repository.applyIfNewer(message);

        verify(dynamoDb).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void transacaoRejeitadaUsaMesmoFluxoDeEscritaQueAprovada() {
        // REJECTED já vem com o balance anterior, não tem branch especial pra isso
        var rejectedMessage = new TransactionMessage(
            new TransactionMessage.TransactionData(
                "tx-rejected", "DEBIT", 500.0, "BRL", "REJECTED", 1_700_000_000_000_001L),
            new TransactionMessage.AccountData(
                "acc-2", "owner-2", "1634874339", "ENABLED",
                new TransactionMessage.BalanceData(183.12, "BRL")) // saldo anterior
        );

        repository.applyIfNewer(rejectedMessage);

        var captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDb).updateItem(captor.capture());
        assertThat(captor.getValue().expressionAttributeValues().get(":amount").n())
            .isEqualTo("183.12");
    }
}
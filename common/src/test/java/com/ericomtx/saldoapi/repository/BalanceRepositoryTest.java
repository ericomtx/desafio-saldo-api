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
        // ADR 0002: toda escrita precisa checar se é mais recente que o que já está salvo
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
        // Corner case: mensagem mais antiga chega depois de uma mais nova.
        // O DynamoDB rejeita a condição — a aplicação não deve propagar isso como erro.
        var message = messageWith("acc-1", 100.0, 1_600_000_000_000_000L);

        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
            .thenThrow(ConditionalCheckFailedException.builder().build());

        // não deve lançar exceção — é comportamento esperado, não falha
        repository.applyIfNewer(message);

        verify(dynamoDb).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void transacaoRejeitadaUsaMesmoFluxoDeEscritaQueAprovada() {
        // Corner case (descoberto no docker-compose.yml, não no PDF): quando
        // status é REJECTED, account.balance já vem com o saldo anterior —
        // então não deve haver nenhum branch especial de código para isso.
        // Esse teste documenta essa decisão: o método nem recebe o status,
        // só o balance já resolvido.
        var rejectedMessage = new TransactionMessage(
            new TransactionMessage.TransactionData(
                "tx-rejected", "DEBIT", 500.0, "BRL", "REJECTED", 1_700_000_000_000_001L),
            new TransactionMessage.AccountData(
                "acc-2", "owner-2", "1634874339", "ENABLED",
                new TransactionMessage.BalanceData(183.12, "BRL")) // saldo ANTERIOR, não alterado
        );

        repository.applyIfNewer(rejectedMessage);

        var captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDb).updateItem(captor.capture());
        assertThat(captor.getValue().expressionAttributeValues().get(":amount").n())
            .isEqualTo("183.12");
    }
}

package com.ericomtx.saldoapi.consumer;

import com.ericomtx.saldoapi.repository.BalanceRepository;
import com.ericomtx.saldoapi.repository.DedupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IngestionServiceTest {

    private DedupRepository dedupRepository;
    private BalanceRepository balanceRepository;
    private IngestionService service;

    private static final String PAYLOAD = """
        {
          "transaction": {"id": "tx-1", "type": "CREDIT", "amount": 97.07, "currency": "BRL", "status": "APPROVED", "timestamp": 1751641364589998},
          "account": {"id": "acc-1", "owner": "owner-1", "created_at": "1634874339", "status": "ENABLED", "balance": {"amount": 183.12, "currency": "BRL"}}
        }
        """;

    @BeforeEach
    void setUp() {
        dedupRepository = mock(DedupRepository.class);
        balanceRepository = mock(BalanceRepository.class);
        service = new IngestionService(new ObjectMapper(), dedupRepository, balanceRepository);
    }

    @Test
    void deveProcessarTransacaoNovaNormalmente() {
        when(dedupRepository.isAlreadyProcessed("tx-1")).thenReturn(false);

        service.processMessage(PAYLOAD);

        verify(balanceRepository).applyIfNewer(any());
        verify(dedupRepository).markAsProcessed("tx-1");
    }

    @Test
    void deveIgnorarTransacaoDuplicadaSemChamarOBanco() {
        // Corner case (ADR 0003): SQS Standard pode entregar a mesma mensagem
        // mais de uma vez. A segunda entrega não deve gerar escrita nenhuma.
        when(dedupRepository.isAlreadyProcessed("tx-1")).thenReturn(true);

        service.processMessage(PAYLOAD);

        verify(balanceRepository, never()).applyIfNewer(any());
        verify(dedupRepository, never()).markAsProcessed(any());
    }

    @Test
    void naoDeveMarcarComoProcessadaSeAEscritaFalhar() {
        // Corner case crítico (ADR 0003): se a escrita do saldo falhar, a
        // transação NÃO pode ser marcada como processada — senão a mensagem
        // reentregue seria ignorada pra sempre sem o saldo nunca ser aplicado.
        when(dedupRepository.isAlreadyProcessed("tx-1")).thenReturn(false);
        doThrow(new RuntimeException("Falha simulada no DynamoDB"))
            .when(balanceRepository).applyIfNewer(any());

        try {
            service.processMessage(PAYLOAD);
            org.junit.jupiter.api.Assertions.fail("Deveria ter propagado a exceção");
        } catch (RuntimeException e) {
            // esperado
        }

        verify(dedupRepository, never()).markAsProcessed(any());
    }

    @Test
    void deveLancarExcecaoParaPayloadMalformado() {
        // Corner case: mensagem com JSON inválido não deve travar o consumer
        // silenciosamente — precisa propagar erro para não ser deletada da
        // fila (permitindo retry / eventual DLQ, ver ADR 0005).
        try {
            service.processMessage("{ isso não é um JSON válido");
            org.junit.jupiter.api.Assertions.fail("Deveria ter lançado IngestionException");
        } catch (IngestionService.IngestionException e) {
            // esperado
        }

        verifyNoInteractions(balanceRepository);
    }
}

package com.ericomtx.saldoapi.consumer;

import com.ericomtx.saldoapi.domain.TransactionMessage;
import com.ericomtx.saldoapi.repository.BalanceRepository;
import com.ericomtx.saldoapi.repository.DedupRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionService.class);

    private final ObjectMapper objectMapper;
    private final DedupRepository dedupRepository;
    private final BalanceRepository balanceRepository;

    public IngestionService(ObjectMapper objectMapper, DedupRepository dedupRepository,
                             BalanceRepository balanceRepository) {
        this.objectMapper = objectMapper;
        this.dedupRepository = dedupRepository;
        this.balanceRepository = balanceRepository;
    }

    /**
     * Processa uma mensagem crua da fila. Não precisa checar
     * `transaction.status` explicitamente — se for REJECTED, o
     * `account.balance` na mensagem já vem com o saldo anterior (o gerador
     * garante isso), então a escrita condicional da ADR 0002 trata os dois
     * casos (APPROVED/REJECTED) de forma idêntica e correta. Ver nota na
     * ADR 0002 sobre esse detalhe.
     *
     * IMPORTANTE (ADR 0003): a transação só é marcada como processada
     * DEPOIS que a escrita do saldo é confirmada — nunca antes. Marcar
     * antes "envenenaria" a transação permanentemente caso a escrita
     * falhasse no meio do caminho (a mensagem seria reentregue, veria que
     * "já foi processada", e nunca teria o saldo aplicado de verdade).
     */
    public void processMessage(String rawBody) {
        TransactionMessage message;
        try {
            message = objectMapper.readValue(rawBody, TransactionMessage.class);
        } catch (Exception e) {
            LOG.error("Mensagem malformada, não é possível parsear: {}", rawBody, e);
            throw new IngestionException("Payload inválido", e);
        }

        String transactionId = message.transaction().id();

        if (dedupRepository.isAlreadyProcessed(transactionId)) {
            LOG.debug("Transação {} já processada anteriormente — ignorando duplicata (ADR 0003)",
                transactionId);
            return;
        }

        balanceRepository.applyIfNewer(message); // se lançar exceção, a transação NÃO é marcada — reentrega tentará de novo

        dedupRepository.markAsProcessed(transactionId);
    }

    public static class IngestionException extends RuntimeException {
        public IngestionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

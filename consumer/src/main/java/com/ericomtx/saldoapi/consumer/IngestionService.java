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

    // Não checa status explicitamente — REJECTED já vem com o balance
    // anterior no payload, então applyIfNewer trata os dois casos igual.
    // Marca como processada só depois da escrita ter sucesso (se marcasse
    // antes e a escrita falhasse, a reentrega seria ignorada pra sempre).
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
            LOG.debug("Transação {} já processada anteriormente — ignorando duplicata", transactionId);
            return;
        }

        balanceRepository.applyIfNewer(message);

        dedupRepository.markAsProcessed(transactionId);
    }

    public static class IngestionException extends RuntimeException {
        public IngestionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
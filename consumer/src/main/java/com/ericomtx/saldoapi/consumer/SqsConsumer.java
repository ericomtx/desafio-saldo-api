package com.ericomtx.saldoapi.consumer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Consome a fila em lotes de até 10 mensagens (o máximo que o SQS permite
 * por chamada — ADR 0006), usando long polling para não gastar chamadas à
 * toa quando a fila está vazia.
 *
 * A escala real vem de rodar MÚLTIPLAS INSTÂNCIAS desse serviço em
 * paralelo (várias tasks ECS, ver diagrama de deploy) — o SQS já distribui
 * mensagens entre consumers concorrentes com segurança, então não há
 * necessidade de paralelismo adicional dentro de uma única instância além
 * do processamento do próprio lote.
 */
@Component
public class SqsConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SqsConsumer.class);

    private final SqsClient sqsClient;
    private final IngestionService ingestionService;

    @Value("${app.sqs.queue-name}")
    String queueName;

    private final ExecutorService pollingThread = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    private String queueUrl;

    public SqsConsumer(SqsClient sqsClient, IngestionService ingestionService) {
        this.sqsClient = sqsClient;
        this.ingestionService = ingestionService;
    }

    @PostConstruct
    void start() {
        queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
            .queueUrl();
        LOG.info("Iniciando consumer para a fila: {}", queueUrl);
        pollingThread.submit(this::pollLoop);
    }

    @PreDestroy
    void stop() {
        running = false;
        pollingThread.shutdown();
    }

    private void pollLoop() {
        while (running) {
            try {
                var response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10) // máximo permitido por chamada — ADR 0006
                    .waitTimeSeconds(10)     // long polling — evita chamadas vazias repetidas
                    .build());

                if (!response.messages().isEmpty()) {
                    processBatch(response.messages());
                }
            } catch (Exception e) {
                LOG.error("Erro no loop de polling da fila — tentando novamente", e);
            }
        }
    }

    private void processBatch(List<Message> messages) {
        List<DeleteMessageBatchRequestEntry> toDelete = new ArrayList<>();

        for (Message message : messages) {
            try {
                ingestionService.processMessage(message.body());
                toDelete.add(DeleteMessageBatchRequestEntry.builder()
                    .id(message.messageId())
                    .receiptHandle(message.receiptHandle())
                    .build());
            } catch (Exception e) {
                // Não deleta — a mensagem volta a ficar visível após o visibility timeout
                // e será reentregue. Se falhar repetidamente, o próprio SQS move para a
                // DLQ com base no redrivePolicy configurado na fila (ADR 0005).
                LOG.error("Falha ao processar mensagem {} — será reentregue", message.messageId(), e);
            }
        }

        if (!toDelete.isEmpty()) {
            sqsClient.deleteMessageBatch(DeleteMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(toDelete)
                .build());
        }
    }
}

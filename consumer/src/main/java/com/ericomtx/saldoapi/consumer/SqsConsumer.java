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

// Lê a fila em lotes de 10 (máximo do SQS) com long polling. Escala
// rodando várias instâncias em paralelo — o SQS já cuida da distribuição.
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
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(10)
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
                // não deleta — volta a ficar visível e é reentregue. Depois
                // de N tentativas o SQS move sozinho pra DLQ.
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
package com.ericomtx.saldoapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * O worker de ingestão — consome a fila SQS e escreve no DynamoDB. Sem
 * servidor HTTP: como spring-boot-starter-web não está no classpath deste
 * módulo, o Spring Boot detecta automaticamente que a aplicação é do tipo
 * NONE (sem servlet container). O processo continua rodando porque
 * SqsConsumer inicia uma thread não-daemon no @PostConstruct.
 */
@SpringBootApplication
public class SaldoApiConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaldoApiConsumerApplication.class, args);
    }
}

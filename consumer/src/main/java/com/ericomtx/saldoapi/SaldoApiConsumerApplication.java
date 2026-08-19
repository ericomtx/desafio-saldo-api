package com.ericomtx.saldoapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Worker de ingestão. Sem web starter, então sem servidor HTTP — o
// processo fica de pé pela thread do SqsConsumer.
@SpringBootApplication
public class SaldoApiConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaldoApiConsumerApplication.class, args);
    }
}
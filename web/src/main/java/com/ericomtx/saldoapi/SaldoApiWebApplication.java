package com.ericomtx.saldoapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A API REST — só expõe GET /balances/{accountId}. Não consome a fila.
 * Component scan do pacote base com.ericomtx.saldoapi pega automaticamente os
 * beans de saldo-api-common (BalanceRepository, AwsClientsConfig, etc),
 * já que estão sob o mesmo pacote base, mesmo vindo de um JAR diferente.
 */
@SpringBootApplication
public class SaldoApiWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaldoApiWebApplication.class, args);
    }
}

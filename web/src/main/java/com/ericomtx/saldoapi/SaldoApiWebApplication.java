package com.ericomtx.saldoapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Só a API REST, não consome fila.
@SpringBootApplication
public class SaldoApiWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(SaldoApiWebApplication.class, args);
    }
}
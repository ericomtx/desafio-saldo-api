package com.ericomtx.saldoapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declarado explicitamente porque nem web nem consumer dependem de
 * spring-boot-starter-web — a autoconfiguração padrão do Spring Boot pro
 * ObjectMapper depende de uma classe (Jackson2ObjectMapperBuilder) que só
 * vem com spring-web, então nunca dispara aqui.
 *
 * JavaTimeModule é necessário pro Jackson serializar/desserializar
 * java.time.Instant (usado em BalanceResponse.updatedAt) — sem isso, o
 * Jackson lança erro ao tentar serializar qualquer tipo de data/hora do
 * Java 8+.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
package com.ericomtx.saldoapi.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMessageTest {

    // Payload exatamente como veio no PDF do desafio — inclui o campo
    // "created_at" em snake_case, que é a causa raiz do bug corrigido aqui.
    private static final String PAYLOAD_REAL = """
        {
          "transaction": {
            "id": "8e8ae808-b154-48b5-9f3e-553935cc4543",
            "type": "CREDIT",
            "amount": 97.07,
            "currency": "BRL",
            "status": "APPROVED",
            "timestamp": 1751641364589998
          },
          "account": {
            "id": "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
            "owner": "315e3cfe-f4af-4cd2-b298-a449e614349a",
            "created_at": "1634874339",
            "status": "ENABLED",
            "balance": {
              "amount": 183.12,
              "currency": "BRL"
            }
          }
        }
        """;

    @Test
    void deveDesserializarOPayloadRealSemErro() throws Exception {
        var mapper = new ObjectMapper();

        var message = mapper.readValue(PAYLOAD_REAL, TransactionMessage.class);

        assertThat(message.transaction().id()).isEqualTo("8e8ae808-b154-48b5-9f3e-553935cc4543");
        assertThat(message.transaction().timestamp()).isEqualTo(1751641364589998L);
        assertThat(message.account().id()).isEqualTo("5b19c8b6-0cc4-4c72-a989-0c2ee15fa975");
        // Este é o campo que estava quebrado: "created_at" (snake_case) no JSON
        // precisa mapear pro campo Java `createdAt`.
        assertThat(message.account().createdAt()).isEqualTo("1634874339");
        assertThat(message.account().balance().amount()).isEqualTo(183.12);
    }
}

package com.ericomtx.saldoapi.web;

import com.ericomtx.saldoapi.domain.BalanceResponse;
import com.ericomtx.saldoapi.repository.BalanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BalanceResource.class)
class BalanceResourceTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    BalanceRepository balanceRepository;

    @Test
    void deveRetornar200ComSaldoQuandoContaExiste() throws Exception {
        when(balanceRepository.findByAccountId("acc-1")).thenReturn(
            new BalanceResponse("acc-1", "owner-1",
                new BalanceResponse.Money(183.12, "BRL"), OffsetDateTime.parse("2025-07-04T18:04:13.433-03:00"))
        );

        mockMvc.perform(get("/balances/acc-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("acc-1"))
            .andExpect(jsonPath("$.owner").value("owner-1"))
            .andExpect(jsonPath("$.balance.amount").value(183.12))
            .andExpect(jsonPath("$.balance.currency").value("BRL"));
    }

    @Test
    void deveRetornar404QuandoContaNaoExiste() throws Exception {
        // Corner case: conta nunca recebeu nenhuma transação ainda
        when(balanceRepository.findByAccountId("conta-inexistente")).thenReturn(null);

        mockMvc.perform(get("/balances/conta-inexistente"))
            .andExpect(status().isNotFound());
    }
}
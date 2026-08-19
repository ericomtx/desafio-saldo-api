package com.ericomtx.saldoapi.web;

import com.ericomtx.saldoapi.domain.BalanceResponse;
import com.ericomtx.saldoapi.repository.BalanceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BalanceResource {

    private final BalanceRepository balanceRepository;

    public BalanceResource(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    @GetMapping("/balances/{accountId}")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable("accountId") String accountId) {
        BalanceResponse balance = balanceRepository.findByAccountId(accountId);

        if (balance == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(balance);
    }
}
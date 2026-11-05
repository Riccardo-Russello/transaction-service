package it.russello.contocorrente.controller;

import it.russello.contocorrente.repository.AccountTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalTransactionController {
    private final AccountTransactionRepository accountTransactionRepository;

    @GetMapping("/internal/transactions/by-account/{id}/exists")
    @PreAuthorize("hasRole('admin')")
    public boolean existsByAccount(@PathVariable("id") Long id) {
        return accountTransactionRepository.existsByAccount_Id(id);
    }
}

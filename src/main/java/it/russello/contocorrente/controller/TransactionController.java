package it.russello.contocorrente.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.russello.contocorrente.dto.*;
import it.russello.contocorrente.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Transazioni", description = "Gestione dei prelievi, versamenti e trasferimenti")
@RestController
@RequestMapping("/api/account/{id}/transaction")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @Operation(summary = "Registra una transazione sul conto")
    @PostMapping
    public ResponseEntity<AccountTransactionResponse> createTransaction(
            @PathVariable("id") Long accountId,
            @Valid @RequestBody AccountTransactionRequest request) {
        AccountTransactionResponse response = transactionService.createTransaction(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Trasferisce denaro da un conto a un altro conto")
    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @PathVariable("id") Long sourceAccountId,
            @Valid @RequestBody TransferRequest request) {
        TransferResponse response = transactionService.transfer(sourceAccountId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Visualizza le ultime 5 operazioni del conto")
    @GetMapping
    public ResponseEntity<List<TransactionListResponse>>
    getLastFiveTransactions(@PathVariable("id") Long accountId) {
        return ResponseEntity.status(HttpStatus.OK).body(transactionService.getLastFiveTransactions(accountId));
    }
}

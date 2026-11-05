package it.russello.contocorrente.dto;

import it.russello.contocorrente.entity.AccountTransaction;
import it.russello.contocorrente.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionListResponse(Long id,
                                      TransactionType type,
                                      BigDecimal amount,
                                      LocalDateTime occurredAt,
                                      Long sourceAccountId,
                                      Long destinationAccountId) {
    public static TransactionListResponse from(AccountTransaction transaction) {
        return new TransactionListResponse(transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getOccurredAt(),
                transaction.getAccount().getId(),
                transaction.getDestinationAccount() == null ? null : transaction.getDestinationAccount().getId());
    }
}

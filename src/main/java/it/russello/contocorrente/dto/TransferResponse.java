package it.russello.contocorrente.dto;

import it.russello.contocorrente.entity.AccountTransaction;
import it.russello.contocorrente.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(Long id,
                               TransactionType type,
                               BigDecimal amount,
                               LocalDateTime occurredAt,
                               BigDecimal balance,
                               Long sourceAccountId,
                               Long destinationAccountId,
                               BigDecimal destinationBalance) {
    public static TransferResponse from(AccountTransaction transaction,
                                        BigDecimal balance,
                                        BigDecimal destinationBalance) {
        return new TransferResponse(transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getOccurredAt(),
                balance,
                transaction.getAccount().getId(),
                transaction.getDestinationAccount() == null ?
                        null : transaction.getDestinationAccount().getId(),
                destinationBalance);
    }
}

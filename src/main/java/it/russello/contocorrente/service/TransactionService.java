package it.russello.contocorrente.service;

import it.russello.contocorrente.dto.*;
import it.russello.contocorrente.entity.Account;
import it.russello.contocorrente.entity.AccountTransaction;
import it.russello.contocorrente.entity.TransactionType;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.repository.AccountRepository;
import it.russello.contocorrente.repository.AccountTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999999.99");
    private final AccountRepository accountRepository;
    private final AccountTransactionRepository accountTransactionRepository;

    @Transactional
    @PreAuthorize("""
            hasRole('admin') or (hasRole('user') and @accountSecurity.isOwner(#accountId, authentication))
            """)
    public AccountTransactionResponse createTransaction(@P("accountId") Long accountId, AccountTransactionRequest request) {     // createAccountTransaction (spostare nel service separato)
        if (accountId == null || accountId <= 0) {
            throw invalidTransaction("Id del conto deve essere positivo");
        }
        if (request.type() == TransactionType.TRANSFER) {
            TransferResponse response = transfer(accountId, new TransferRequest(request.to(), request.amount()));
            return new AccountTransactionResponse(response.id(),
                    response.type(),
                    response.amount(),
                    response.occurredAt(),
                    response.balance(),
                    response.sourceAccountId(),
                    response.destinationAccountId(),
                    response.destinationBalance());
        }
        BigDecimal amount = request.amount().setScale(2);
        Account account = requireAccountForUpdate(accountId);
        BigDecimal newBalance;

        switch (request.type()) {
            case DEPOSIT -> {
                newBalance = account.getBalance().add(amount);
                if (newBalance.compareTo(MAX_MONEY) > 0) {
                    throw new ApiException(HttpStatus.CONFLICT, "BALANCE_LIMIT_EXCEEDED", "Versamento supera la capacità del saldo");
                }
            }
            case WITHDRAWAL -> {
                if (account.getBalance().compareTo(amount) < 0) {
                    throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE", "Saldo insufficiente");
                }
                newBalance = account.getBalance().subtract(amount);
            }
            default -> throw invalidTransaction("Movimento non valido");
        }

        account.setBalance(newBalance);
        Account savedAccount = accountRepository.save(account);
        AccountTransaction transaction = new AccountTransaction(savedAccount, request.type(), amount);
        AccountTransaction savedTransaction = accountTransactionRepository.saveAndFlush(transaction);
        return AccountTransactionResponse.from(savedTransaction, savedAccount.getBalance(), null);
    }

    @Transactional
    @PreAuthorize("""
            hasRole('admin') or (hasRole('user') and @accountSecurity.isOwner(#sourceAccountId, authentication))
            """)
    public TransferResponse transfer(@P("sourceAccountId") Long sourceAccountId, TransferRequest request) {
        Long destinationAccountId = request.destinationAccountId();
        if (sourceAccountId == null || sourceAccountId <= 0) {
            throw invalidTransaction("Id dei conti devono essere positivi");
        }
        if (sourceAccountId.equals(destinationAccountId)) {
            throw invalidTransaction("Id dei conti sorgente e destinazione devono essere diversi");
        }
        BigDecimal amount = request.amount().setScale(2);
        boolean sourceFirst = sourceAccountId < destinationAccountId;
        Account first = requireAccountForUpdate(
                sourceFirst ? sourceAccountId : destinationAccountId);
        Account second = requireAccountForUpdate(
                sourceFirst ? destinationAccountId : sourceAccountId);
        Account source = sourceFirst ? first : second;
        Account destination = sourceFirst ? second : first;

        if (source.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE", "Saldo insufficiente al trasferimento");
        }
        BigDecimal sourceBalance = source.getBalance().subtract(amount);
        BigDecimal destinationBalance = destination.getBalance().add(amount);

        if (destinationBalance.compareTo(MAX_MONEY) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BALANCE_LIMIT_EXCEEDED", "Il trasferimento supera la capacità del saldo");
        }
        source.setBalance(sourceBalance);
        destination.setBalance(destinationBalance);

        AccountTransaction transaction = new AccountTransaction(source, destination, amount);
        AccountTransaction savedTransaction = accountTransactionRepository.save(transaction);
        return TransferResponse.from(savedTransaction, sourceBalance, destinationBalance);
    }

    @PreAuthorize("""
            hasRole('admin') or (hasRole('user') and @accountSecurity.isOwner(#accountId, authentication))
            """)
    public List<TransactionListResponse> getLastFiveTransactions(@P("accountId") Long accountId) {
        requireAccount(accountId);
        return accountTransactionRepository
                .findTop5ByAccount_IdOrDestinationAccount_IdOrderByOccurredAtDescIdDesc(accountId, accountId)
                .stream()
                .map(TransactionListResponse::from)
                .toList();
    }

    private Account requireAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ACCOUNT_NOT_FOUND",
                        "Conto non trovato"
                ));
    }

    private ApiException invalidTransaction(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_TRANSACTION_DATA", message);
    }

    private Account requireAccountForUpdate(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Conto non trovato"));
    }
}

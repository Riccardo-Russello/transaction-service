package it.russello.contocorrente.service;

import it.russello.contocorrente.dto.*;
import it.russello.contocorrente.entity.Account;
import it.russello.contocorrente.entity.AccountTransaction;
import it.russello.contocorrente.entity.Person;
import it.russello.contocorrente.entity.TransactionType;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.repository.AccountRepository;
import it.russello.contocorrente.repository.AccountTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 10, 12, 0);
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountTransactionRepository accountTransactionRepository;
    @InjectMocks
    private TransactionService transactionService;

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static Account account(Long id, String balance) {
        Person owner = new Person("Mario", "Rossi", LocalDate.of(1995, 5, 15));
        Account account = new Account(owner, "ACC-" + id);
        ReflectionTestUtils.setField(account, "id", id);
        account.setBalance(money(balance));
        return account;
    }

    private static AccountTransaction persisted(AccountTransaction transaction) {
        ReflectionTestUtils.setField(transaction, "id", 100L);
        ReflectionTestUtils.setField(transaction, "occurredAt", OCCURRED_AT);
        return transaction;
    }

    private static void assertApiError(ApiException exception, HttpStatus status, String code) {
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }

    static Stream<Arguments> invalidTransactions() {
        AccountTransactionRequest request = new AccountTransactionRequest(money("10.00"), TransactionType.DEPOSIT, null);
        return Stream.of(
                Arguments.of(null, request),
                Arguments.of(0L, request),
                Arguments.of(-1L, request)
        );
    }

    static Stream<Arguments> invalidTransfers() {
        return Stream.of(
                Arguments.of(null, new TransferRequest(2L, money("10.00"))),
                Arguments.of(0L, new TransferRequest(2L, money("10.00"))),
                Arguments.of(-1L, new TransferRequest(2L, money("10.00"))),
                Arguments.of(1L, new TransferRequest(1L, money("10.00")))
        );
    }

    @ParameterizedTest
    @CsvSource({"DEPOSIT, 125.00",
            "WITHDRAWAL, 75.00"})
    void createDepositOrWithdrawal(TransactionType type, String expectedBalance) {
        Account existing = account(1L, "100.00");
        AccountTransactionRequest request = new AccountTransactionRequest(money("25.00"), type, null);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(accountRepository.save(existing)).thenReturn(existing);
        when(accountTransactionRepository.saveAndFlush(any(AccountTransaction.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        AccountTransactionResponse response = transactionService.createTransaction(1L, request);
        assertEquals(new AccountTransactionResponse(
                100L, type, money("25.00"), OCCURRED_AT, money(expectedBalance), 1L, null, null), response);
        assertEquals(money(expectedBalance), existing.getBalance());
        var captor = org.mockito.ArgumentCaptor.forClass(AccountTransaction.class);
        verify(accountTransactionRepository).saveAndFlush(captor.capture());
        AccountTransaction submitted = captor.getValue();
        assertSame(existing, submitted.getAccount());
        assertNull(submitted.getDestinationAccount());
        assertEquals(type, submitted.getType());
        assertEquals(money("25.00"), submitted.getAmount());
        verify(accountRepository).save(existing);
        verify(accountTransactionRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"DEPOSIT, 9999999999999.98, 0.01, 9999999999999.99",
            "WITHDRAWAL, 100.00, 100.00, 0.00",
            "DEPOSIT, 0.00, 9999999999999.99, 9999999999999.99"})
    void acceptsBalanceLimits(TransactionType type, String initialBalance, String amount, String expectedBalance) {
        Account existing = account(1L, initialBalance);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(accountRepository.save(existing)).thenReturn(existing);
        when(accountTransactionRepository.saveAndFlush(any(AccountTransaction.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        AccountTransactionResponse response = transactionService.createTransaction(1L, new AccountTransactionRequest(money(amount), type, null));
        assertEquals(money(expectedBalance), response.balance());
        assertEquals(money(expectedBalance), existing.getBalance());
    }

    @ParameterizedTest
    @CsvSource({
            "100.00, 50.00, 100.00, 0.00, 150.00",
            "100.00, 9999999999999.98, 0.01, 99.99, 9999999999999.99"})
    void transferAcceptsExactBalanceLimits(String initialSource, String initialDestination, String amount, String expectedSource, String expectedDestination) {
        Account source = account(1L, initialSource);
        Account destination = account(2L, initialDestination);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));
        when(accountTransactionRepository.save(any(AccountTransaction.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        TransferResponse response = transactionService.transfer(1L, new TransferRequest(2L, money(amount)));
        assertEquals(money(expectedSource), response.balance());
        assertEquals(money(expectedDestination), response.destinationBalance());
        assertEquals(money(expectedSource), source.getBalance());
        assertEquals(money(expectedDestination), destination.getBalance());
    }

    @Test
    void normalizesAmountTwoDecimals() {
        Account existing = account(1L, "100.00");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(accountRepository.save(existing)).thenReturn(existing);
        when(accountTransactionRepository.saveAndFlush(any(AccountTransaction.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        AccountTransactionResponse response = transactionService.createTransaction(1L, new AccountTransactionRequest(money("25"), TransactionType.DEPOSIT, null));
        assertEquals(money("25.00"), response.amount());
        assertEquals(money("125.00"), response.balance());
    }

    @ParameterizedTest
    @MethodSource("invalidTransactions")
    void invalidTransaction(Long accountId, AccountTransactionRequest request) {
        ApiException exception = assertThrows(ApiException.class, () -> transactionService.createTransaction(accountId, request));
        assertApiError(exception, HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION_DATA");
        verifyNoInteractions(accountRepository, accountTransactionRepository);
    }

    @Test
    void createTransactionMissingAccount() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        ApiException exception = assertThrows(ApiException.class, () -> transactionService.createTransaction(1L, new AccountTransactionRequest(money("10.00"), TransactionType.DEPOSIT, null)));
        assertApiError(exception, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        verify(accountRepository).findByIdForUpdate(1L);
        verifyNoMoreInteractions(accountRepository);
        verifyNoInteractions(accountTransactionRepository);
    }

    @ParameterizedTest
    @CsvSource({"WITHDRAWAL, 100.00, 100.01, INSUFFICIENT_BALANCE",
            "DEPOSIT, 9999999999999.99, 0.01, BALANCE_LIMIT_EXCEEDED"})
    void transactionWithBalanceConflict(TransactionType type, String initialBalance, String amount, String code) {
        Account existing = account(1L, initialBalance);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        ApiException exception = assertThrows(ApiException.class, () -> transactionService.createTransaction(1L, new AccountTransactionRequest(money(amount), type, null)));
        assertApiError(exception, HttpStatus.CONFLICT, code);
        assertEquals(money(initialBalance), existing.getBalance());
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(accountTransactionRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void transfersThroughBothServiceEntryPoints(boolean useCreateTransaction) {
        Account source = account(1L, "100.00");
        Account destination = account(2L, "50.00");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));
        when(accountTransactionRepository.save(any(AccountTransaction.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        AccountTransactionResponse response;
        if (useCreateTransaction) {
            response = transactionService.createTransaction(1L, new AccountTransactionRequest(money("25.00"), TransactionType.TRANSFER, 2L));
        } else {
            TransferResponse transfer = transactionService.transfer(1L, new TransferRequest(2L, money("25.00")));
            response = new AccountTransactionResponse(transfer.id(), transfer.type(), transfer.amount(),
                    transfer.occurredAt(), transfer.balance(), transfer.sourceAccountId(),
                    transfer.destinationAccountId(), transfer.destinationBalance());
        }
        assertEquals(new AccountTransactionResponse(100L, TransactionType.TRANSFER, money("25.00"), OCCURRED_AT,
                money("75.00"), 1L, 2L, money("75.00")), response);
        assertEquals(money("75.00"), source.getBalance());
        assertEquals(money("75.00"), destination.getBalance());
        var captor = org.mockito.ArgumentCaptor.forClass(AccountTransaction.class);
        verify(accountTransactionRepository).save(captor.capture());
        assertSame(source, captor.getValue().getAccount());
        assertSame(destination, captor.getValue().getDestinationAccount());
        assertEquals(TransactionType.TRANSFER, captor.getValue().getType());
        assertEquals(money("25.00"), captor.getValue().getAmount());
        verify(accountRepository, never()).save(any());
        verify(accountTransactionRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @MethodSource("invalidTransfers")
    void invalidTransfer(Long sourceId, TransferRequest request) {
        ApiException exception = assertThrows(ApiException.class, () -> transactionService.transfer(sourceId, request));
        assertApiError(exception, HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION_DATA");
        verifyNoInteractions(accountRepository, accountTransactionRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L})
    void transferMissingAccount(Long missingId) {
        Account source = account(1L, "100.00");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(missingId == 1L ? Optional.empty() : Optional.of(source));
        if (missingId == 2L) {
            when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        }
        ApiException exception = assertThrows(ApiException.class, () -> transactionService.transfer(1L, new TransferRequest(2L, money("25.00"))));
        assertApiError(exception, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertEquals(money("100.00"), source.getBalance());
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(accountTransactionRepository);
        if (missingId == 1L) {
            verify(accountRepository, never()).findByIdForUpdate(2L);
        }
    }

    @ParameterizedTest
    @CsvSource({"10.00, 50.00, 25.00, INSUFFICIENT_BALANCE",
            "100.00, 9999999999999.99, 0.01, BALANCE_LIMIT_EXCEEDED"})
    void transferBalanceConflict(String sourceBalance, String destinationBalance, String amount, String code) {
        Account source = account(1L, sourceBalance);
        Account destination = account(2L, destinationBalance);
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));
        ApiException exception = assertThrows(ApiException.class, () -> transactionService.transfer(1L, new TransferRequest(2L, money(amount))));
        assertApiError(exception, HttpStatus.CONFLICT, code);
        assertEquals(money(sourceBalance), source.getBalance());
        assertEquals(money(destinationBalance), destination.getBalance());
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(accountTransactionRepository);
    }

    @Test
    void getLastFiveTransactions() {
        Account source = account(1L, "100.00");
        Account destination = account(2L, "50.00");
        AccountTransaction deposit = persisted(new AccountTransaction(source, TransactionType.DEPOSIT, money("10.00")));
        AccountTransaction transfer = persisted(new AccountTransaction(source, destination, money("25.00")));
        ReflectionTestUtils.setField(transfer, "id", 101L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountTransactionRepository.findTop5ByAccount_IdOrDestinationAccount_IdOrderByOccurredAtDescIdDesc(1L, 1L))
                .thenReturn(List.of(transfer, deposit));
        List<TransactionListResponse> response = transactionService.getLastFiveTransactions(1L);
        assertEquals(List.of(new TransactionListResponse(
                                101L, TransactionType.TRANSFER, money("25.00"),
                                OCCURRED_AT, 1L, 2L),
                        new TransactionListResponse(
                                100L, TransactionType.DEPOSIT, money("10.00"),
                                OCCURRED_AT, 1L, null))
                , response);
        verify(accountTransactionRepository).findTop5ByAccount_IdOrDestinationAccount_IdOrderByOccurredAtDescIdDesc(1L, 1L);
    }

    @Test
    void getLastFiveTransactionsEmptyList() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, "0.00")));
        when(accountTransactionRepository.findTop5ByAccount_IdOrDestinationAccount_IdOrderByOccurredAtDescIdDesc(1L, 1L))
                .thenReturn(List.of());
        assertTrue(transactionService.getLastFiveTransactions(1L).isEmpty());
    }

    @Test
    void getLastFiveTransactionsWithMissingAccount() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());
        ApiException exception = assertThrows(ApiException.class,
                () -> transactionService.getLastFiveTransactions(1L));
        assertApiError(exception, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        verifyNoInteractions(accountTransactionRepository);
    }
}

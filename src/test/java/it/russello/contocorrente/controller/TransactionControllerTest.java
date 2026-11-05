package it.russello.contocorrente.controller;

import it.russello.contocorrente.dto.*;
import it.russello.contocorrente.entity.TransactionType;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.exception.ApiExceptionHandler;
import it.russello.contocorrente.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
public class TransactionControllerTest {
    private static final String BASE_URL = "/api/account/1/transaction";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 10, 12, 0);
    private static final String TRANSACTION_JSON = """
            {
                "amount": 25.00,
                "type": "DEPOSIT"
            }
            """;
    private static final String TRANSFER_JSON = """
            {
                "destinationAccountId": 2,
                "amount": 25.00
            }
            """;
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TransactionService transactionService;

    private static AccountTransactionRequest transactionRequest() {
        return new AccountTransactionRequest(new BigDecimal("25.00"), TransactionType.DEPOSIT, null);
    }

    private static TransferRequest transferRequest() {
        return new TransferRequest(2L, new BigDecimal("25.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEPOSIT", "WITHDRAWAL", "TRANSFER"})
    void createTransaction(String typeName) throws Exception {
        TransactionType type = TransactionType.valueOf(typeName);
        boolean transfer = type == TransactionType.TRANSFER;
        Long destination = transfer ? 2L : null;
        BigDecimal balance = new BigDecimal(type == TransactionType.DEPOSIT ? "125.00" : "75.00");
        BigDecimal destinationBalance = transfer ? new BigDecimal("75.00") : null;
        AccountTransactionRequest request = new AccountTransactionRequest(new BigDecimal("25.00"), type, destination);
        when(transactionService.createTransaction(1L, request))
                .thenReturn(new AccountTransactionResponse(100L, type, new BigDecimal("25.00"), OCCURRED_AT, balance, 1L, destination, destinationBalance));
        String json = """
                {
                    "amount" : 25.00,
                    "type" : "%s",
                    "to" : %s
                }
                """.formatted(typeName, transfer ? "2" : "null");
        var result = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.type").value(typeName))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.occurredAt").value("2026-01-10T12:00:00"))
                .andExpect(jsonPath("$.balance").value(balance.doubleValue()))
                .andExpect(jsonPath("$.sourceAccountId").value(1));
        if (transfer) {
            result.andExpect(jsonPath("$.destinationAccountId").value(2))
                    .andExpect(jsonPath("$.destinationBalance").value(75.00));
        } else {
            result.andExpect(jsonPath("$.destinationAccountId").doesNotExist())
                    .andExpect(jsonPath("$.destinationBalance").doesNotExist());
        }
        verify(transactionService).createTransaction(1L, request);
    }

    @Test
    void transfer() throws Exception {
        when(transactionService.transfer(1L, transferRequest()))
                .thenReturn(new TransferResponse(100L, TransactionType.TRANSFER,
                        new BigDecimal("25.00"), OCCURRED_AT,
                        new BigDecimal("75.00"), 1L, 2L,
                        new BigDecimal("75.00")));
        mockMvc.perform(post(BASE_URL + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON).content(TRANSFER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.occurredAt").value("2026-01-10T12:00:00"))
                .andExpect(jsonPath("$.balance").value(75.00))
                .andExpect(jsonPath("$.sourceAccountId").value(1))
                .andExpect(jsonPath("$.destinationAccountId").value(2))
                .andExpect(jsonPath("$.destinationBalance").value(75.00));
        verify(transactionService).transfer(1L, transferRequest());
    }

    @Test
    void getLastFiveTransactions() throws Exception {
        when(transactionService.getLastFiveTransactions(1L))
                .thenReturn(List.of(new TransactionListResponse(101L, TransactionType.TRANSFER, new BigDecimal("25.00"), OCCURRED_AT, 1L, 2L),
                        new TransactionListResponse(100L, TransactionType.DEPOSIT, new BigDecimal("10.00"), OCCURRED_AT, 1L, null)));
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$[0].amount").value(25.00))
                .andExpect(jsonPath("$[0].occurredAt").value("2026-01-10T12:00:00"))
                .andExpect(jsonPath("$[0].sourceAccountId").value(1))
                .andExpect(jsonPath("$[0].destinationAccountId").value(2))
                .andExpect(jsonPath("$[1].id").value(100))
                .andExpect(jsonPath("$[1].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[1].amount").value(10.00))
                .andExpect(jsonPath("$[1].destinationAccountId").doesNotExist());
        verify(transactionService).getLastFiveTransactions(1L);
    }

    @Test
    void getLastFiveEmptyList() throws Exception {
        when(transactionService.getLastFiveTransactions(1L)).thenReturn(List.of());
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        verify(transactionService).getLastFiveTransactions(1L);
    }

    @ParameterizedTest
    @CsvSource({"400, INVALID_TRANSACTION_DATA",
            "404, ACCOUNT_NOT_FOUND",
            "409, INSUFFICIENT_BALANCE",
            "409, BALANCE_LIMIT_EXCEEDED"})
    void createServiceError(int httpStatus, String code) throws Exception {
        when(transactionService.createTransaction(1L, transactionRequest()))
                .thenThrow(new ApiException(HttpStatus.valueOf(httpStatus), code, "Errore movimento"));
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(TRANSACTION_JSON))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("Errore movimento"));
        verify(transactionService).createTransaction(1L, transactionRequest());
    }

    @ParameterizedTest
    @CsvSource({"400, INVALID_TRANSACTION_DATA",
            "404, ACCOUNT_NOT_FOUND",
            "409, INSUFFICIENT_BALANCE",
            "409, BALANCE_LIMIT_EXCEEDED"})
    void transferServiceError(int httpStatus, String code) throws Exception {
        when(transactionService.transfer(1L, transferRequest()))
                .thenThrow(new ApiException(HttpStatus.valueOf(httpStatus), code, "Errore trasferimento"));
        mockMvc.perform(post(BASE_URL + "/transfer").contentType(MediaType.APPLICATION_JSON).content(TRANSFER_JSON))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("Errore trasferimento"));
        verify(transactionService).transfer(1L, transferRequest());
    }

    @Test
    void getTransactionMissingAccount() throws Exception {
        when(transactionService.getLastFiveTransactions(1L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Conto non trovato"));
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Conto non trovato"));
        verify(transactionService).getLastFiveTransactions(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/transfer"})
    void rejectsMalformedJson(String suffix) throws Exception {
        mockMvc.perform(post(BASE_URL + suffix).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(transactionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/transfer"})
    void rejectsMissingBody(String suffix) throws Exception {
        mockMvc.perform(post(BASE_URL + suffix).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(transactionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"amount\":25.00,\"type\":\"UNKNOWN\"}",
            "{\"amount\":\"abc\",\"type\":\"DEPOSIT\"}",
            "{\"amount\":25.00,\"type\":\"TRANSFER\",\"to\":\"abc\"}"})
    void invalidTransactionJson(String json) throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(transactionService);
    }

    @Test
    void rejectsNonNumericTransferDestination() throws Exception {
        mockMvc.perform(post(BASE_URL + "/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                   "destinationAccountId": "abc",
                                   "amount": 25.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(transactionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "TRANSFER"})
    void rejectsNonNumericAccountId(String operation) throws Exception {
        String url = "/api/account/abc/transaction";
        var request = switch (operation) {
            case "GET" -> get(url);
            case "POST" -> post(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TRANSACTION_JSON);
            case "TRANSFER" -> post(url + "/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TRANSFER_JSON);
            default -> throw new IllegalArgumentException(operation);
        };
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(transactionService);
    }

    @Test
    void unexpectedInternalError() throws Exception {
        when(transactionService.getLastFiveTransactions(1L))
                .thenThrow(new RuntimeException("Errore database"));
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Errore interno durante l'operazione"));
        verify(transactionService).getLastFiveTransactions(1L);
    }
}

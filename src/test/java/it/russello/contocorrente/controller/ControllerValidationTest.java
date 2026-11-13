package it.russello.contocorrente.controller;

import it.russello.contocorrente.exception.ApiExceptionHandler;
import it.russello.contocorrente.service.TransactionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
public class ControllerValidationTest {
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    TransactionService transactionService;
    @MockitoBean
    private RestClient restClient;


    static Stream<InvalidRequest> invalidRequests() {
        List<InvalidRequest> cases = new ArrayList<>();
        // Importi: tutti i tipi di movimento e l'endpoint dedicato.
        for (String amount : new String[]{
                "null", "0", "-0.01", "1.001", "10000000000000.00"}) {

            for (String type : List.of("DEPOSIT", "WITHDRAWAL", "TRANSFER")) {
                String destination = type.equals("TRANSFER") ? "2" : "null";
                cases.add(new InvalidRequest(
                        "POST", "/api/account/1/transaction",
                        """
                                {"amount":%s,"type":"%s","to":%s}
                                """.formatted(amount, type, destination),
                        "INVALID_TRANSACTION_DATA"));
            }

            cases.add(new InvalidRequest(
                    "POST", "/api/account/1/transaction/transfer",
                    """
                            {"destinationAccountId":2,"amount":%s}
                            """.formatted(amount),
                    "INVALID_TRANSACTION_DATA"));
        }

        cases.add(new InvalidRequest(
                "POST", "/api/account/1/transaction",
                "{\"amount\":10,\"type\":null}",
                "INVALID_TRANSACTION_DATA"));

        // Coerenza tipo/destinatario.
        for (String destination : new String[]{"null", "0", "-1"}) {
            cases.add(new InvalidRequest(
                    "POST", "/api/account/1/transaction",
                    """
                            {"amount":10,"type":"TRANSFER","to":%s}
                            """.formatted(destination),
                    "INVALID_TRANSACTION_DATA"));

            cases.add(new InvalidRequest(
                    "POST", "/api/account/1/transaction/transfer",
                    """
                            {"amount":10,"destinationAccountId":%s}
                            """.formatted(destination),
                    "INVALID_TRANSACTION_DATA"));
        }

        for (String type : List.of("DEPOSIT", "WITHDRAWAL")) {
            cases.add(new InvalidRequest(
                    "POST", "/api/account/1/transaction",
                    """
                            {"amount":10,"type":"%s","to":2}
                            """.formatted(type),
                    "INVALID_TRANSACTION_DATA"));
        }

        // Proprietà JSON sconosciute.
        for (String url : List.of(
                "/api/account/1/transaction",
                "/api/account/1/transaction/transfer")) {

            cases.add(new InvalidRequest(
                    "POST", url, "{\"unexpected\":true}", "INVALID_REQUEST"));
        }

        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidDataDoesNotReachService(InvalidRequest test) throws Exception {
        var request = test.method().equals("PUT")
                ? put(test.url())
                : post(test.url());

        mockMvc.perform(request
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(test.json()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(test.code()))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(transactionService);
    }


    record InvalidRequest(String method, String url, String json, String code) {
    }
}


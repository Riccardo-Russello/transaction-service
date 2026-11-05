package it.russello.contocorrente.controller;

import jakarta.persistence.EntityManager;

import it.russello.contocorrente.client.InternalClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.when;

import it.russello.contocorrente.entity.Account;
import it.russello.contocorrente.entity.Person;
import it.russello.contocorrente.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static it.russello.contocorrente.support.TestJwt.admin;
import static it.russello.contocorrente.support.TestJwt.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthorizationIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    EntityManager entityManager;
    @Autowired
    AccountRepository accountRepository;
    @MockitoBean
    InternalClient internalClient;

    Person owner;
    Person other;
    Account source;
    Account destination;

    @BeforeEach
    void setUp() {
        owner = savePerson("auth.owner");
        other = savePerson("auth.other");

        source = new Account(owner, "AUTH-SOURCE");
        source.setBalance(new BigDecimal("100.00"));
        accountRepository.saveAndFlush(source);

        destination = accountRepository.saveAndFlush(
                new Account(other, "AUTH-DESTINATION"));
    }

    private Person savePerson(String username) {
        Person person = new Person(
                "Mario", "Rossi", LocalDate.of(1990, 5, 15));
        person.setUsername(username);
        person.setPassword("fixture-password");
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private MockHttpServletRequestBuilder operation(String operation) {
        return switch (operation) {

            case "TRANSACTION_CREATE" -> post(
                    "/api/account/{id}/transaction", source.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":10,\"type\":\"DEPOSIT\"}");
            case "TRANSACTION_LIST" -> get(
                    "/api/account/{id}/transaction", source.getId());
            case "TRANSFER" -> post(
                    "/api/account/{id}/transaction/transfer", source.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"destinationAccountId":%d,"amount":10}
                            """.formatted(destination.getId()));
            default -> throw new IllegalArgumentException(operation);
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRANSACTION_CREATE", "TRANSACTION_LIST", "TRANSFER"})
    void protectedOperationsRequireAuthentication(String operation) throws Exception {
        mockMvc.perform(operation(operation))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRANSACTION_CREATE", "TRANSACTION_LIST", "TRANSFER"})
    void userCannotOperateOnAnotherPersonsAccount(String operation) throws Exception {
        BigDecimal before = source.getBalance();

        mockMvc.perform(operation(operation).with(user(other.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertEquals(before,
                accountRepository.findById(source.getId()).orElseThrow().getBalance());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRANSACTION_CREATE", "TRANSACTION_LIST", "TRANSFER"})
    void ownerCanPerformAllowedOperations(String operation) throws Exception {
        int expected = switch (operation) {
            case "TRANSACTION_CREATE", "TRANSFER" -> 201;
            default -> 200;
        };

        when(internalClient.check("http://account.test",
                "/internal/accounts/{id}/owned", source.getId())).thenReturn(true);
        mockMvc.perform(operation(operation).with(user(owner.getId())))
                .andExpect(status().is(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRANSACTION_CREATE", "TRANSACTION_LIST", "TRANSFER"})
    void adminCanPerformAllowedOperations(String operation) throws Exception {
        int expected = switch (operation) {
            case "TRANSACTION_CREATE", "TRANSFER" -> 201;
            default -> 200;
        };

        mockMvc.perform(operation(operation).with(admin(owner.getId())))
                .andExpect(status().is(expected));
    }

}
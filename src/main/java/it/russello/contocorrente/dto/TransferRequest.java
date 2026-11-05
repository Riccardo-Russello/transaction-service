package it.russello.contocorrente.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(@NotNull(message = "Id conto destinatario obbligatorio")
                              @Positive(message = "Id conto destinatario deve essere positivo")
                              Long destinationAccountId,
                              @NotNull(message = "Importo obbligatorio")
                              @DecimalMin(
                                      value = "0",
                                      inclusive = false,
                                      message = "Importo deve essere maggiore di zero")
                              @Digits(
                                      integer = 13,
                                      fraction = 2,
                                      message = "Importo con massimo 13 cifre intere e 2 decimali") BigDecimal amount) {
}
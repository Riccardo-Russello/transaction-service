package it.russello.contocorrente.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import it.russello.contocorrente.entity.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record AccountTransactionRequest(@NotNull(message = "Importo obbligatorio")
                                        @DecimalMin(
                                                value = "0",
                                                inclusive = false,
                                                message = "Importo deve essere maggiore di zero")
                                        @Digits(
                                                integer = 13,
                                                fraction = 2,
                                                message = "Importo con massimo 13 cifre intere e 2 decimali")
                                        BigDecimal amount,

                                        @NotNull(message = "Tipo obbligatorio: DEPOSIT, WITHDRAWAL o TRNAFER") TransactionType type,
                                        @Positive(message = "Id conto destinatario deve essere positivo") Long to) {
    @JsonIgnore
    @AssertTrue(message = "Destinatario obbligatorio per TRANSFER e non ammesso per gli altri tipi")
    public boolean isDestinationValid() {
        if (type == null) {
            return true;
        }
        return type == TransactionType.TRANSFER ? to != null : to == null;
    }
}
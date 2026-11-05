package it.russello.contocorrente.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "accounttransaction")
@Getter
@NoArgsConstructor
public class AccountTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_transaction_account")
    )
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinationAccountId", updatable = false,
            foreignKey = @ForeignKey(name = "fk_transaction_destination"))
    private Account destinationAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "VARCHAR(10)")
    private TransactionType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime occurredAt;


    public AccountTransaction(Account account, TransactionType type, BigDecimal amount) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.occurredAt = LocalDateTime.now()
                .truncatedTo(ChronoUnit.MICROS);
    }

    public AccountTransaction(Account source, Account destination, BigDecimal amount) {
        this(source, TransactionType.TRANSFER, amount);
        this.destinationAccount = destination;
    }

}

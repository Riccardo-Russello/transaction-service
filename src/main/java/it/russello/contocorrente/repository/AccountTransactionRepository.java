package it.russello.contocorrente.repository;

import it.russello.contocorrente.entity.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    List<AccountTransaction>
    findTop5ByAccount_IdOrDestinationAccount_IdOrderByOccurredAtDescIdDesc(Long sourceAccountId, Long destinationAccountId);

    boolean existsByAccount_IdOrDestinationAccount_Id(Long accountId, Long destinationAccountId);

    default boolean existsByAccount_Id(Long accountId) {
        return existsByAccount_IdOrDestinationAccount_Id(accountId, accountId);
    }

    @Query(value = """
            SELECT COALESCE(SUM(CASE
                WHEN destination_account_id = :id THEN amount
                WHEN type = 'DEPOSIT' THEN amount
                ELSE -amount
            END), 0.00)
            FROM accounttransaction
            WHERE account_id = :id OR destination_account_id = :id
            """, nativeQuery = true)
    BigDecimal calculateBalance(@Param("id") Long id);
}

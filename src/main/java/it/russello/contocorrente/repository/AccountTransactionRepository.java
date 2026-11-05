package it.russello.contocorrente.repository;

import it.russello.contocorrente.entity.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    List<AccountTransaction>
    findTop5ByAccount_IdOrDestinationAccount_IdOrderByOccurredAtDescIdDesc(Long sourceAccountId, Long destinationAccountId);

    boolean existsByAccount_IdOrDestinationAccount_Id(Long accountId, Long destinationAccountId);

    default boolean existsByAccount_Id(Long accountId) {
        return existsByAccount_IdOrDestinationAccount_Id(accountId, accountId);
    }
}

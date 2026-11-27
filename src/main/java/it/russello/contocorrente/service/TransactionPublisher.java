package it.russello.contocorrente.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.publisher-enabled", havingValue = "true")
public class TransactionPublisher {
    private final KafkaTemplate<String, String> kafka;

    @Value("${app.kafka.transaction-topic}")
    private String topic;

    public record BalanceChanged(Long accountId, BigDecimal delta) {
    }

    @TransactionalEventListener
    public void publish(BalanceChanged event) {
        String accountId = event.accountId().toString();
        try {
            kafka.send(topic, accountId, event.delta().toPlainString())
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Invio Kafka fallito per il conto {}", accountId, error);
                        }
                    });
        } catch (RuntimeException error) {
            log.error("Invio Kafka fallito per il conto {}", accountId, error);
        }
    }
}

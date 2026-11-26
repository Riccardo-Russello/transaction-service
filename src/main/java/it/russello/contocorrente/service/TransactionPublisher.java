package it.russello.contocorrente.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.publisher-enabled", havingValue = "true")
public class TransactionPublisher {
    private final KafkaTemplate<String, String> kafka;

    @Value("${app.kafka.transaction-topic}")
    private String topic;

    public record TransactionCreated(Long id) {
    }

    @TransactionalEventListener
    public void publish(TransactionCreated event) {
        String id = event.id().toString();
        try {
            kafka.send(topic, id, id).whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Invio Kafka fallito per il movimento {}", id, error);
                }
            });
        } catch (RuntimeException error) {
            log.error("Invio Kafka fallito per il movimento {}", id, error);
        }
    }
}

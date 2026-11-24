package it.russello.contocorrente.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(name = "app.kafka.publisher-enabled", havingValue = "true")
public class TransactionPublisher {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    @Value("${app.kafka.transaction-topic}")
    private String topic;

    @Scheduled(fixedDelay = 1000)
    public void publishPending() throws ExecutionException, TimeoutException {
        var ids = jdbc.queryForList("""
                SELECT id FROM accounttransaction
                WHERE kafka_published = FALSE
                ORDER BY id
                LIMIT 100
                """, Long.class);

        for (Long id : ids) {
            String transactionId = id.toString();
            try {
                kafka.send(topic, transactionId, transactionId).get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            jdbc.update("UPDATE accounttransaction SET kafka_published = TRUE WHERE id = ?", id);
        }
    }
}

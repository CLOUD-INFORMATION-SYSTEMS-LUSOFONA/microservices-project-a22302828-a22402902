package pt.ulusofona.productservice.sqs;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductCreatedSqsEvent(
        String eventType,
        Long productId,
        String name,
        BigDecimal price,
        Instant occurredAt
) {
}
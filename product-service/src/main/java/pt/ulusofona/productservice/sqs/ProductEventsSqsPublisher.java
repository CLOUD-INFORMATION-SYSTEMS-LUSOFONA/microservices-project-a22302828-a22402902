package pt.ulusofona.productservice.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pt.ulusofona.productservice.model.Product;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "cloud.sqs.product-events", name = "enabled", havingValue = "true")
public class ProductEventsSqsPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final ProductEventsSqsProperties properties;

    public ProductEventsSqsPublisher(
            @Qualifier("productEventsSqsClient") SqsClient sqsClient,
            ObjectMapper objectMapper,
            ProductEventsSqsProperties properties
    ) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publishProductCreated(Product product) {
        try {
            ProductCreatedSqsEvent event = new ProductCreatedSqsEvent(
                    "PRODUCT_CREATED",
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    Instant.now()
            );

            String body = objectMapper.writeValueAsString(event);

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(properties.getQueueUrl())
                    .messageBody(body)
                    .build());

            log.info("Published ProductCreated event to SQS. productId={}", product.getId());
        } catch (Exception e) {
            log.error("Failed to publish ProductCreated event to SQS", e);
        }
    }
}
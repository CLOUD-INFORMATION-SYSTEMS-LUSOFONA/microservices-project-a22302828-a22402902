package pt.ulusofona.orderservice.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@ConditionalOnProperty(prefix = "cloud.sqs.product-events-consumer", name = "enabled", havingValue = "true")
public class OrderProductEventsSqsConfiguration {

    @Bean(name = "productEventsConsumerSqsClient", destroyMethod = "close")
    public SqsClient productEventsConsumerSqsClient(OrderProductEventsSqsProperties properties) {
        if (properties.getQueueUrl() == null || properties.getQueueUrl().isBlank()) {
            throw new IllegalStateException(
                    "cloud.sqs.product-events-consumer.queue-url must be set"
            );
        }

        var builder = SqsClient.builder();

        if (properties.getRegion() != null && !properties.getRegion().isBlank()) {
            builder.region(Region.of(properties.getRegion()));
        }

        return builder.build();
    }

    @Bean
    public ProductEventSqsPollingConsumer productEventSqsPollingConsumer(
            @Qualifier("productEventsConsumerSqsClient") SqsClient sqsClient,
            ObjectMapper objectMapper,
            OrderProductEventsSqsProperties properties
    ) {
        return new ProductEventSqsPollingConsumer(sqsClient, objectMapper, properties);
    }
}
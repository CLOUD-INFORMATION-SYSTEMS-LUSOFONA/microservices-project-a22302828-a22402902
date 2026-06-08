package pt.ulusofona.productservice.sqs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@ConditionalOnProperty(prefix = "cloud.sqs.product-events", name = "enabled", havingValue = "true")
public class ProductEventsSqsConfiguration {

    @Bean(name = "productEventsSqsClient", destroyMethod = "close")
    public SqsClient productEventsSqsClient(ProductEventsSqsProperties properties) {
        if (properties.getQueueUrl() == null || properties.getQueueUrl().isBlank()) {
            throw new IllegalStateException("cloud.sqs.product-events.queue-url must be set");
        }

        var builder = SqsClient.builder();

        if (properties.getRegion() != null && !properties.getRegion().isBlank()) {
            builder.region(Region.of(properties.getRegion()));
        }

        return builder.build();
    }
}
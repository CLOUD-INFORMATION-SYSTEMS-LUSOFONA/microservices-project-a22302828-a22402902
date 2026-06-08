package pt.ulusofona.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ulusofona.orderservice.client.ProductResponse;
import pt.ulusofona.orderservice.client.ProductServiceClient;
import pt.ulusofona.orderservice.client.UserResponse;
import pt.ulusofona.orderservice.client.UserServiceClient;
import pt.ulusofona.orderservice.dto.OrderItemRequest;
import pt.ulusofona.orderservice.dto.OrderItemResponse;
import pt.ulusofona.orderservice.dto.OrderRequest;
import pt.ulusofona.orderservice.dto.OrderResponse;
import pt.ulusofona.orderservice.event.OrderCreatedEvent;
import pt.ulusofona.orderservice.event.OrderItemEvent;
import pt.ulusofona.orderservice.event.OrderStatusChangedEvent;
import pt.ulusofona.orderservice.model.Order;
import pt.ulusofona.orderservice.model.OrderItem;
import pt.ulusofona.orderservice.model.OrderStatus;
import pt.ulusofona.orderservice.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserServiceClient userServiceClient;
    private final ProductServiceClient productServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ORDER_CREATED_TOPIC = "order-created";
    private static final String ORDER_STATUS_CHANGED_TOPIC = "order-status-changed";

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for user ID: {}", request.getUserId());

        try {
            UserResponse user = userServiceClient.getUserById(request.getUserId());
            log.debug("User validated: {}", user.getName());
        } catch (Exception e) {
            log.error("User validation failed for user ID: {}", request.getUserId(), e);
            throw new RuntimeException("User not found with ID: " + request.getUserId());
        }

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus(OrderStatus.PENDING);

        for (OrderItemRequest itemRequest : request.getItems()) {
            ProductResponse product;

            try {
                product = productServiceClient.getProductById(itemRequest.getProductId());
                log.debug("Product validated: {} - Price: {}", product.getName(), product.getPrice());
            } catch (Exception e) {
                log.error("Product validation failed for product ID: {}", itemRequest.getProductId(), e);
                throw new RuntimeException("Product not found with ID: " + itemRequest.getProductId());
            }

            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new RuntimeException(
                        String.format("Insufficient stock for product %s. Available: %d, Requested: %d",
                                product.getName(),
                                product.getStockQuantity(),
                                itemRequest.getQuantity()));
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setPrice(product.getPrice());
            order.addOrderItem(orderItem);
        }

        order.calculateTotal();

        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully with ID: {}", savedOrder.getId());

        publishOrderCreatedEvent(savedOrder);

        return mapToResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + id));
        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus newStatus) {
        log.info("Updating order {} status to {}", id, newStatus);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + id));

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        log.info("Order {} status updated from {} to {}", id, previousStatus, newStatus);

        publishOrderStatusChangedEvent(updatedOrder, previousStatus);

        return mapToResponse(updatedOrder);
    }

    private void publishOrderCreatedEvent(Order order) {
        try {
            OrderCreatedEvent event = new OrderCreatedEvent(
                    order.getId(),
                    order.getUserId(),
                    order.getOrderItems().stream()
                            .map(item -> new OrderItemEvent(
                                    item.getProductId(),
                                    item.getProductName(),
                                    item.getQuantity(),
                                    item.getPrice()))
                            .collect(Collectors.toList()),
                    order.getTotalAmount(),
                    order.getCreatedAt()
            );

            kafkaTemplate.send(ORDER_CREATED_TOPIC, event);
            log.info("Published OrderCreatedEvent to Kafka for order ID: {}", order.getId());

        } catch (Exception e) {
            log.error("Failed to publish OrderCreatedEvent for order ID: {}", order.getId(), e);
        }
    }

    private void publishOrderStatusChangedEvent(Order order, OrderStatus previousStatus) {
        try {
            OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                    order.getId(),
                    order.getUserId(),
                    previousStatus,
                    order.getStatus(),
                    LocalDateTime.now()
            );

            kafkaTemplate.send(ORDER_STATUS_CHANGED_TOPIC, event);
            log.info("Published OrderStatusChangedEvent for order ID: {} ({} -> {})",
                    order.getId(), previousStatus, order.getStatus());

        } catch (Exception e) {
            log.error("Failed to publish OrderStatusChangedEvent for order ID: {}", order.getId(), e);
        }
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems() != null ?
                order.getOrderItems().stream()
                        .map(item -> new OrderItemResponse(
                                item.getId(),
                                item.getProductId(),
                                item.getProductName(),
                                item.getQuantity(),
                                item.getPrice()))
                        .collect(Collectors.toList()) :
                List.of();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                items,
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
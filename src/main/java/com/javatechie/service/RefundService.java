package com.javatechie.service;

import com.javatechie.model.OrderEntity;
import com.javatechie.model.RefundPolicy;
import com.javatechie.repository.OrderRepository;
import com.javatechie.repository.RefundPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {

    private RefundPolicyRepository repository;;

    private final OrderRepository orderRepository;

    public RefundService(RefundPolicyRepository repository, OrderRepository orderRepository) {
        this.repository = repository;
        this.orderRepository = orderRepository;
    }

    public RefundPolicy checkEligibility(String orderStatus) {
        System.out.println("\nREFUND SERVICE -> Checking status: " + orderStatus);
        return repository.findByOrderStatus(orderStatus)
                .orElseThrow(() -> new RuntimeException("Refund policy not found for: " + orderStatus));
    }

    @Transactional
    public String processRefund(String orderId) {

        OrderEntity order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Order not found: " + orderId
                        )
                );

        if ("REFUNDED".equalsIgnoreCase(order.getPaymentStatus())) {
            return "Refund already completed for " + orderId;
        }

        order.setPaymentStatus("REFUNDED");
        order.setStatus("CANCELLED");
        order.setExpectedDelivery(null);

        orderRepository.save(order);

        System.out.println(
                "REFUND COMPLETED -> " + orderId
        );

        return """
                Refund completed successfully.
                Order ID: %s
                Amount: %s
                Payment Status: %s
                Order Status: %s
                """
                .formatted(
                        order.getOrderId(),
                        order.getTotalAmount(),
                        order.getPaymentStatus(),
                        order.getStatus()
                );
    }
}

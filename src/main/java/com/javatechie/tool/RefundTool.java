package com.javatechie.tool;

import com.javatechie.dto.RefundDecision;
import com.javatechie.model.RefundPolicy;
import com.javatechie.service.RefundService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class RefundTool {

    private RefundService refundService;

    public RefundTool(RefundService refundService) {
        this.refundService = refundService;
    }

    @Tool("Check whether an order is eligible for a refund based on order status")
    public RefundDecision checkRefundEligibility(String orderStatus) {
        System.out.println("\n>>> REFUND TOOL CALLED -> " + orderStatus);
        RefundPolicy policy = refundService.checkEligibility(orderStatus);
        return new RefundDecision(
                policy.isEligible(),
                policy.getMessage());
    }
}

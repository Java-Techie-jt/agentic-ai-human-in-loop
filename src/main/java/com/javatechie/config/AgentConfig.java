package com.javatechie.config;

import com.javatechie.agents.*;
import com.javatechie.dto.RefundDecision;
import com.javatechie.service.RefundService;
import com.javatechie.tool.OrderTools;
import com.javatechie.tool.RefundTool;
import com.javatechie.tool.ShippingTools;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class AgentConfig {

    @Bean
    public OrderAgent orderAgent(
            ChatModel chatModel,
            OrderTools orderTools) {

        return AgenticServices
                .agentBuilder(OrderAgent.class)
                .chatModel(chatModel)
                .tools(orderTools)
                .outputKey("orderDetails")
                .build();
    }

    @Bean
    public RefundAgent refundAgent(
            ChatModel chatModel,
            RefundTool refundTools) {

        return AgenticServices
                .agentBuilder(RefundAgent.class)
                .chatModel(chatModel)
                .tools(refundTools)
                .outputKey("refundDecision")
                .build();
    }

    @Bean
    public ResponseAgent responseAgent(
            ChatModel chatModel) {

        return AgenticServices
                .agentBuilder(ResponseAgent.class)
                .chatModel(chatModel)
                .outputKey("finalResponse")
                .build();
    }

    @Bean
    public ShippingAgent shippingAgent(
            ChatModel chatModel,
            ShippingTools shippingTools) {

        return AgenticServices
                .agentBuilder(ShippingAgent.class)
                .chatModel(chatModel)
                .tools(shippingTools)
                .outputKey("shippingDetails")
                .build();
    }

//    @Bean
//    public CustomerSupportWorkflow
//    customerSupportWorkflow(
//            OrderAgent orderAgent,
//            RefundAgent refundAgent,
//            ResponseAgent responseAgent) {
//
//        return AgenticServices
//                .sequenceBuilder(
//                        CustomerSupportWorkflow.class
//                )
//                .subAgents(
//                        orderAgent,
//                        refundAgent,
//                        responseAgent
//                )
//                .outputKey("finalResponse")

    /// /                .listener(new DebugAgentListener())
//                .build();
//    }

    @Bean
    public HumanInTheLoop refundApproval() {

        return AgenticServices
                .humanInTheLoopBuilder()

                .description("""
                    Ask a human for approval ONLY when:
                    1. The customer wants to actually process/execute a refund.
                    2. RefundAgent has already confirmed that the order
                       is eligible for refund.

                    Never ask for human approval when the customer
                    is only asking whether a refund is possible.

                    This approval MUST happen before executeRefund.
                    """)

                .inputKeys(
                        String.class, "request",
                        String.class, "orderDetails",
                        RefundDecision.class, "refundDecision"
                )

                .outputKey("humanApproval")

                .responseProvider(scope -> {

                    RefundDecision decision =
                            scope.readState(
                                    "refundDecision",
                                    new RefundDecision(
                                            false,
                                            "Refund eligibility not available"
                                    )
                            );

                    if (!decision.eligible()) {

                        System.out.println(
                                "\nRefund is NOT eligible."
                        );

                        return "NOT_REQUIRED";
                    }

                    String orderDetails =
                            scope.readState(
                                    "orderDetails",
                                    ""
                            );

                    System.out.println();
                    System.out.println(
                            "======================================"
                    );
                    System.out.println(
                            "       REFUND APPROVAL REQUIRED"
                    );
                    System.out.println(
                            "======================================"
                    );

                    System.out.println(orderDetails);

                    System.out.println(
                            "\nRefund Eligibility : "
                                    + decision.eligible()
                    );

                    System.out.println(
                            "Reason             : "
                                    + decision.reason()
                    );

                    System.out.print(
                            "\nApprove refund? (APPROVE / REJECT): "
                    );

                    try {

                        BufferedReader reader =
                                new BufferedReader(
                                        new InputStreamReader(System.in)
                                );

                        String approval =
                                reader.readLine();

                        if (approval == null) {
                            return "REJECT";
                        }

                        return "APPROVE".equalsIgnoreCase(
                                approval.trim()
                        )
                                ? "APPROVE"
                                : "REJECT";

                    } catch (IOException e) {

                        throw new RuntimeException(
                                "Failed to read approval",
                                e
                        );
                    }
                })

                .build();
    }

    @Bean
    public AgenticServices.AgenticScopeFunction<String> executeRefund(
            RefundService refundService) {

        return AgenticServices
                .<String>nonAiAgentBuilder(scope -> {

                    RefundDecision decision =
                            scope.readState(
                                    "refundDecision",
                                    //fallback in case refundDecision is not available
                                    new RefundDecision(
                                            false,
                                            "Refund eligibility unavailable"
                                    )
                            );

                    String approval =
                            scope.readState(
                                    "humanApproval",
                                    ""
                            );

                    if (!decision.eligible()) {
                        return """
                            Refund was NOT processed.
                            Reason: Order is not eligible for refund.
                            """;
                    }

                    if (!"APPROVE".equalsIgnoreCase(approval)) {
                        return """
                            Refund was NOT processed.
                            Reason: Human approval was not provided.
                            """;
                    }

                    // IMPORTANT:
                    // Get order ID from OrderAgent output,
                    // NOT from supervisor-generated request
                    String orderDetails =
                            scope.readState(
                                    "orderDetails",
                                    ""
                            );

                    String orderId =
                            extractOrderId(orderDetails);

                    return refundService
                            .processRefund(orderId);
                })

                .name("executeRefund")

                .description("""
                    Executes the actual refund.

                    Use ONLY after:
                    1. Refund eligibility is confirmed.
                    2. Human approval is APPROVE.

                    Uses the order information already retrieved
                    by OrderAgent to identify the order.

                    Never execute refund without human approval.
                    """)

                .inputKeys(
                        String.class, "orderDetails",
                        RefundDecision.class, "refundDecision",
                        String.class, "humanApproval"
                )

                .outputKey("refundExecution")
                .outputType(String.class)

                .build();
    }
    
    @Bean
    public SupervisorAgent customerSupportSupervisor(
            ChatModel chatModel,
            OrderAgent orderAgent,
            RefundAgent refundAgent,
            ResponseAgent responseAgent,
            ShippingAgent shippingAgent,
            HumanInTheLoop refundApproval,
            AgenticServices.AgenticScopeFunction<String> executeRefund) {

        return AgenticServices
                .supervisorBuilder()
                .chatModel(chatModel)
                .subAgents(
                        orderAgent,
                        refundAgent,
                        shippingAgent,
                        //Human in the loop for refund approval
                        refundApproval,
                        //Actual refund execution after approval
                        executeRefund,
                        responseAgent
                )
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .build();
    }




    private static String extractOrderId(String text) {

        Pattern pattern =
                Pattern.compile(
                        "ORD-\\d+",
                        Pattern.CASE_INSENSITIVE
                );

        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group().toUpperCase();
        }

        throw new IllegalArgumentException(
                "Order ID not found in request"
        );
    }
}
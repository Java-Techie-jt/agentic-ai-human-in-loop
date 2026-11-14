package com.javatechie.agents;

import com.javatechie.dto.RefundDecision;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ResponseAgent {

    @UserMessage("""
        Original customer request:
        {{request}}

        Order details:
        {{orderDetails}}

        Shipping details:
        {{shippingDetails}}

        Refund decision:
        {{refundDecision}}

        Answer the exact user question.

        Important:
        - Include every detail explicitly requested by the user.
        - If the user asks for a tracking number, include the tracking number.
        - Do not mention refund information unless the user asked about refund.
        - If the user asks only whether refund is possible,
          explain eligibility only.
        - Never claim that a refund was processed or completed.
        - Actual refund execution is handled by a separate refund execution agent.
        - Do not invent information.
        """)
    @Agent(
        name = "responseAgent",
        description = """
            Creates customer-friendly responses for informational requests
            such as order details, shipping details and refund eligibility.

            Do NOT use this agent to confirm an actual refund execution.
            The refund execution agent returns that result directly.
            """,
        outputKey = "finalResponse",
        optional = true
    )
    String createResponse(
        @V("request") String request,
        @V("orderDetails") String orderDetails,
        @V("shippingDetails") String shippingDetails,
        @V("refundDecision") RefundDecision refundDecision
    );
}
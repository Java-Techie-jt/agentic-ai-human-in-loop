package com.javatechie.dto;

public record RefundDecision(
    boolean eligible,
    String reason
) {}
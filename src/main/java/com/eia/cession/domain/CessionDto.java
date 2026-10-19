package com.eia.cession.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CessionDto(
        UUID id,
        UUID tenantId,
        String businessBook,
        String cessionNumber,
        UUID policyId,
        UUID cessionContractId,
        CessionStatus status,
        BigDecimal cededPremium,
        BigDecimal cededLoss,
        String currency,
        int version,
        Instant createdAt,
        Instant updatedAt
) {}

package com.eia.cession.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Inputs for one immutable FS-RI cession hand-off (SPEC 7.18.3).
 *
 * <p>{@code transitionVersion} is the optimistic version the case moves to when
 * the hand-off succeeds. It participates in the idempotency key, so a retry of
 * the same transition reuses the same key (HAND-01) while a later attempt from
 * a new case version gets a new key (HAND-02).</p>
 */
public record FsriCessionHandoff(
        UUID tenantId,
        UUID cessionId,
        String cessionNumber,
        String contractNumber,
        UUID policyId,
        BigDecimal cededPremium,
        BigDecimal cededLoss,
        BigDecimal expectedAmount,
        String currency,
        LocalDate effectiveDate,
        int transitionVersion
) {}

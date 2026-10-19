package com.eia.cession.domain;

import java.util.Locale;
import java.util.UUID;

/**
 * Resolves and enforces the request scope. Cession is fixed to the DIRECT book
 * (SPEC 3.6, 7.18.5, 11.9.1).
 */
public final class RequestScope {
    public static final String TENANT_HEADER = "X-EIA-Tenant";
    public static final String BOOK_HEADER = "X-EIA-Book";

    private RequestScope() {
    }

    public static UUID tenant(String header) {
        if (header == null || header.isBlank()) {
            throw DomainException.tenantRequired();
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException ex) {
            throw DomainException.tenantInvalid();
        }
    }

    public static void requireDirectBook(String header) {
        if (header == null || header.isBlank()) {
            throw DomainException.bookRequired();
        }
        if (!"DIRECT".equals(header.trim().toUpperCase(Locale.ROOT))) {
            throw DomainException.bookViolation();
        }
    }
}

package com.eia.cession.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class CessionClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public CessionClient(RestClient.Builder builder,
                         ObjectMapper objectMapper,
                         @Value("${eia.cession.expert-url:http://localhost:8091}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public List<CessionDto> findAll(UUID tenantId) {
        return client.get()
                .uri(uri -> uri.path("/internal/v1/cessions").queryParam("tenantId", tenantId).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> translate(response))
                .body(new ParameterizedTypeReference<>() {});
    }

    public CessionDto findById(UUID tenantId, UUID id) {
        return client.get()
                .uri(uri -> uri.path("/internal/v1/cessions/{id}").queryParam("tenantId", tenantId).build(id))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> translate(response))
                .body(CessionDto.class);
    }

    public CessionDto create(UUID tenantId, CreateCessionCommand command) {
        return client.post()
                .uri("/internal/v1/cessions")
                .body(new CreateCessionRequest(tenantId, "DIRECT", command.policyId(),
                        command.cessionContractId(), command.cededPremium(), command.cededLoss(),
                        command.currency()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> translate(response))
                .body(CessionDto.class);
    }

    public CessionDto updateStatus(UUID tenantId, UUID id, CessionStatus status, int version) {
        return client.put()
                .uri(uri -> uri.path("/internal/v1/cessions/{id}/status")
                        .queryParam("tenantId", tenantId).build(id))
                .body(new StatusRequest(status.name(), version))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> translate(response))
                .body(CessionDto.class);
    }

    /**
     * Persist the FS-RI acknowledgement and move the case to HANDED_OFF in one
     * optimistic, lifecycle-checked write. A 409 is a real concurrency conflict
     * for this operation, not a caller validation error.
     */
    public CessionDto recordHandoff(UUID tenantId, UUID id, HandoffRecord record) {
        return client.post()
                .uri(uri -> uri.path("/internal/v1/cessions/{id}/handoff")
                        .queryParam("tenantId", tenantId).build(id))
                .body(record)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> translate(response, true))
                .body(CessionDto.class);
    }

    /**
     * Record a failed FS-RI attempt without changing the case status, keeping
     * the case retryable. The case version is unchanged, so the retry reuses
     * the same transition version and idempotency key.
     */
    public CessionDto recordHandoffFailure(UUID tenantId, UUID id, HandoffFailure failure) {
        return client.post()
                .uri(uri -> uri.path("/internal/v1/cessions/{id}/handoff-failure")
                        .queryParam("tenantId", tenantId).build(id))
                .body(failure)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> translate(response, true))
                .body(CessionDto.class);
    }

    private void translate(ClientHttpResponse response) throws IOException {
        translate(response, false);
    }

    private void translate(ClientHttpResponse response, boolean conflictAsConflict) throws IOException {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
        ApiError error = readError(response);
        String code = error == null || error.code() == null ? "EXPERT_REJECTED" : error.code();
        String message = error == null || error.message() == null
                ? "Cession expert rejected the request"
                : error.message();

        if (status == HttpStatus.NOT_FOUND) {
            throw DomainException.notFound(message);
        }
        if (status == HttpStatus.CONFLICT) {
            if (conflictAsConflict) {
                throw new DomainException(HttpStatus.CONFLICT, code, message,
                        error == null ? null : error.details());
            }
            throw DomainException.badRequest("CONFLICT", message);
        }
        if (status != null && status.is4xxClientError()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, code, message,
                    error == null ? null : error.details());
        }
        throw DomainException.unavailable("Cession expert returned " + response.getStatusCode().value());
    }

    private ApiError readError(ClientHttpResponse response) {
        try {
            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return null;
            }
            return objectMapper.readValue(body, ApiError.class);
        } catch (IOException ex) {
            return null;
        }
    }

    record CreateCessionRequest(
            UUID tenantId,
            String businessBook,
            UUID policyId,
            UUID cessionContractId,
            BigDecimal cededPremium,
            BigDecimal cededLoss,
            String currency
    ) {}

    record StatusRequest(String status, int version) {}

    public record HandoffRecord(
            int expectedVersion,
            String fsriHandoffId,
            String fsriStatus,
            Instant fsriHandedOffAt
    ) {}

    public record HandoffFailure(
            int expectedVersion,
            String fsriLastError
    ) {}
}

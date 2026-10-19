package com.eia.cession.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CessionController.class)
@Import(CessionService.class)
class CessionControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID POLICY = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID CONTRACT = UUID.fromString("44444444-4444-7444-8444-444444444444");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CessionClient client;

    private static CessionDto dto(CessionStatus status, int version) {
        Instant now = Instant.parse("2026-10-19T12:00:00Z");
        return new CessionDto(ID, TENANT, "DIRECT", "CES-000000001", POLICY, CONTRACT, status,
                new BigDecimal("1000.0000"), new BigDecimal("250.0000"), "USD", version, now, now);
    }

    @Test
    void listRequiresTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/cessions").header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));
    }

    @Test
    void listRequiresBookHeader() throws Exception {
        mvc.perform(get("/api/v1/cessions").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOOK_REQUIRED"));
    }

    @Test
    void listHidesRetrocessionBookAs404() throws Exception {
        mvc.perform(get("/api/v1/cessions")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Cession is a DIRECT-only vertical; X-EIA-Book must be DIRECT"));
    }

    @Test
    void wrongBookOnCreateReturns404WithoutCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/cessions")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "ASSUMED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyId": "%s",
                                  "cessionContractId": "%s",
                                  "cededPremium": 1000.0000,
                                  "cededLoss": 250.0000,
                                  "currency": "USD"
                                }
                                """.formatted(POLICY, CONTRACT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_SCOPE_VIOLATION"));

        verifyNoInteractions(client);
    }

    @Test
    void rejectsMalformedTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/cessions")
                        .header("X-EIA-Tenant", "not-a-uuid")
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_INVALID"));
    }

    @Test
    void listsCessionsForDirectBook() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(dto(CessionStatus.OPEN, 0)));

        mvc.perform(get("/api/v1/cessions")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cessionNumber").value("CES-000000001"))
                .andExpect(jsonPath("$[0].businessBook").value("DIRECT"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void moneyFieldsAreSerializedAsDecimalStrings() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(dto(CessionStatus.OPEN, 0)));

        mvc.perform(get("/api/v1/cessions")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cededPremium").isString())
                .andExpect(jsonPath("$[0].cededPremium").value("1000.0000"))
                .andExpect(jsonPath("$[0].cededLoss").isString())
                .andExpect(jsonPath("$[0].cededLoss").value("250.0000"));
    }

    @Test
    void returnsStructured404ForMissingCession() throws Exception {
        when(client.findById(TENANT, ID))
                .thenThrow(DomainException.notFound("Cession not found: " + ID));

        mvc.perform(get("/api/v1/cessions/{id}", ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Cession not found: " + ID));
    }

    @Test
    void createsCession() throws Exception {
        when(client.create(eq(TENANT), any())).thenReturn(dto(CessionStatus.OPEN, 0));

        mvc.perform(post("/api/v1/cessions")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyId": "%s",
                                  "cessionContractId": "%s",
                                  "cededPremium": 1000.0000,
                                  "cededLoss": 250.0000,
                                  "currency": "USD"
                                }
                                """.formatted(POLICY, CONTRACT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cessionNumber").value("CES-000000001"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void rejectsNegativeCededLossBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/cessions")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyId": "%s",
                                  "cessionContractId": "%s",
                                  "cededPremium": 1000.0000,
                                  "cededLoss": -0.0100,
                                  "currency": "USD"
                                }
                                """.formatted(POLICY, CONTRACT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.details.cededLoss").value("must be zero or greater"));
    }

    @Test
    void rejectsSkippedLifecycleTransition() throws Exception {
        when(client.findById(TENANT, ID)).thenReturn(dto(CessionStatus.OPEN, 0));

        mvc.perform(post("/api/v1/cessions/{id}/transition", ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "CONFIRMED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void advancesStatusThroughDomain() throws Exception {
        when(client.findById(TENANT, ID)).thenReturn(dto(CessionStatus.OPEN, 0));
        when(client.updateStatus(TENANT, ID, CessionStatus.RULE_APPLIED, 0))
                .thenReturn(dto(CessionStatus.RULE_APPLIED, 1));

        mvc.perform(post("/api/v1/cessions/{id}/transition", ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "RULE_APPLIED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RULE_APPLIED"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void reportsExpertOutageAs503() throws Exception {
        when(client.findAll(TENANT))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("connection refused"));

        mvc.perform(get("/api/v1/cessions")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .header("X-EIA-Book", "DIRECT"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXPERT_UNAVAILABLE"));
    }
}

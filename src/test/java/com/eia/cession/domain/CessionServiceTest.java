package com.eia.cession.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CessionServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID ID = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID POLICY = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID CONTRACT = UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @Mock
    private CessionClient client;

    @Mock
    private FsriCessionClient fsriClient;

    @InjectMocks
    private CessionService service;

    private static CreateCessionCommand command(String premium, String loss, String currency) {
        return new CreateCessionCommand(POLICY, CONTRACT,
                new BigDecimal(premium), new BigDecimal(loss), currency);
    }

    private static CessionDto dto(CessionStatus status, int version) {
        return new CessionDto(ID, TENANT, "DIRECT", "CES-000000001", POLICY, CONTRACT, status,
                new BigDecimal("1000.0000"), new BigDecimal("250.0000"), "USD", version, NOW, NOW,
                null, null, null, null);
    }

    private static CessionDto dto(CessionStatus status, int version, String premium, String loss) {
        return new CessionDto(ID, TENANT, "DIRECT", "CES-000000001", POLICY, CONTRACT, status,
                new BigDecimal(premium), new BigDecimal(loss), "USD", version, NOW, NOW,
                null, null, null, null);
    }

    private static CessionDto handedOffDto() {
        return new CessionDto(ID, TENANT, "DIRECT", "CES-000000001", POLICY, CONTRACT,
                CessionStatus.HANDED_OFF, new BigDecimal("1000.0000"), new BigDecimal("250.0000"),
                "USD", 1, NOW, NOW,
                "FSRI-CES-DIRECT-000001", "POSTED", NOW, null);
    }

    private static FsriHandoffConfirmation confirmation() {
        return new FsriHandoffConfirmation(
                "FSRI-CES-DIRECT-000001", "POSTED", false, "FSRI-CNF-DIRECT-000001",
                new BigDecimal("250.0000"), new BigDecimal("250.0000"), new BigDecimal("0.0000"),
                "USD", NOW, "fsri-cession-" + ID + "-v1");
    }

    @Test
    void rejectsNegativeCededPremium() {
        assertThatThrownBy(() -> service.create(TENANT, command("-1.0000", "0.0000", "USD")))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("VALIDATION");
                    assertThat(ex.getDetails()).containsEntry("cededPremium", "must be zero or greater");
                });

        verify(client, never()).create(any(), any());
    }

    @Test
    void rejectsNullCededLoss() {
        assertThatThrownBy(() -> service.create(TENANT,
                new CreateCessionCommand(POLICY, CONTRACT, BigDecimal.TEN, null, "USD")))
                .isInstanceOfSatisfying(DomainException.class, ex ->
                        assertThat(ex.getDetails()).containsEntry("cededLoss", "must not be null"));
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> service.create(TENANT, command("1.0000", "0.0000", "EURO")))
                .isInstanceOfSatisfying(DomainException.class, ex ->
                        assertThat(ex.getDetails()).containsEntry("currency", "must be a three-letter currency code"));
    }

    @Test
    void delegatesCreateToExpert() {
        when(client.create(eq(TENANT), any())).thenReturn(dto(CessionStatus.OPEN, 0));

        CessionDto created = service.create(TENANT, command("1000.0000", "250.0000", "USD"));

        assertThat(created.status()).isEqualTo(CessionStatus.OPEN);
        assertThat(created.businessBook()).isEqualTo("DIRECT");
        verify(client).create(eq(TENANT), any());
    }

    @Test
    void transitionReadsCurrentVersionAndDelegates() {
        when(client.findById(TENANT, ID)).thenReturn(dto(CessionStatus.OPEN, 0));
        when(client.updateStatus(TENANT, ID, CessionStatus.RULE_APPLIED, 0))
                .thenReturn(dto(CessionStatus.RULE_APPLIED, 1));

        CessionDto updated = service.transition(TENANT, ID, CessionStatus.RULE_APPLIED);

        assertThat(updated.status()).isEqualTo(CessionStatus.RULE_APPLIED);
        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    void transitionRejectsIllegalEdgeWithoutUpdating() {
        when(client.findById(TENANT, ID)).thenReturn(dto(CessionStatus.OPEN, 0));

        assertThatThrownBy(() -> service.transition(TENANT, ID, CessionStatus.HANDED_OFF))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("INVALID_STATUS_TRANSITION");
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                });

        verify(client, never()).updateStatus(any(), any(), any(), anyInt());
    }

    @Test
    void handoffCallsFsriBeforePersistingTheAcceptedState() {
        when(client.findById(TENANT, ID)).thenReturn(dto(CessionStatus.RULE_APPLIED, 0));
        when(fsriClient.handoff(any())).thenReturn(confirmation());
        when(client.recordHandoff(eq(TENANT), eq(ID), any())).thenReturn(handedOffDto());

        CessionDto updated = service.transition(TENANT, ID, CessionStatus.HANDOFF_PENDING, "CES-2026-0001");

        assertThat(updated.status()).isEqualTo(CessionStatus.HANDED_OFF);
        assertThat(updated.fsriHandoffId()).isEqualTo("FSRI-CES-DIRECT-000001");
        assertThat(updated.fsriStatus()).isEqualTo("POSTED");

        verify(fsriClient).handoff(argThat(handoff ->
                handoff.transitionVersion() == 1
                        && "CES-2026-0001".equals(handoff.contractNumber())
                        && "CES-000000001".equals(handoff.cessionNumber())
                        && handoff.expectedAmount().compareTo(new BigDecimal("250.0000")) == 0
                        && handoff.effectiveDate().toString().equals("2026-10-19")));
        verify(client).recordHandoff(eq(TENANT), eq(ID), argThat(record ->
                record.expectedVersion() == 0
                        && "FSRI-CES-DIRECT-000001".equals(record.fsriHandoffId())
                        && "POSTED".equals(record.fsriStatus())
                        && NOW.equals(record.fsriHandedOffAt())));
        verify(client, never()).updateStatus(any(), any(), any(), anyInt());
    }

    @Test
    void handoffDefaultsToTheCessionNumberAsFsriContractNumber() {
        when(client.findById(TENANT, ID)).thenReturn(dto(CessionStatus.RULE_APPLIED, 0));
        when(fsriClient.handoff(any())).thenReturn(confirmation());
        when(client.recordHandoff(eq(TENANT), eq(ID), any())).thenReturn(handedOffDto());

        service.transition(TENANT, ID, CessionStatus.HANDOFF_PENDING);

        verify(fsriClient).handoff(argThat(handoff ->
                "CES-000000001".equals(handoff.contractNumber())));
    }

    @Test
    void fsriFailureRecordsErrorAndLeavesTheCaseRetryable() {
        when(client.findById(TENANT, ID)).thenReturn(dto(CessionStatus.RULE_APPLIED, 0));
        when(fsriClient.handoff(any())).thenThrow(DomainException.fsriUnavailable(
                "FS-RI did not respond before the configured timeout", Map.of("cause", "ResourceAccessException")));
        when(client.recordHandoffFailure(eq(TENANT), eq(ID), any()))
                .thenReturn(dto(CessionStatus.RULE_APPLIED, 1));

        assertThatThrownBy(() -> service.transition(TENANT, ID, CessionStatus.HANDOFF_PENDING, "CES-2026-0001"))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(503);
                    assertThat(ex.getCode()).isEqualTo("FSRI_UNAVAILABLE");
                });

        verify(client).recordHandoffFailure(eq(TENANT), eq(ID), argThat(failure ->
                failure.expectedVersion() == 0
                        && failure.fsriLastError().contains("FSRI_UNAVAILABLE")));
        verify(client, never()).recordHandoff(any(), any(), any());
        verify(client, never()).updateStatus(any(), any(), any(), anyInt());
    }

    @Test
    void rejectionFromFsriIsRecordedBeforeTheCanonicalErrorIsReturned() {
        when(client.findById(TENANT, ID)).thenReturn(dto(CessionStatus.RULE_APPLIED, 0));
        when(fsriClient.handoff(any())).thenThrow(new DomainException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "FSRI_REJECTED",
                "FS-RI rejected the cession hand-off", Map.of("reasonCode", "FSRI-CESSION-REJECTED")));
        when(client.recordHandoffFailure(eq(TENANT), eq(ID), any()))
                .thenReturn(dto(CessionStatus.RULE_APPLIED, 1));

        assertThatThrownBy(() -> service.transition(TENANT, ID, CessionStatus.HANDOFF_PENDING, "CES-2026-0001"))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("FSRI_REJECTED");
                });

        verify(client).recordHandoffFailure(eq(TENANT), eq(ID), argThat(failure ->
                failure.fsriLastError().contains("FSRI_REJECTED")));
    }

    @Test
    void handoffRequiresAPositiveExpectedAmountBeforeCallingFsri() {
        when(client.findById(TENANT, ID))
                .thenReturn(dto(CessionStatus.RULE_APPLIED, 0, "0.0000", "0.0000"));

        assertThatThrownBy(() -> service.transition(TENANT, ID, CessionStatus.HANDOFF_PENDING, "CES-2026-0001"))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getDetails()).containsEntry("expectedAmount",
                            "must be greater than zero for an FS-RI hand-off");
                });

        verifyNoInteractions(fsriClient);
        verify(client, never()).recordHandoffFailure(any(), any(), any());
    }

    @Test
    void directHandedOffTargetRequiresAnFsriHandoff() {
        when(client.findById(TENANT, ID)).thenReturn(dto(CessionStatus.HANDOFF_PENDING, 1));

        assertThatThrownBy(() -> service.transition(TENANT, ID, CessionStatus.HANDED_OFF))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                    assertThat(ex.getCode()).isEqualTo("HANDOFF_REQUIRED");
                });

        verify(client, never()).updateStatus(any(), any(), any(), anyInt());
        verifyNoInteractions(fsriClient);
    }

    @Test
    void fsriSuccessThatCannotBePersistedIsReportedAsBadGateway() {
        when(client.findById(TENANT, ID)).thenReturn(dto(CessionStatus.RULE_APPLIED, 0));
        when(fsriClient.handoff(any())).thenReturn(confirmation());
        when(client.recordHandoff(eq(TENANT), eq(ID), any())).thenThrow(DomainException.fsriConflict(
                "Cession was modified concurrently", Map.of("reason", "VERSION")));

        assertThatThrownBy(() -> service.transition(TENANT, ID, CessionStatus.HANDOFF_PENDING, "CES-2026-0001"))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(502);
                    assertThat(ex.getCode()).isEqualTo("FSRI_HANDOFF_NOT_RECORDED");
                    assertThat(ex.getDetails()).containsEntry("fsriHandoffId", "FSRI-CES-DIRECT-000001");
                });
    }

    @Test
    void lifecycleAllowsRejectionRetryAndTerminalStates() {
        assertThat(CessionStatus.HANDOFF_PENDING.canTransitionTo(CessionStatus.REJECTED)).isTrue();
        assertThat(CessionStatus.REJECTED.canTransitionTo(CessionStatus.HANDOFF_PENDING)).isTrue();
        assertThat(CessionStatus.HANDED_OFF.canTransitionTo(CessionStatus.CONFIRMED)).isTrue();
        assertThat(CessionStatus.CONFIRMED.isTerminal()).isTrue();
        assertThat(CessionStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(CessionStatus.CONFIRMED.canTransitionTo(CessionStatus.CANCELLED)).isFalse();
    }
}

package com.eia.cession.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Transition command. {@code fsriContractNumber} is optional and only used for
 * the FS-RI hand-off: it identifies the contract master that FS-RI already
 * knows. When omitted the cession number is used.
 */
public record TransitionCessionCommand(
        @NotNull CessionStatus targetStatus,
        @Size(max = 32, message = "must be at most 32 characters")
        @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{3,31}",
                message = "must match an FS-RI contract number (4-32 uppercase alphanumerics or hyphens)")
        String fsriContractNumber
) {
    public TransitionCessionCommand(CessionStatus targetStatus) {
        this(targetStatus, null);
    }
}

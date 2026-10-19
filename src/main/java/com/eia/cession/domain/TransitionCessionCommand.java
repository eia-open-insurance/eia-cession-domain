package com.eia.cession.domain;

import jakarta.validation.constraints.NotNull;

public record TransitionCessionCommand(
        @NotNull CessionStatus targetStatus
) {}

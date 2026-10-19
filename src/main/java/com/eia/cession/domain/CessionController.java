package com.eia.cession.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cessions")
public class CessionController {
    private final CessionService service;

    public CessionController(CessionService service) {
        this.service = service;
    }

    @GetMapping
    public List<CessionDto> list(
            @RequestHeader(value = RequestScope.TENANT_HEADER, required = false) String tenantHeader,
            @RequestHeader(value = RequestScope.BOOK_HEADER, required = false) String bookHeader) {
        UUID tenantId = RequestScope.tenant(tenantHeader);
        RequestScope.requireDirectBook(bookHeader);
        return service.list(tenantId);
    }

    @GetMapping("/{id}")
    public CessionDto get(
            @RequestHeader(value = RequestScope.TENANT_HEADER, required = false) String tenantHeader,
            @RequestHeader(value = RequestScope.BOOK_HEADER, required = false) String bookHeader,
            @PathVariable UUID id) {
        UUID tenantId = RequestScope.tenant(tenantHeader);
        RequestScope.requireDirectBook(bookHeader);
        return service.get(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CessionDto create(
            @RequestHeader(value = RequestScope.TENANT_HEADER, required = false) String tenantHeader,
            @RequestHeader(value = RequestScope.BOOK_HEADER, required = false) String bookHeader,
            @Valid @RequestBody CreateCessionCommand command) {
        UUID tenantId = RequestScope.tenant(tenantHeader);
        RequestScope.requireDirectBook(bookHeader);
        return service.create(tenantId, command);
    }

    @PostMapping("/{id}/transition")
    public CessionDto transition(
            @RequestHeader(value = RequestScope.TENANT_HEADER, required = false) String tenantHeader,
            @RequestHeader(value = RequestScope.BOOK_HEADER, required = false) String bookHeader,
            @PathVariable UUID id,
            @Valid @RequestBody TransitionCessionCommand command) {
        UUID tenantId = RequestScope.tenant(tenantHeader);
        RequestScope.requireDirectBook(bookHeader);
        return service.transition(tenantId, id, command.targetStatus());
    }
}

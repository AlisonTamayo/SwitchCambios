package com.bancario.compensacion.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import com.bancario.compensacion.dto.CreateSolicitudDevolucionRequest;
import com.bancario.compensacion.dto.SolicitudDevolucionDTO;
import com.bancario.compensacion.service.SolicitudDevolucionService;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions/returns")
@Tag(name = "Devoluciones", description = "Gestión de devoluciones (reversos) del switch transaccional")
public class SolicitudDevolucionController {

    private final SolicitudDevolucionService service;

    public SolicitudDevolucionController(SolicitudDevolucionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar solicitud de devolución (reverso)")
    public SolicitudDevolucionDTO create(@Valid @RequestBody CreateSolicitudDevolucionRequest req) {
        log.info("POST /transactions/returns id={}", req.getId());
        return service.create(req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una devolución por id")
    public SolicitudDevolucionDTO getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping
    @Operation(summary = "Buscar devoluciones por idInstruccionOriginal")
    public List<SolicitudDevolucionDTO> findByOriginal(@RequestParam UUID idInstruccionOriginal) {
        return service.findByOriginal(idInstruccionOriginal);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Actualizar estado de devolución")
    public SolicitudDevolucionDTO updateStatus(
            @PathVariable UUID id,
            @RequestParam @Pattern(regexp = "RECEIVED|REVERSED|FAILED") String estado
    ) {
        return service.updateEstado(id, estado);
    }
}

package com.bancario.devolucion.controlador;

import com.bancario.devolucion.dto.SolicitudCreacionDTO;
import com.bancario.devolucion.dto.SolicitudDevolucionDTO;
import com.bancario.devolucion.servicio.SolicitudDevolucionServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/devoluciones")
@RequiredArgsConstructor
@Tag(name = "Gestión de Devoluciones", description = "Endpoints para manejo de Returns (pacs.004)")
public class SolicitudDevolucionControlador {

    private final SolicitudDevolucionServicio service;

    @PostMapping
    @Operation(summary = "Crear solicitud de devolución", description = "Inicia un proceso de retorno basado en ISO 20022.")
    public ResponseEntity<SolicitudDevolucionDTO> crear(@RequestBody SolicitudCreacionDTO req) {
        return new ResponseEntity<>(service.create(req), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener por ID")
    public ResponseEntity<SolicitudDevolucionDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/original/{idInstruccionOriginal}")
    @Operation(summary = "Buscar devoluciones asociadas a una transacción original")
    public ResponseEntity<List<SolicitudDevolucionDTO>> buscarPorOriginal(@PathVariable UUID idInstruccionOriginal) {
        return ResponseEntity.ok(service.findByOriginal(idInstruccionOriginal));
    }

    @PutMapping("/{id}/estado")
    @Operation(summary = "Actualizar estado")
    public ResponseEntity<SolicitudDevolucionDTO> actualizarEstado(
            @PathVariable UUID id,
            @RequestParam String estado) {
        return ResponseEntity.ok(service.updateEstado(id, estado));
    }
}

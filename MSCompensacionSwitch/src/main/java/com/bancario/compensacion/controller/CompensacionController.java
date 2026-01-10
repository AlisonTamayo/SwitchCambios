package com.bancario.compensacion.controller;

import com.bancario.compensacion.dto.ArchivoDTO;
import com.bancario.compensacion.dto.CicloDTO;
import com.bancario.compensacion.dto.PosicionDTO;
import com.bancario.compensacion.service.CompensacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/compensacion")
@RequiredArgsConstructor
@Tag(name = "Microservicio de Compensación", description = "Endpoints para la gestión de liquidación y compensación bancaria")
public class CompensacionController {

    private final CompensacionService service;

    @GetMapping("/ciclos")
    @Operation(summary = "Listar ciclos", description = "Obtiene todos los ciclos de compensación registrados")
    public ResponseEntity<List<CicloDTO>> listarCiclos() {
        return ResponseEntity.ok(service.listarCiclos());
    }

    @PostMapping("/ciclos")
    @Operation(summary = "Crear ciclo", description = "Registra un nuevo ciclo de compensación")
    public ResponseEntity<CicloDTO> crearCiclo(@Valid @RequestBody CicloDTO dto) {
        return new ResponseEntity<>(service.crearCiclo(dto), HttpStatus.CREATED);
    }

    @GetMapping("/ciclos/{id}")
    @Operation(summary = "Obtener ciclo", description = "Obtiene los detalles de un ciclo específico")
    public ResponseEntity<CicloDTO> obtenerCiclo(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerCiclo(id));
    }

    @PostMapping("/posiciones")
    @Operation(summary = "Registrar posición", description = "Registra la posición neta de una institución en un ciclo")
    public ResponseEntity<PosicionDTO> registrarPosicion(@Valid @RequestBody PosicionDTO dto) {
        return new ResponseEntity<>(service.registrarPosicion(dto), HttpStatus.CREATED);
    }

    @GetMapping("/ciclos/{cicloId}/posiciones")
    @Operation(summary = "Listar posiciones por ciclo", description = "Obtiene todas las posiciones netas asociadas a un ciclo")
    public ResponseEntity<List<PosicionDTO>> listarPosiciones(@PathVariable Integer cicloId) {
        return ResponseEntity.ok(service.listarPosicionesPorCiclo(cicloId));
    }

    @PostMapping("/archivos")
    @Operation(summary = "Registrar archivo", description = "Registra un archivo de liquidación generado para un ciclo")
    public ResponseEntity<ArchivoDTO> registrarArchivo(@Valid @RequestBody ArchivoDTO dto) {
        return new ResponseEntity<>(service.registrarArchivo(dto), HttpStatus.CREATED);
    }

    @PostMapping("/ciclos/{cicloId}/acumular")
    @Operation(summary = "INTERNAL: Acumular movimiento (Usado por Nucleo)",
            description = "Suma un monto a los débitos o créditos de un banco en tiempo real.")
    public ResponseEntity<Void> acumular(
            @PathVariable Integer cicloId,
            @RequestParam String bic,
            @RequestParam BigDecimal monto,
            @RequestParam boolean esDebito) {

        service.acumularMovimiento(cicloId, bic, monto, esDebito);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/ciclos/{cicloId}/archivos")
    @Operation(summary = "Listar archivos por ciclo", description = "Obtiene todos los archivos de liquidación generados para un ciclo")
    public ResponseEntity<List<ArchivoDTO>> listarArchivos(@PathVariable Integer cicloId) {
        return ResponseEntity.ok(service.listarArchivosPorCiclo(cicloId));
    }

    @PostMapping("/ciclos/{cicloId}/cierre")
    @Operation(summary = "EJECUTAR CIERRE DIARIO", description = "Verifica suma cero, genera archivo XML y cierra el ciclo.")
    public ResponseEntity<ArchivoDTO> cerrarCiclo(@PathVariable Integer cicloId) {
        return ResponseEntity.ok(service.realizarCierreDiario(cicloId));
    }
    
}

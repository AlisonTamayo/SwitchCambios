package com.bancario.compensacion.controlador;

import com.bancario.compensacion.dto.ArchivoDTO;
import com.bancario.compensacion.dto.CicloDTO;
import com.bancario.compensacion.dto.PosicionDTO;
import com.bancario.compensacion.servicio.CompensacionServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/compensacion")
@RequiredArgsConstructor
@Tag(name = "Microservicio de Compensación (G4)", description = "Gestión de Clearing, Settlement y Continuidad")
public class CompensacionControlador {

    private final CompensacionServicio service;

    @GetMapping("/ciclos")
    @Operation(summary = "Listar ciclos", description = "Obtiene el historial de todos los ciclos operativos.")
    public ResponseEntity<List<CicloDTO>> listarCiclos() {
        return ResponseEntity.ok(service.listarCiclos());
    }

    @GetMapping("/ciclos/{cicloId}/posiciones")
    @Operation(summary = "Obtener detalle de posiciones", description = "Ver acumulados netos por banco")
    public ResponseEntity<List<PosicionDTO>> obtenerPosiciones(@PathVariable Integer cicloId) {
        return ResponseEntity.ok(service.obtenerPosicionesCiclo(cicloId));
    }

    @PostMapping("/ciclos/{cicloId}/acumular")
    @Operation(summary = "INTERNAL: Acumular movimiento (Deprecated)", description = "Use el endpoint sin ID para autodetectar ciclo.")
    public ResponseEntity<Void> acumular(
            @PathVariable Integer cicloId,
            @RequestParam String bic,
            @RequestParam BigDecimal monto,
            @RequestParam boolean esDebito) {

        service.acumularTransaccion(cicloId, bic, monto, esDebito);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/acumular")
    @Operation(summary = "INTERNAL: Acumular movimiento (Auto-Ciclo)", description = "Registra débitos/créditos en el ciclo ABIERTO actual.")
    public ResponseEntity<Void> acumularAuto(
            @RequestParam String bic,
            @RequestParam BigDecimal monto,
            @RequestParam boolean esDebito) {

        service.acumularEnCicloAbierto(bic, monto, esDebito);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ciclos/{cicloId}/cierre")
    @Operation(summary = "EJECUTAR CIERRE DIARIO (Settlement)", description = "Cierra ciclo actual y programa el siguiente con la duración especificada (o 10 min por defecto).")
    public ResponseEntity<ArchivoDTO> cerrarCiclo(
            @PathVariable Integer cicloId,
            @RequestParam(required = false, defaultValue = "10") Integer proximoCicloEnMinutos) {
        return ResponseEntity.ok(service.realizarCierreDiario(cicloId, proximoCicloEnMinutos));
    }

    @GetMapping("/reporte/pdf/{cicloId}")
    @Operation(summary = "Descargar Reporte PDF", description = "Genera visualización imprimible del ciclo.")
    public ResponseEntity<byte[]> descargarReportePDF(@PathVariable Integer cicloId) {
        byte[] pdf = service.generarReportePDF(cicloId);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=Reporte_Ciclo_" + cicloId + ".pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}

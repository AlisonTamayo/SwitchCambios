package com.switchbank.mscontabilidad.controlador;

import com.switchbank.mscontabilidad.dto.CrearCuentaRequest;
import com.switchbank.mscontabilidad.dto.CuentaDTO;
import com.switchbank.mscontabilidad.dto.MovimientoDTO;
import com.switchbank.mscontabilidad.dto.RegistroMovimientoRequest;
import com.switchbank.mscontabilidad.dto.ReturnRequestDTO;
import com.switchbank.mscontabilidad.servicio.ContabilidadServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Tag(name = "Contabilidad (Ledger)", description = "Gestión del Libro Mayor y Saldos Bancarios")
public class ContabilidadControlador {

    private final ContabilidadServicio servicio;

    @PostMapping("/cuentas")
    @Operation(summary = "Crear Cuenta Técnica", description = "Inicializa una cuenta técnica para un Banco.")
    public ResponseEntity<CuentaDTO> crearCuenta(@RequestBody CrearCuentaRequest req) {
        return ResponseEntity.ok(servicio.crearCuenta(req));
    }

    @GetMapping("/cuentas/{bic}")
    @Operation(summary = "Consultar Saldo", description = "Obtiene el saldo disponible y verificación de integridad.")
    public ResponseEntity<CuentaDTO> obtenerSaldo(@PathVariable String bic) {
        return ResponseEntity.ok(servicio.obtenerCuenta(bic));
    }

    @PostMapping("/movimientos")
    @Operation(summary = "Registrar Movimiento", description = "Debita o Acredita fondos en la cuenta técnica.")
    public ResponseEntity<CuentaDTO> registrarMovimiento(@RequestBody RegistroMovimientoRequest req) {
        return ResponseEntity.ok(servicio.registrarMovimiento(req));
    }

    @PostMapping("/v2/switch/transfers/return")
    @Operation(summary = "Reversar Transacción", description = "Ejecuta una devolución o reverso contable.")
    public ResponseEntity<CuentaDTO> revertirTransaccion(@RequestBody ReturnRequestDTO req) {
        return ResponseEntity.ok(servicio.revertirTransaccion(req));
    }

    @GetMapping("/range")
    @Operation(summary = "Movimientos por Rango", description = "Auditoría de movimientos por fecha.")
    public ResponseEntity<List<MovimientoDTO>> obtenerMovimientosPorRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(servicio.obtenerMovimientosPorRango(start, end));
    }
}

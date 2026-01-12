package com.switchbank.mscontabilidad.controlador;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.switchbank.mscontabilidad.dto.CrearCuentaRequest;
import com.switchbank.mscontabilidad.dto.CuentaDTO;
import com.switchbank.mscontabilidad.dto.RegistroMovimientoRequest;
import com.switchbank.mscontabilidad.servicio.LedgerService;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService service;

    public LedgerController(LedgerService service) {
        this.service = service;
    }

    @PostMapping("/cuentas")
    public ResponseEntity<CuentaDTO> crearCuenta(@RequestBody CrearCuentaRequest req) {
        return ResponseEntity.ok(service.crearCuenta(req));
    }

    @GetMapping("/cuentas/{bic}")
    public ResponseEntity<CuentaDTO> obtenerSaldo(@PathVariable String bic) {
        return ResponseEntity.ok(service.obtenerCuenta(bic));
    }

    @PostMapping("/movimientos")
    public ResponseEntity<?> registrarMovimiento(@RequestBody RegistroMovimientoRequest req) { // Nota el <?>
        try {
            return ResponseEntity.ok(service.registrarMovimiento(req));
        } catch (RuntimeException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    static class ApiError {
        private String error;
    }

    @PostMapping("/v2/switch/transfers/return")
    public ResponseEntity<?> revertirTransaccion(@RequestBody com.switchbank.mscontabilidad.dto.ReturnRequestDTO req) {
        try {
            return ResponseEntity.ok(service.revertirTransaccion(req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/range")
    public ResponseEntity<?> obtenerMovimientosPorRango(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime start,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime end) {
        return ResponseEntity.ok(service.obtenerMovimientosPorRango(start, end));
    }

    @lombok.Data
    static class ReversoRequest {
        private java.util.UUID idInstruccion;
    }
}
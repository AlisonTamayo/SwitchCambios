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
            e.printStackTrace(); // Ver error en logs de Docker
            // Devolver el mensaje de error al cliente (Postman/Curl)
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    // Clase interna simple para devolver JSON de error
    @lombok.Data
    @lombok.AllArgsConstructor
    static class ApiError {
        private String error;
    }
}
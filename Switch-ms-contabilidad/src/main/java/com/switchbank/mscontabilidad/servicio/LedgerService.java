package com.switchbank.mscontabilidad.servicio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.switchbank.mscontabilidad.dto.CrearCuentaRequest;
import com.switchbank.mscontabilidad.dto.CuentaDTO;
import com.switchbank.mscontabilidad.dto.RegistroMovimientoRequest;
import com.switchbank.mscontabilidad.modelo.CuentaTecnica;
import com.switchbank.mscontabilidad.modelo.Movimiento;
import com.switchbank.mscontabilidad.modelo.TipoMovimiento;
import com.switchbank.mscontabilidad.repositorio.CuentaTecnicaRepository;
import com.switchbank.mscontabilidad.repositorio.MovimientoRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class LedgerService {

    private final CuentaTecnicaRepository cuentaRepo;
    private final MovimientoRepository movimientoRepo;

    public LedgerService(CuentaTecnicaRepository cuentaRepo, MovimientoRepository movimientoRepo) {
        this.cuentaRepo = cuentaRepo;
        this.movimientoRepo = movimientoRepo;
    }

    @Transactional
    public CuentaDTO crearCuenta(CrearCuentaRequest req) {
        if (cuentaRepo.findByCodigoBic(req.getCodigoBic()).isPresent()) {
            throw new RuntimeException("Cuenta ya existe para el BIC: " + req.getCodigoBic());
        }

        CuentaTecnica cuenta = new CuentaTecnica(req.getCodigoBic());
        cuenta.setFirmaIntegridad(calcularHash(cuenta)); // Hash inicial

        CuentaTecnica saved = cuentaRepo.save(cuenta);
        return mapToDTO(saved);
    }

    @Transactional
    public CuentaDTO registrarMovimiento(RegistroMovimientoRequest req) {
        CuentaTecnica cuenta = cuentaRepo.findByCodigoBic(req.getCodigoBic())
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada para BIC: " + req.getCodigoBic()));

        // 1. Validar integridad antes de operar
        String hashActual = calcularHash(cuenta);
        if (!hashActual.equals(cuenta.getFirmaIntegridad())) {
            throw new RuntimeException("ALERTA DE SEGURIDAD: La cuenta " + req.getCodigoBic() + " ha sido alterada manualmente.");
        }

        TipoMovimiento tipo = TipoMovimiento.valueOf(req.getTipo());

        // 2. Lógica de Saldo
        if (tipo == TipoMovimiento.DEBIT) {
            if (cuenta.getSaldoDisponible().compareTo(req.getMonto()) < 0) {
                throw new RuntimeException("FONDOS INSUFICIENTES para el banco: " + req.getCodigoBic());
            }
            cuenta.setSaldoDisponible(cuenta.getSaldoDisponible().subtract(req.getMonto()));
        } else {
            cuenta.setSaldoDisponible(cuenta.getSaldoDisponible().add(req.getMonto()));
        }

        // 3. Registrar Movimiento
        Movimiento mov = new Movimiento();
        mov.setCuenta(cuenta);
        mov.setIdInstruccion(req.getIdInstruccion());
        mov.setTipo(tipo);
        mov.setMonto(req.getMonto());
        mov.setSaldoResultante(cuenta.getSaldoDisponible());
        mov.setFechaRegistro(LocalDateTime.now());
        movimientoRepo.save(mov);

        // 4. Actualizar Hash de Integridad y Guardar Cuenta
        cuenta.setFirmaIntegridad(calcularHash(cuenta));
        CuentaTecnica saved = cuentaRepo.save(cuenta);

        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public CuentaDTO obtenerCuenta(String bic) {
        CuentaTecnica cuenta = cuentaRepo.findByCodigoBic(bic)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
        return mapToDTO(cuenta);
    }

    // --- Utilidades ---

    private String calcularHash(CuentaTecnica c) {
        try {
            // CORRECCIÓN: Forzamos siempre 2 decimales (Scale 2) para evitar problemas de "0" vs "0.00"
            // RoundingMode.HALF_UP es el estándar bancario común.
            String saldoFormateado = c.getSaldoDisponible()
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .toString();

            String data = c.getCodigoBic() + ":" + saldoFormateado;
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error calculando hash", e);
        }
    }

    private CuentaDTO mapToDTO(CuentaTecnica c) {
        return CuentaDTO.builder()
                .id(c.getId())
                .codigoBic(c.getCodigoBic())
                .saldoDisponible(c.getSaldoDisponible())
                .firmaIntegridad(c.getFirmaIntegridad())
                .build();
    }
}
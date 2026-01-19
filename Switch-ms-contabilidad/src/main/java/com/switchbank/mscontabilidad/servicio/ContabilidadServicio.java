package com.switchbank.mscontabilidad.servicio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.switchbank.mscontabilidad.dto.CrearCuentaRequest;
import com.switchbank.mscontabilidad.dto.CuentaDTO;
import com.switchbank.mscontabilidad.dto.MovimientoDTO;
import com.switchbank.mscontabilidad.dto.RegistroMovimientoRequest;
import com.switchbank.mscontabilidad.dto.ReturnRequestDTO;
import com.switchbank.mscontabilidad.modelo.CuentaTecnica;
import com.switchbank.mscontabilidad.modelo.Movimiento;
import com.switchbank.mscontabilidad.modelo.TipoMovimiento;
import com.switchbank.mscontabilidad.repositorio.CuentaTecnicaRepository;
import com.switchbank.mscontabilidad.repositorio.MovimientoRepository;
import com.switchbank.mscontabilidad.mapper.ContabilidadMapper;

import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContabilidadServicio {

    private final CuentaTecnicaRepository cuentaRepo;
    private final MovimientoRepository movimientoRepo;
    private final ContabilidadMapper mapper;

    @Transactional
    public CuentaDTO crearCuenta(CrearCuentaRequest req) {
        if (cuentaRepo.findByCodigoBic(req.getCodigoBic()).isPresent()) {
            throw new RuntimeException("Cuenta ya existe para el BIC: " + req.getCodigoBic());
        }

        CuentaTecnica cuenta = new CuentaTecnica(req.getCodigoBic());
        cuenta.setFirmaIntegridad(calcularHash(cuenta));

        CuentaTecnica saved = cuentaRepo.save(cuenta);
        return mapper.toDTO(saved);
    }

    @Transactional
    public CuentaDTO registrarMovimiento(RegistroMovimientoRequest req) {
        CuentaTecnica cuenta = cuentaRepo.findByCodigoBic(req.getCodigoBic())
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada para BIC: " + req.getCodigoBic()));

        String hashActual = calcularHash(cuenta);
        if (!hashActual.equals(cuenta.getFirmaIntegridad())) {
            throw new RuntimeException(
                    "ALERTA DE SEGURIDAD: La cuenta " + req.getCodigoBic() + " ha sido alterada manualmente.");
        }

        TipoMovimiento tipo = TipoMovimiento.valueOf(req.getTipo());

        if (tipo == TipoMovimiento.DEBIT) {
            if (cuenta.getSaldoDisponible().compareTo(req.getMonto()) < 0) {
                throw new RuntimeException("FONDOS INSUFICIENTES para el banco: " + req.getCodigoBic());
            }
            cuenta.setSaldoDisponible(cuenta.getSaldoDisponible().subtract(req.getMonto()));
        } else {
            cuenta.setSaldoDisponible(cuenta.getSaldoDisponible().add(req.getMonto()));
        }

        Movimiento mov = new Movimiento();
        mov.setCuenta(cuenta);
        mov.setIdInstruccion(req.getIdInstruccion());
        mov.setTipo(tipo);
        mov.setMonto(req.getMonto());
        mov.setSaldoResultante(cuenta.getSaldoDisponible());
        mov.setFechaRegistro(LocalDateTime.now());
        movimientoRepo.save(mov);

        cuenta.setFirmaIntegridad(calcularHash(cuenta));
        CuentaTecnica saved = cuentaRepo.save(cuenta);

        return mapper.toDTO(saved);
    }

    public CuentaDTO obtenerCuenta(String bic) {
        CuentaTecnica cuenta = cuentaRepo.findByCodigoBic(bic)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
        return mapper.toDTO(cuenta);
    }

    @Transactional(readOnly = true)
    public boolean verificarSaldo(String bic, BigDecimal monto) {
        return cuentaRepo.findByCodigoBic(bic)
                .map(cuenta -> cuenta.getSaldoDisponible().compareTo(monto) >= 0)
                .orElse(false);
    }

    @Transactional
    public CuentaDTO recargarSaldo(String bic, BigDecimal monto, UUID idInstruccion) {

        if (!movimientoRepo.findByIdInstruccion(idInstruccion).isEmpty()) {

            CuentaTecnica cuenta = cuentaRepo.findByCodigoBic(bic)
                    .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
            return mapper.toDTO(cuenta);
        }

        CuentaTecnica cuenta = cuentaRepo.findByCodigoBic(bic)
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada para BIC: " + bic));

        String hashActual = calcularHash(cuenta);
        if (!hashActual.equals(cuenta.getFirmaIntegridad())) {
            throw new RuntimeException("ALERTA: Integridad comprometida en cuenta " + bic);
        }

        cuenta.setSaldoDisponible(cuenta.getSaldoDisponible().add(monto));

        Movimiento mov = new Movimiento();
        mov.setCuenta(cuenta);
        mov.setIdInstruccion(idInstruccion);
        mov.setTipo(TipoMovimiento.RECHARGE);
        mov.setMonto(monto);
        mov.setSaldoResultante(cuenta.getSaldoDisponible());
        mov.setFechaRegistro(LocalDateTime.now());
        movimientoRepo.save(mov);

        cuenta.setFirmaIntegridad(calcularHash(cuenta));
        return mapper.toDTO(cuentaRepo.save(cuenta));
    }

    @Transactional
    public CuentaDTO revertirTransaccion(ReturnRequestDTO req) {
        String originalIdStr = req.getBody().getOriginalInstructionId();
        if (originalIdStr == null) {
            throw new RuntimeException("originalInstructionId es obligatorio");
        }
        UUID originalInstructionId = UUID.fromString(originalIdStr);

        List<Movimiento> encontrados = movimientoRepo.findByIdInstruccion(originalInstructionId);
        if (encontrados.isEmpty()) {
            throw new RuntimeException("Transacción original no encontrada: " + originalInstructionId);
        }
        Movimiento original = encontrados.get(0);

        if (original.getFechaRegistro().isBefore(LocalDateTime.now().minusHours(48))) {
            throw new RuntimeException("La transacción original es mayor a 48 horas, no se puede revertir.");
        }
        if (movimientoRepo.existsByTipoAndReferenciaId(TipoMovimiento.REVERSAL, originalInstructionId)) {
            throw new RuntimeException("DUPLICADO: Esta transacción ya ha sido revertida anteriormente.");
        }

        BigDecimal montoSolicitado = req.getBody().getReturnAmount().getValue();
        if (montoSolicitado.compareTo(original.getMonto()) != 0) {
            throw new RuntimeException("El monto a revertir (" + montoSolicitado + ") no coincide con el original ("
                    + original.getMonto() + ")");
        }

        CuentaTecnica cuenta = original.getCuenta();

        String hashActual = calcularHash(cuenta);
        if (!hashActual.equals(cuenta.getFirmaIntegridad())) {
            throw new RuntimeException(
                    "ALERTA DE SEGURIDAD: La cuenta " + cuenta.getCodigoBic() + " ha sido alterada.");
        }

        TipoMovimiento tipoOriginal = original.getTipo();
        if (tipoOriginal == TipoMovimiento.REVERSAL) {
            throw new RuntimeException("No se puede revertir una reversión.");
        }

        if (tipoOriginal == TipoMovimiento.DEBIT) {
            cuenta.setSaldoDisponible(cuenta.getSaldoDisponible().add(montoSolicitado));
        } else {
            if (cuenta.getSaldoDisponible().compareTo(montoSolicitado) < 0) {
                throw new RuntimeException("Fondos insuficientes para revertir el crédito.");
            }
            cuenta.setSaldoDisponible(cuenta.getSaldoDisponible().subtract(montoSolicitado));
        }

        Movimiento reverso = new Movimiento();
        reverso.setCuenta(cuenta);

        String returnIdStr = req.getBody().getReturnInstructionId();
        reverso.setIdInstruccion(returnIdStr != null ? UUID.fromString(returnIdStr) : UUID.randomUUID());

        reverso.setReferenciaId(originalInstructionId);
        reverso.setTipo(TipoMovimiento.REVERSAL);
        reverso.setMonto(montoSolicitado);
        reverso.setSaldoResultante(cuenta.getSaldoDisponible());
        reverso.setFechaRegistro(LocalDateTime.now());

        movimientoRepo.save(reverso);

        cuenta.setFirmaIntegridad(calcularHash(cuenta));
        return mapper.toDTO(cuentaRepo.save(cuenta));
    }

    @Transactional(readOnly = true)
    public List<MovimientoDTO> obtenerMovimientosPorRango(LocalDateTime start, LocalDateTime end) {
        List<Movimiento> movimientos = movimientoRepo.findByFechaRegistroBetween(start, end);
        return mapper.toDTOList(movimientos);
    }

    private String calcularHash(CuentaTecnica c) {
        try {
            String secretKey = "SECRET_KEY_INTERNAL_LEDGER_V3";
            String saldoFormateado = c.getSaldoDisponible()
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .toString();

            String data = saldoFormateado + c.getCodigoBic() + secretKey;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error calculando hash", e);
        }
    }

}

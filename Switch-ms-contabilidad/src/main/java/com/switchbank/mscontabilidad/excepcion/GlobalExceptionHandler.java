package com.switchbank.mscontabilidad.excepcion;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<ErrorDTO> handleSaldoInsuficiente(SaldoInsuficienteException ex, HttpServletRequest request) {
        log.warn("Saldo Insuficiente: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "SALDO_INSUFICIENTE", ex.getMessage(), request);
    }

    @ExceptionHandler(CuentaNoEncontradaException.class)
    public ResponseEntity<ErrorDTO> handleCuentaNoEncontrada(CuentaNoEncontradaException ex,
            HttpServletRequest request) {
        log.warn("Cuenta No Encontrada: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "CUENTA_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorDTO> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.error("Error de Ejecución: ", ex);
        return buildResponse(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDTO> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled Exception: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Error inesperado del sistema contable", request);
    }

    private ResponseEntity<ErrorDTO> buildResponse(HttpStatus status, String codigo, String mensaje,
            HttpServletRequest request) {
        ErrorDTO error = ErrorDTO.builder()
                .codigo(codigo)
                .mensaje(mensaje)
                .fecha(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(error, status);
    }
}

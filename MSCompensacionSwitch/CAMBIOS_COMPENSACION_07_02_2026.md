# Cambios en Módulo de Compensación - 07/02/2026

Este documento detalla los cambios realizados en el microservicio `MS-COMPENSACIÓN` para cumplir con los requerimientos de eliminación de firma JWS e implementación del algoritmo de neteo.

## 1. Eliminación de Firma JWS

Se ha eliminado por completo la dependencia y el uso de firmas JWS en la generación de archivos de liquidación.

### Cambios Realizados:
- **Base de Datos**: Se eliminó la columna `firma_jws` de la tabla `archivoliquidacion`.
- **Entidad (`ArchivoLiquidacion`)**: Se eliminó el atributo `firmaJws`.
- **DTO (`ArchivoDTO`)**: Se eliminó el campo `firmaJws` para que no sea expuesto en la API.
- **Servicio (`CompensacionServicio`)**: 
  - Se eliminó la inyección de `SeguridadServicio`.
  - Se eliminó la llamada a `firmarDocumento`.
  - El XML generado (`xmlContenido`) se guarda tal cual, sin firmas adjuntas.

**Impacto**: El archivo XML de liquidación generado es texto plano XML puro, listo para ser firmado por un componente externo si fuera necesario, pero este microservicio ya no se encarga de ello.

---

## 2. Algoritmo de Neteo (Clearing)

Se implementó la lógica de compensación ("Clearing") que procesa los detalles de las transacciones para calcular los saldos finales de cada institución financiera al cierre del ciclo.

### Funcionamiento:

El proceso se ejecuta automáticamente durante el `cierreDiario`. El sistema recalculas las posiciones basándose estrictamente en los registros de la tabla `DetalleCompensacion`.

### Lógica de Operaciones:

El algoritmo clasifica cada movimiento y ajusta los saldos (Débitos y Créditos) de la siguiente manera:

#### A. Operación `PAGO` (Transferencia Normal)
Cuando un banco envía dinero a otro.
- **Banco Emisor (Origen)**: Se le **DEBITA** (Suma a sus `TotalDebitos`). *El banco debe pagar esta cantidad.*
- **Banco Receptor (Destino)**: Se le **ACREDITA** (Suma a sus `TotalCreditos`). *El banco debe recibir esta cantidad.*

#### B. Operación `REVERSO` (Devolución / Rechazo)
Cuando una transferencia anterior es devuelta o rechazada.
- **Banco Emisor (Origen Original)**: Se le **ACREDITA** (Suma a sus `TotalCreditos`). *Recibe el dinero de vuelta (Refund).*
- **Banco Receptor (Destino Original)**: Se le **DEBITA** (Suma a sus `TotalDebitos`). *Devuelve el dinero recibido indebidamente.*

### Cálculo Final (Posición Neta):
Para cada banco, se calcula:
`Posición Neta = Total Créditos - Total Débitos`

- **Positivo**: El sistema le debe dinero al banco.
- **Negativo**: El banco le debe dinero al sistema.

Esta lógica asegura que el archivo de liquidación refleje exactamente la realidad de todas las transacciones procesadas y sus devoluciones.

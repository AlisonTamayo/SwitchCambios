package com.bancario.compensacion.service;

import com.bancario.compensacion.dto.ArchivoDTO;
import com.bancario.compensacion.dto.CicloDTO;
import com.bancario.compensacion.dto.PosicionDTO;
import com.bancario.compensacion.exception.ResourceNotFoundException;
import com.bancario.compensacion.model.ArchivoLiquidacion;
import com.bancario.compensacion.model.CicloCompensacion;
import com.bancario.compensacion.model.PosicionInstitucion;
import com.bancario.compensacion.repository.ArchivoLiquidacionRepository;
import com.bancario.compensacion.repository.CicloCompensacionRepository;
import com.bancario.compensacion.repository.PosicionInstitucionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensacionService {

    private final CicloCompensacionRepository cicloRepository;
    private final PosicionInstitucionRepository posicionRepository;
    private final ArchivoLiquidacionRepository archivoRepository;

    @Transactional(readOnly = true)
    public List<CicloDTO> listarCiclos() {
        log.info("Listando todos los ciclos de compensación");
        return cicloRepository.findAll().stream()
                .map(this::mapToCicloDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public CicloDTO crearCiclo(CicloDTO dto) {
        log.info("Creando nuevo ciclo de compensación para fecha: {}", dto.getFechaCorte());
        CicloCompensacion entity = new CicloCompensacion();
        entity.setFechaCorte(dto.getFechaCorte());
        entity.setEstado(dto.getEstado());
        entity.setDescripcion(dto.getDescripcion());
        return mapToCicloDTO(cicloRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public CicloDTO obtenerCiclo(Integer id) {
        log.info("Obteniendo ciclo con ID: {}", id);
        return cicloRepository.findById(id)
                .map(this::mapToCicloDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Ciclo no encontrado con ID: " + id));
    }

    @Transactional
    public PosicionDTO registrarPosicion(PosicionDTO dto) {
        log.info("Registrando posición para BIC: {} en ciclo: {}", dto.getCodigoBic(), dto.getIdCiclo());
        CicloCompensacion ciclo = cicloRepository.findById(dto.getIdCiclo())
                .orElseThrow(() -> new ResourceNotFoundException("Ciclo no encontrado con ID: " + dto.getIdCiclo()));

        PosicionInstitucion entity = new PosicionInstitucion();
        entity.setCiclo(ciclo);
        entity.setCodigoBic(dto.getCodigoBic());
        entity.setTotalDebitos(dto.getTotalDebitos());
        entity.setTotalCreditos(dto.getTotalCreditos());
        entity.setNeto(dto.getNeto());

        return mapToPosicionDTO(posicionRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<PosicionDTO> listarPosicionesPorCiclo(Integer cicloId) {
        log.info("Listando posiciones para el ciclo: {}", cicloId);
        return posicionRepository.findByCicloId(cicloId).stream()
                .map(this::mapToPosicionDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public ArchivoDTO registrarArchivo(ArchivoDTO dto) {
        log.info("Registrando archivo: {} para ciclo: {}", dto.getNombre(), dto.getIdCiclo());
        CicloCompensacion ciclo = cicloRepository.findById(dto.getIdCiclo())
                .orElseThrow(() -> new ResourceNotFoundException("Ciclo no encontrado con ID: " + dto.getIdCiclo()));

        ArchivoLiquidacion entity = new ArchivoLiquidacion();
        entity.setCiclo(ciclo);
        entity.setNombre(dto.getNombre());
        entity.setCanalEnvio(dto.getCanalEnvio());
        entity.setEstado(dto.getEstado());
        entity.setFechaGeneracion(dto.getFechaGeneracion());

        return mapToArchivoDTO(archivoRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ArchivoDTO> listarArchivosPorCiclo(Integer cicloId) {
        log.info("Listando archivos para el ciclo: {}", cicloId);
        return archivoRepository.findByCicloId(cicloId).stream()
                .map(this::mapToArchivoDTO)
                .collect(Collectors.toList());
    }

    private CicloDTO mapToCicloDTO(CicloCompensacion entity) {
        return CicloDTO.builder()
                .id(entity.getId())
                .fechaCorte(entity.getFechaCorte())
                .estado(entity.getEstado())
                .descripcion(entity.getDescripcion())
                .build();
    }

    private PosicionDTO mapToPosicionDTO(PosicionInstitucion entity) {
        return PosicionDTO.builder()
                .id(entity.getId())
                .idCiclo(entity.getCiclo().getId())
                .codigoBic(entity.getCodigoBic())
                .totalDebitos(entity.getTotalDebitos())
                .totalCreditos(entity.getTotalCreditos())
                .neto(entity.getNeto())
                .build();
    }

    private ArchivoDTO mapToArchivoDTO(ArchivoLiquidacion entity) {
        return ArchivoDTO.builder()
                .id(entity.getId())
                .idCiclo(entity.getCiclo().getId())
                .nombre(entity.getNombre())
                .canalEnvio(entity.getCanalEnvio())
                .estado(entity.getEstado())
                .fechaGeneracion(entity.getFechaGeneracion())
                .build();
    }

    @Transactional
    public void acumularMovimiento(Integer cicloId, String bic, BigDecimal monto, boolean esDebito) {
        log.info("Acumulando {} para BIC: {}", monto, bic);

        // 1. Buscamos la posición existente O creamos una nueva en ceros
        PosicionInstitucion posicion = posicionRepository.findByCicloId(cicloId).stream()
                .filter(p -> p.getCodigoBic().equals(bic))
                .findFirst()
                .orElseGet(() -> {
                    // Si no existe, la creamos al vuelo
                    CicloCompensacion ciclo = cicloRepository.findById(cicloId)
                            .orElseThrow(() -> new ResourceNotFoundException("Ciclo no encontrado"));
                    PosicionInstitucion nueva = new PosicionInstitucion();
                    nueva.setCiclo(ciclo);
                    nueva.setCodigoBic(bic);
                    nueva.setTotalDebitos(BigDecimal.ZERO);
                    nueva.setTotalCreditos(BigDecimal.ZERO);
                    nueva.setNeto(BigDecimal.ZERO);
                    return nueva;
                });

        // 2. Acumulamos matemáticas (BigDecimal es inmutable, ojo)
        if (esDebito) {
            posicion.setTotalDebitos(posicion.getTotalDebitos().add(monto));
        } else {
            posicion.setTotalCreditos(posicion.getTotalCreditos().add(monto));
        }

        // 3. Recalculamos el Neto (Créditos - Débitos)
        posicion.setNeto(posicion.getTotalCreditos().subtract(posicion.getTotalDebitos()));

        posicionRepository.save(posicion);
    }

    // --- NUEVO: LÓGICA DE CIERRE DIARIO Y GENERACIÓN DE ARCHIVO ---

    @Transactional
    public ArchivoDTO realizarCierreDiario(Integer cicloId) {
        log.info(">>> INICIANDO CIERRE CONTABLE DEL CICLO: {}", cicloId);

        // 1. Obtener todas las posiciones del ciclo
        List<PosicionInstitucion> posiciones = posicionRepository.findByCicloId(cicloId);

        if (posiciones.isEmpty()) {
            throw new RuntimeException("No hay movimientos en el ciclo " + cicloId);
        }

        // 2. VERIFICACIÓN MATEMÁTICA (La prueba de fuego)
        // Sumamos todos los netos. La suma DEBE ser 0.00
        BigDecimal sumaNetos = posiciones.stream()
                .map(PosicionInstitucion::getNeto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info(">>> SUMA TOTAL DE NETOS: {}", sumaNetos);

        // Validamos con un margen de error mínimo (0.01) por temas de redondeo
        if (sumaNetos.abs().compareTo(new BigDecimal("0.01")) > 0) {
            log.error("ALERTA GRAVE: El ciclo no cuadra. Descuadre de: {}", sumaNetos);
            // OJO: En un banco real esto detiene el mundo. Aquí lanzamos excepción.
            throw new RuntimeException("ERROR DE CONCILIACIÓN: La suma de netos no es cero. Descuadre: " + sumaNetos);
        }
        
        log.info(">>> CONCILIACIÓN EXITOSA: El sistema está cuadrado (Suma = 0).");

        // 3. Generar contenido del Archivo XML (Settlement File) simulado
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<SettlementFile>\n");
        xml.append("  <CycleID>").append(cicloId).append("</CycleID>\n");
        xml.append("  <Date>").append(java.time.LocalDateTime.now()).append("</Date>\n");
        xml.append("  <TotalVolume>").append(posiciones.size()).append("</TotalVolume>\n");
        xml.append("  <Transfers>\n");

        for (PosicionInstitucion pos : posiciones) {
            xml.append("    <Tx>\n");
            xml.append("      <Bank>").append(pos.getCodigoBic()).append("</Bank>\n");
            xml.append("      <NetPosition>").append(pos.getNeto()).append("</NetPosition>\n");
            
            // Lógica visual: ¿Quién paga y quién recibe?
            if (pos.getNeto().compareTo(BigDecimal.ZERO) >= 0) {
                xml.append("      <Action>RECEIVE</Action>\n"); // Tiene saldo a favor
            } else {
                xml.append("      <Action>PAY</Action>\n");     // Debe pagar
            }
            xml.append("    </Tx>\n");
        }
        xml.append("  </Transfers>\n");
        xml.append("  <IntegrityCheck>").append(sumaNetos).append("</IntegrityCheck>\n");
        xml.append("</SettlementFile>");

        String contenidoXml = xml.toString();
        log.info("ARCHIVO XML GENERADO:\n{}", contenidoXml);

        // 4. Guardar registro del archivo (Simulamos envío al Banco Central)
        CicloCompensacion ciclo = cicloRepository.findById(cicloId)
                .orElseThrow(() -> new ResourceNotFoundException("Ciclo no encontrado"));
        
        ArchivoLiquidacion archivo = new ArchivoLiquidacion();
        archivo.setCiclo(ciclo);
        archivo.setNombre("SETTLEMENT_CICLO_" + cicloId + "_" + System.currentTimeMillis() + ".xml");
        archivo.setCanalEnvio("BCE_DIRECT_LINK");
        archivo.setEstado("ENVIADO"); // Asumimos éxito
        archivo.setFechaGeneracion(java.time.LocalDateTime.now());
        
        // 5. CERRAR EL CICLO (Ya nadie puede operar aquí)
        ciclo.setEstado("CERRADO");
        cicloRepository.save(ciclo);

        return mapToArchivoDTO(archivoRepository.save(archivo));
    }

}

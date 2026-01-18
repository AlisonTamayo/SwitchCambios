package com.bancario.compensacion.servicio;

import com.bancario.compensacion.dto.ArchivoDTO;
import com.bancario.compensacion.dto.CicloDTO;
import com.bancario.compensacion.dto.PosicionDTO;
import com.bancario.compensacion.mapper.CompensacionMapper;
import com.bancario.compensacion.modelo.*;
import com.bancario.compensacion.repositorio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensacionServicio {

    private final CicloCompensacionRepositorio cicloRepo;
    private final PosicionInstitucionRepositorio posicionRepo;
    private final ArchivoLiquidacionRepositorio archivoRepo;
    private final SeguridadServicio seguridadServicio;
    private final CompensacionMapper mapper;

    // Inyección del Scheduler de Spring para tareas dinámicas
    private final org.springframework.scheduling.TaskScheduler taskScheduler;
    private java.util.concurrent.ScheduledFuture<?> scheduledTask; // Referencia para cancelar tarea anterior

    @Transactional
    public void acumularTransaccion(Integer cicloId, String bic, BigDecimal monto, boolean esDebito) {
        PosicionInstitucion posicion = posicionRepo.findByCicloIdAndCodigoBic(cicloId, bic)
                .orElseGet(() -> crearPosicionVacia(cicloId, bic));

        if (esDebito) {
            posicion.setTotalDebitos(posicion.getTotalDebitos().add(monto));
        } else {
            posicion.setTotalCreditos(posicion.getTotalCreditos().add(monto));
        }

        posicion.recalcularNeto();
        posicionRepo.save(posicion);
    }

    @Transactional
    public void acumularEnCicloAbierto(String bic, BigDecimal monto, boolean esDebito) {
        CicloCompensacion cicloAbierto = cicloRepo.findByEstado("ABIERTO")
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No hay ciclo abierto para compensar"));

        acumularTransaccion(cicloAbierto.getId(), bic, monto, esDebito);
    }

    private PosicionInstitucion crearPosicionVacia(Integer cicloId, String bic) {
        PosicionInstitucion p = new PosicionInstitucion();
        p.setCiclo(cicloRepo.getReferenceById(cicloId));
        p.setCodigoBic(bic);
        p.setSaldoInicial(BigDecimal.ZERO);
        p.setTotalDebitos(BigDecimal.ZERO);
        p.setTotalCreditos(BigDecimal.ZERO);
        p.setNeto(BigDecimal.ZERO);
        return posicionRepo.save(p);
    }

    @Transactional
    public ArchivoDTO realizarCierreDiario(Integer cicloId, Integer minutosProximoCiclo) {
        log.info(">>> INICIANDO CIERRE DEL CICLO: {}", cicloId);

        // 1. Cancelar cualquier tarea programada pendiente para evitar cierres dobles
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
            log.info("Tarea automática anterior cancelada.");
        }

        CicloCompensacion cicloActual = cicloRepo.findById(cicloId)
                .orElseThrow(() -> new RuntimeException("Ciclo no encontrado"));

        if (!"ABIERTO".equals(cicloActual.getEstado())) {
            throw new RuntimeException("El ciclo ya está cerrado");
        }

        List<PosicionInstitucion> posiciones = posicionRepo.findByCicloId(cicloId);

        BigDecimal sumaNetos = posiciones.stream()
                .map(PosicionInstitucion::getNeto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sumaNetos.abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new RuntimeException("ALERTA: El sistema no cuadra. Suma Netos: " + sumaNetos);
        }

        String xml = generarXML(cicloActual, posiciones);
        String firma = seguridadServicio.firmarDocumento(xml);

        ArchivoLiquidacion archivo = new ArchivoLiquidacion();
        archivo.setCiclo(cicloActual);
        archivo.setNombre("LIQ_CICLO_" + cicloActual.getNumeroCiclo() + ".xml");
        archivo.setXmlContenido(xml);
        archivo.setFirmaJws(firma);
        archivo.setCanalEnvio("BCE_DIRECT_LINK");
        archivo.setEstado("ENVIADO");
        archivo.setFechaGeneracion(LocalDateTime.now(java.time.ZoneOffset.UTC));
        archivo = archivoRepo.save(archivo);

        cicloActual.setEstado("CERRADO");
        cicloActual.setFechaCierre(LocalDateTime.now(java.time.ZoneOffset.UTC));
        cicloRepo.save(cicloActual);

        // 2. Iniciar siguiente ciclo pasando los minutos definidos por el usuario
        iniciarSiguienteCiclo(cicloActual, posiciones, minutosProximoCiclo);

        return mapper.toDTO(archivo);
    }

    private void iniciarSiguienteCiclo(CicloCompensacion anterior, List<PosicionInstitucion> saldosAnteriores,
            Integer minutosDuracion) {
        CicloCompensacion nuevo = new CicloCompensacion();
        nuevo.setNumeroCiclo(anterior.getNumeroCiclo() + 1);
        nuevo.setDescripcion("Ciclo Automático (" + minutosDuracion + " min)");
        nuevo.setEstado("ABIERTO");
        nuevo.setFechaApertura(LocalDateTime.now(java.time.ZoneOffset.UTC));
        CicloCompensacion guardado = cicloRepo.save(nuevo);

        for (PosicionInstitucion posAnt : saldosAnteriores) {
            PosicionInstitucion posNueva = new PosicionInstitucion();
            posNueva.setCiclo(guardado);
            posNueva.setCodigoBic(posAnt.getCodigoBic());
            posNueva.setSaldoInicial(BigDecimal.ZERO); // Podria arrastrar saldos si fuera multivia
            posNueva.setTotalDebitos(BigDecimal.ZERO);
            posNueva.setTotalCreditos(BigDecimal.ZERO);
            posNueva.recalcularNeto();
            posicionRepo.save(posNueva);
        }

        // 3. Programar el cierre automático de ESTE nuevo ciclo
        programarCierreAutomatico(guardado.getId(), minutosDuracion != null ? minutosDuracion : 10);

        log.info(">>> CICLO {} INICIADO. Cierre programado en {} minutos.", nuevo.getNumeroCiclo(), minutosDuracion);
    }

    public void programarCierreAutomatico(Integer cicloId, int minutos) {
        // Ejecutar cierre automático usando el mismo sevicio (se invocará a sí mismo)
        // NOTA: En ambiente real, esto debe manejar transacciones con cuidado.
        // Aquí simulamos la llamada.
        Runnable tareaCierre = () -> {
            try {
                log.info(">>> EJECUTANDO CIERRE AUTOMÁTICO CICLO {}", cicloId);
                // Por defecto, si es automático, el siguiente ciclo también dura 10 min
                realizarCierreDiario(cicloId, 10);
            } catch (Exception e) {
                log.error("Error en cierre automático: {}", e.getMessage());
            }
        };

        // Programar fecha
        java.time.Instant fechaEjecucion = java.time.Instant.now().plus(java.time.Duration.ofMinutes(minutos));
        this.scheduledTask = taskScheduler.schedule(tareaCierre, fechaEjecucion);
    }

    // --- GENERACIÓN DE PDF (OpenPDF) ---
    public byte[] generarReportePDF(Integer cicloId) {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            com.lowagie.text.Document document = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(document, out);

            document.open();

            // Título
            com.lowagie.text.Font titleFont = com.lowagie.text.FontFactory
                    .getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 18);
            com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("Reporte de Compensación (Switch V3)",
                    titleFont);
            title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            document.add(title);
            document.add(new com.lowagie.text.Paragraph(" ")); // Espacio

            CicloCompensacion ciclo = cicloRepo.findById(cicloId).orElseThrow();
            document.add(new com.lowagie.text.Paragraph("Ciclo: " + ciclo.getNumeroCiclo()));
            document.add(new com.lowagie.text.Paragraph("Estado: " + ciclo.getEstado()));
            document.add(new com.lowagie.text.Paragraph("Fecha Apertura: " + ciclo.getFechaApertura()));
            if (ciclo.getFechaCierre() != null)
                document.add(new com.lowagie.text.Paragraph("Fecha Cierre: " + ciclo.getFechaCierre()));

            document.add(new com.lowagie.text.Paragraph(" "));

            // Tabla
            com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(4);
            table.setWidthPercentage(100);
            table.addCell("Banco (BIC)");
            table.addCell("Débitos");
            table.addCell("Créditos");
            table.addCell("Posición Neta");

            List<PosicionInstitucion> posiciones = posicionRepo.findByCicloId(cicloId);
            for (PosicionInstitucion p : posiciones) {
                table.addCell(p.getCodigoBic());
                table.addCell(p.getTotalDebitos().toString());
                table.addCell(p.getTotalCreditos().toString());

                com.lowagie.text.pdf.PdfPCell cellNeto = new com.lowagie.text.pdf.PdfPCell(
                        new com.lowagie.text.Phrase(p.getNeto().toString()));
                if (p.getNeto().signum() < 0)
                    cellNeto.setBackgroundColor(java.awt.Color.PINK); // Rojo para deudores
                else
                    cellNeto.setBackgroundColor(java.awt.Color.CYAN); // Verde/Azul para acreedores

                table.addCell(cellNeto);
            }
            document.add(table);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF: " + e.getMessage());
        }
    }

    private String generarXML(CicloCompensacion ciclo, List<PosicionInstitucion> posiciones) {
        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<SettlementFile xmlns=\"http://bancario.switch/settlement/v1\">\n");
        sb.append("  <Header>\n");
        sb.append("    <MsgId>MSG-LIQ-").append(System.currentTimeMillis()).append("</MsgId>\n");
        sb.append("    <CycleId>").append(ciclo.getNumeroCiclo()).append("</CycleId>\n");
        sb.append("    <CreationDate>").append(LocalDateTime.now(java.time.ZoneOffset.UTC)).append("</CreationDate>\n");
        sb.append("    <TotalRecords>").append(posiciones.size()).append("</TotalRecords>\n");
        sb.append("  </Header>\n");
        sb.append("  <Transactions>\n");
        for (PosicionInstitucion p : posiciones) {
            sb.append("    <Tx>\n");
            sb.append("      <BankBIC>").append(p.getCodigoBic()).append("</BankBIC>\n");
            sb.append("      <NetPosition currency=\"USD\">").append(p.getNeto()).append("</NetPosition>\n");
            sb.append("      <Action>").append(p.getNeto().signum() >= 0 ? "RECEIVE" : "PAY").append("</Action>\n");
            sb.append("    </Tx>\n");
        }
        sb.append("  </Transactions>\n");
        sb.append("</SettlementFile>");

        return sb.toString();
    }

    public List<CicloDTO> listarCiclos() {
        List<CicloCompensacion> ciclos = cicloRepo.findAll();
        if (ciclos.isEmpty()) {
            CicloCompensacion primerCiclo = new CicloCompensacion();
            primerCiclo.setNumeroCiclo(1);
            primerCiclo.setDescripcion("Ciclo Inicial");
            primerCiclo.setEstado("ABIERTO");
            primerCiclo.setFechaApertura(LocalDateTime.now(java.time.ZoneOffset.UTC));
            CicloCompensacion guardado = cicloRepo.save(primerCiclo);

            // Iniciar timer por defecto de 10 min para el primer ciclo
            programarCierreAutomatico(guardado.getId(), 10);

            ciclos.add(guardado);
        }
        return ciclos.stream().map(mapper::toDTO).toList();
    }

    public List<PosicionDTO> obtenerPosicionesCiclo(Integer cicloId) {
        List<PosicionInstitucion> posiciones = posicionRepo.findByCicloId(cicloId);
        return mapper.toPosicionList(posiciones);
    }
}

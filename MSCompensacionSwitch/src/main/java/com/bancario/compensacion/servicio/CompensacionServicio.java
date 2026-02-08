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
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensacionServicio {

    private final CicloCompensacionRepositorio cicloRepo;
    private final PosicionInstitucionRepositorio posicionRepo;
    private final ArchivoLiquidacionRepositorio archivoRepo;
    // private final SeguridadServicio seguridadServicio; // REMOVED: JWS signing is
    // no longer handled here
    private final DetalleCompensacionRepositorio detalleRepo;
    private final CompensacionMapper mapper;

    private final org.springframework.scheduling.TaskScheduler taskScheduler;
    private java.util.concurrent.ScheduledFuture<?> scheduledTask;

    @Transactional
    public void registrarOperacion(com.bancario.compensacion.dto.RegistroOperacionDTO req) {
        CicloCompensacion cicloAbierto = cicloRepo.findByEstado("ABIERTO")
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No hay ciclo abierto para compensar"));

        DetalleCompensacion detalle = new DetalleCompensacion();
        detalle.setIdInstruccion(req.getIdInstruccion());
        detalle.setIdInstruccionOriginal(req.getIdInstruccionOriginal());
        detalle.setCiclo(cicloAbierto);
        detalle.setTipoOperacion(req.getTipoOperacion());
        detalle.setBicEmisor(req.getBicEmisor());
        detalle.setBicReceptor(req.getBicReceptor());
        detalle.setMonto(req.getMonto());
        detalle.setCodigoReferencia(req.getCodigoReferencia());
        detalle.setEstadoLiquidacion("INCLUIDO");
        detalleRepo.save(detalle);

        // NOTE: Real-time accumulation is kept for immediate visibility,
        // but final settlement will be recalculated from details at closing.
        if ("REVERSO".equalsIgnoreCase(req.getTipoOperacion())) {
            // REVERSO logic: Credit Emisor (Refund), Debit Receptor (Take back)
            acumularTransaccion(cicloAbierto.getId(), req.getBicEmisor(), req.getMonto(), false); // Credit
            acumularTransaccion(cicloAbierto.getId(), req.getBicReceptor(), req.getMonto(), true); // Debit
        } else {
            // PAGO logic: Debit Emisor, Credit Receptor
            acumularTransaccion(cicloAbierto.getId(), req.getBicEmisor(), req.getMonto(), true);
            acumularTransaccion(cicloAbierto.getId(), req.getBicReceptor(), req.getMonto(), false);
        }
    }

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

        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
            log.info("Tarea automática anterior cancelada.");
        }

        CicloCompensacion cicloActual = cicloRepo.findById(cicloId)
                .orElseThrow(() -> new RuntimeException("Ciclo no encontrado"));

        if (!"ABIERTO".equals(cicloActual.getEstado())) {
            throw new RuntimeException("El ciclo ya está cerrado");
        }

        // --- ALGORITMO DE NETEO / CLEARING ---
        // Recalculate positions based on details to ensure accuracy
        recalcularPosicionesDesdeDetalles(cicloActual);
        // -------------------------------------

        List<PosicionInstitucion> posiciones = posicionRepo.findByCicloId(cicloId);

        BigDecimal sumaNetos = posiciones.stream()
                .map(PosicionInstitucion::getNeto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sumaNetos.abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new RuntimeException("ALERTA: El sistema no cuadra. Suma Netos: " + sumaNetos);
        }

        String xml = generarXML(cicloActual, posiciones);
        // String firma = seguridadServicio.firmarDocumento(xml); // REMOVED

        ArchivoLiquidacion archivo = new ArchivoLiquidacion();
        archivo.setCiclo(cicloActual);
        archivo.setNombre("LIQ_CICLO_" + cicloActual.getNumeroCiclo() + ".xml");
        archivo.setXmlContenido(xml);

        archivo.setCanalEnvio("BCE_DIRECT_LINK");
        archivo.setEstado("ENVIADO");
        archivo.setFechaGeneracion(LocalDateTime.now(java.time.ZoneOffset.UTC));
        archivo = archivoRepo.save(archivo);

        cicloActual.setEstado("CERRADO");
        cicloActual.setFechaCierre(LocalDateTime.now(java.time.ZoneOffset.UTC));
        cicloRepo.save(cicloActual);

        iniciarSiguienteCiclo(cicloActual, posiciones, minutosProximoCiclo);

        return mapper.toDTO(archivo);
    }

    /**
     * Re-processes all details for the cycle to ensure the final positions are
     * correct.
     * Use this for the "Clearing" step.
     */
    private void recalcularPosicionesDesdeDetalles(CicloCompensacion ciclo) {
        log.info("Ejecutando algoritmo de neteo para ciclo {}", ciclo.getId());

        // 1. Reset all positions for the cycle
        List<PosicionInstitucion> posiciones = posicionRepo.findByCicloId(ciclo.getId());
        for (PosicionInstitucion p : posiciones) {
            p.setTotalDebitos(BigDecimal.ZERO);
            p.setTotalCreditos(BigDecimal.ZERO);
            p.setNeto(BigDecimal.ZERO); // derived, but good to reset
        }
        Map<String, PosicionInstitucion> mapaPosiciones = posiciones.stream()
                .collect(Collectors.toMap(PosicionInstitucion::getCodigoBic, p -> p));

        // 2. Fetch all details
        List<DetalleCompensacion> detalles = detalleRepo.findByCicloId(ciclo.getId()); // Assuming this method exists or
                                                                                       // similar

        // 3. Process each detail
        for (DetalleCompensacion d : detalles) {
            if ("EXCLUIDO".equalsIgnoreCase(d.getEstadoLiquidacion()))
                continue;

            PosicionInstitucion posEmisor = mapaPosiciones.computeIfAbsent(d.getBicEmisor(),
                    k -> crearPosicionVacia(ciclo.getId(), k));
            PosicionInstitucion posReceptor = mapaPosiciones.computeIfAbsent(d.getBicReceptor(),
                    k -> crearPosicionVacia(ciclo.getId(), k));

            if ("REVERSO".equalsIgnoreCase(d.getTipoOperacion())) {
                // REVERSO: Emisor receives back (Credit), Receptor pays back (Debit)
                posEmisor.setTotalCreditos(posEmisor.getTotalCreditos().add(d.getMonto()));
                posReceptor.setTotalDebitos(posReceptor.getTotalDebitos().add(d.getMonto()));
            } else {
                // PAGO: Emisor pays (Debit), Receptor receives (Credit)
                posEmisor.setTotalDebitos(posEmisor.getTotalDebitos().add(d.getMonto()));
                posReceptor.setTotalCreditos(posReceptor.getTotalCreditos().add(d.getMonto()));
            }
        }

        // 4. Save and Recalculate Net
        for (PosicionInstitucion p : mapaPosiciones.values()) {
            p.recalcularNeto();
            posicionRepo.save(p);
        }
        log.info("Neteo completado. Procesados {} detalles.", detalles.size());
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
            posNueva.setSaldoInicial(BigDecimal.ZERO);
            posNueva.setTotalDebitos(BigDecimal.ZERO);
            posNueva.setTotalCreditos(BigDecimal.ZERO);
            posNueva.recalcularNeto();
            posicionRepo.save(posNueva);
        }

        programarCierreAutomatico(guardado.getId(), minutosDuracion != null ? minutosDuracion : 10);

        log.info(">>> CICLO {} INICIADO. Cierre programado en {} minutos.", nuevo.getNumeroCiclo(), minutosDuracion);
    }

    public void programarCierreAutomatico(Integer cicloId, int minutos) {

        Runnable tareaCierre = () -> {
            try {
                log.info(">>> EJECUTANDO CIERRE AUTOMÁTICO CICLO {}", cicloId);

                realizarCierreDiario(cicloId, 10);
            } catch (Exception e) {
                log.error("Error en cierre automático: {}", e.getMessage());
            }
        };

        java.time.Instant fechaEjecucion = java.time.Instant.now().plus(java.time.Duration.ofMinutes(minutos));
        this.scheduledTask = taskScheduler.schedule(tareaCierre, fechaEjecucion);
    }

    public byte[] generarReportePDF(Integer cicloId) {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            com.lowagie.text.Document document = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(document, out);

            document.open();

            com.lowagie.text.Font titleFont = com.lowagie.text.FontFactory
                    .getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 18);
            com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("Reporte de Compensación (Switch V3)",
                    titleFont);
            title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            document.add(title);
            document.add(new com.lowagie.text.Paragraph(" "));

            CicloCompensacion ciclo = cicloRepo.findById(cicloId).orElseThrow();
            document.add(new com.lowagie.text.Paragraph("Ciclo: " + ciclo.getNumeroCiclo()));
            document.add(new com.lowagie.text.Paragraph("Estado: " + ciclo.getEstado()));
            document.add(new com.lowagie.text.Paragraph("Fecha Apertura: " + ciclo.getFechaApertura()));
            if (ciclo.getFechaCierre() != null)
                document.add(new com.lowagie.text.Paragraph("Fecha Cierre: " + ciclo.getFechaCierre()));

            document.add(new com.lowagie.text.Paragraph(" "));

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
                    cellNeto.setBackgroundColor(java.awt.Color.PINK);
                else
                    cellNeto.setBackgroundColor(java.awt.Color.CYAN);

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

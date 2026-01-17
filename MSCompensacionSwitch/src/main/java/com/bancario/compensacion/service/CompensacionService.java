package com.bancario.compensacion.service;

import com.bancario.compensacion.model.*;
import com.bancario.compensacion.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CompensacionService {

    @Autowired
    private CicloCompensacionRepository cicloRepo;
    @Autowired
    private PosicionInstitucionRepository posicionRepo;
    @Autowired
    private ArchivoLiquidacionRepository archivoRepo;
    @Autowired
    private SeguridadService seguridadService;

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
    public ArchivoLiquidacion realizarCierreDiario(Integer cicloId) {
        System.out.println(">>> INICIANDO CIERRE DEL CICLO: " + cicloId);

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

        String firma = seguridadService.firmarDocumento(xml);

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

        iniciarSiguienteCiclo(cicloActual, posiciones);

        return archivo;
    }

    private void iniciarSiguienteCiclo(CicloCompensacion anterior, List<PosicionInstitucion> saldosAnteriores) {
        CicloCompensacion nuevo = new CicloCompensacion();
        nuevo.setNumeroCiclo(anterior.getNumeroCiclo() + 1);
        nuevo.setDescripcion("Ciclo Automático");
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
        System.out.println(">>> CICLO " + nuevo.getNumeroCiclo() + " INICIADO CORRECTAMENTE.");
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

    public List<CicloCompensacion> listarCiclos() {
        List<CicloCompensacion> ciclos = cicloRepo.findAll();
        if (ciclos.isEmpty()) {
            CicloCompensacion primerCiclo = new CicloCompensacion();
            primerCiclo.setNumeroCiclo(1);
            primerCiclo.setDescripcion("Ciclo Inicial");
            primerCiclo.setEstado("ABIERTO");
            primerCiclo.setFechaApertura(LocalDateTime.now(java.time.ZoneOffset.UTC));
            cicloRepo.save(primerCiclo);
            ciclos.add(primerCiclo);
        }
        return ciclos;
    }

    public List<PosicionInstitucion> obtenerPosicionesCiclo(Integer cicloId) {
        return posicionRepo.findByCicloId(cicloId);
    }
}
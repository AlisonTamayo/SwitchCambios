package com.bancario.compensacion.servicio;

import com.bancario.compensacion.dto.ArchivoDTO;
import com.bancario.compensacion.mapper.CompensacionMapper;
import com.bancario.compensacion.modelo.*;
import com.bancario.compensacion.repositorio.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompensacionServicioTest {

    @Mock
    private CicloCompensacionRepositorio cicloRepo;
    @Mock
    private PosicionInstitucionRepositorio posicionRepo;
    @Mock
    private ArchivoLiquidacionRepositorio archivoRepo;
    // SeguridadServicio is no longer used
    @Mock
    private DetalleCompensacionRepositorio detalleRepo;
    @Mock
    private CompensacionMapper mapper;
    @Mock
    private TaskScheduler taskScheduler;

    @InjectMocks
    private CompensacionServicio servicio;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRealizarCierreDiario_NettingLogic() {
        // Arrange
        Integer cicloId = 1;
        CicloCompensacion ciclo = new CicloCompensacion();
        ciclo.setId(cicloId);
        ciclo.setNumeroCiclo(100);
        ciclo.setEstado("ABIERTO");

        when(cicloRepo.findById(cicloId)).thenReturn(Optional.of(ciclo));
        when(cicloRepo.save(any(CicloCompensacion.class))).thenAnswer(i -> i.getArguments()[0]);

        // Mock Positions (initial state - will be reset)
        PosicionInstitucion posBankA = new PosicionInstitucion();
        posBankA.setCodigoBic("BANKA");
        posBankA.setNeto(BigDecimal.ZERO);
        posBankA.setTotalDebitos(BigDecimal.ZERO);
        posBankA.setTotalCreditos(BigDecimal.ZERO);

        PosicionInstitucion posBankB = new PosicionInstitucion();
        posBankB.setCodigoBic("BANKB");
        posBankB.setNeto(BigDecimal.ZERO);
        posBankB.setTotalDebitos(BigDecimal.ZERO);
        posBankB.setTotalCreditos(BigDecimal.ZERO);

        List<PosicionInstitucion> posiciones = Arrays.asList(posBankA, posBankB);
        when(posicionRepo.findByCicloId(cicloId)).thenReturn(posiciones);

        // Mock Details
        // 1. PAGO: A pays B 100. -> A Debit 100, B Credit 100.
        DetalleCompensacion det1 = new DetalleCompensacion();
        det1.setTipoOperacion("PAGO");
        det1.setBicEmisor("BANKA");
        det1.setBicReceptor("BANKB");
        det1.setMonto(new BigDecimal("100.00"));
        det1.setEstadoLiquidacion("INCLUIDO");

        // 2. REVERSO: A (original sender) gets refund from B?
        // Logic: "REVERSO" means Credit Emisor, Debit Receptor.
        // If "Emisor" in detail is still BANKA (the original sender), then:
        // BANKA Credit 20 (refund), BANKB Debit 20 (pay back).
        DetalleCompensacion det2 = new DetalleCompensacion();
        det2.setTipoOperacion("REVERSO");
        det2.setBicEmisor("BANKA");
        det2.setBicReceptor("BANKB");
        det2.setMonto(new BigDecimal("20.00"));
        det2.setEstadoLiquidacion("INCLUIDO");

        when(detalleRepo.findByCicloId(cicloId)).thenReturn(Arrays.asList(det1, det2));

        when(archivoRepo.save(any(ArchivoLiquidacion.class))).thenAnswer(i -> i.getArguments()[0]);
        when(mapper.toDTO(any(ArchivoLiquidacion.class))).thenReturn(ArchivoDTO.builder().build());

        // Act
        servicio.realizarCierreDiario(cicloId, 10);

        // Assert Netting Logic
        // BANKA:
        // PAGO (Debit 100) -> -100
        // REVERSO (Credit 20) -> +20
        // Net: -80
        // Expected TotalDebitos = 100, TotalCreditos = 20
        assertEquals(new BigDecimal("100.00"), posBankA.getTotalDebitos());
        assertEquals(new BigDecimal("20.00"), posBankA.getTotalCreditos());
        assertEquals(new BigDecimal("-80.00"), posBankA.getNeto());

        // BANKB:
        // PAGO (Credit 100) -> +100
        // REVERSO (Debit 20) -> -20
        // Net: +80
        // Expected TotalDebitos = 20, TotalCreditos = 100
        assertEquals(new BigDecimal("20.00"), posBankB.getTotalDebitos());
        assertEquals(new BigDecimal("100.00"), posBankB.getTotalCreditos());
        assertEquals(new BigDecimal("80.00"), posBankB.getNeto());

        // Assert XML Signature Removal
        verify(archivoRepo).save(argThat(
                archivo -> archivo.getXmlContenido().contains("<NetPosition currency=\"USD\">-80.00</NetPosition>") &&
                        archivo.getXmlContenido().contains("<NetPosition currency=\"USD\">80.00</NetPosition>")));
    }
}

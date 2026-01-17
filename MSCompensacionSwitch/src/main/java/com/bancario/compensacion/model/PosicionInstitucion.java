package com.bancario.compensacion.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "posicioninstitucion")
@Getter
@Setter
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "ciclo" })
public class PosicionInstitucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_ciclo", insertable = false, updatable = false)
    private Integer idCiclo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ciclo")
    private CicloCompensacion ciclo;

    @Column(name = "codigo_bic", length = 20)
    private String codigoBic;

    @Column(name = "saldo_inicial", precision = 20, scale = 2)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Column(name = "total_debitos", precision = 20, scale = 2)
    private BigDecimal totalDebitos = BigDecimal.ZERO;

    @Column(name = "total_creditos", precision = 20, scale = 2)
    private BigDecimal totalCreditos = BigDecimal.ZERO;

    @Column(name = "neto", precision = 20, scale = 2)
    private BigDecimal neto = BigDecimal.ZERO;

    public void recalcularNeto() {

        this.neto = this.saldoInicial.add(this.totalCreditos).subtract(this.totalDebitos);
    }
}
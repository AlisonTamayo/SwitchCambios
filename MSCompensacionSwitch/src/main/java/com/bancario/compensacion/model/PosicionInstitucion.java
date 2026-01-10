package com.bancario.compensacion.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "PosicionInstitucion")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PosicionInstitucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @NotNull(message = "El ciclo de compensación es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idCiclo", nullable = false)
    private CicloCompensacion ciclo;

    @NotBlank(message = "El código BIC es obligatorio")
    @Size(max = 20)
    @Column(name = "codigoBic", nullable = false, length = 20)
    private String codigoBic;

    @Column(name = "totalDebitos", precision = 18, scale = 2)
    private BigDecimal totalDebitos;

    @Column(name = "totalCreditos", precision = 18, scale = 2)
    private BigDecimal totalCreditos;

    @Column(name = "neto", precision = 18, scale = 2)
    private BigDecimal neto;

    public PosicionInstitucion() {
    }

    public PosicionInstitucion(Integer id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PosicionInstitucion that = (PosicionInstitucion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PosicionInstitucion{" +
                "id=" + id +
                ", idCiclo=" + (ciclo != null ? ciclo.getId() : null) +
                ", codigoBic='" + codigoBic + '\'' +
                ", totalDebitos=" + totalDebitos +
                ", totalCreditos=" + totalCreditos +
                ", neto=" + neto +
                '}';
    }
}

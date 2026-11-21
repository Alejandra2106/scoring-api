package com.crediticio.evaluations.application.dto;

import com.crediticio.shared.util.EnteroLargoEstrictoDeserializer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

public class CalcularScoreRequest {

    /** US 5.1.1 escenario 2: un identificador inválido (no positivo) se rechaza con 400 sin registrar nada. */
    @JsonDeserialize(using = EnteroLargoEstrictoDeserializer.class)
    @Positive(message = "idSolicitante debe ser un entero positivo")
    private Long idSolicitante;

    @Pattern(regexp = "\\d{6,15}", message = "numeroDocumento debe contener entre 6 y 15 dígitos")
    private String numeroDocumento;

    public CalcularScoreRequest() {
    }

    public CalcularScoreRequest(Long idSolicitante, String numeroDocumento) {
        this.idSolicitante = idSolicitante;
        this.numeroDocumento = numeroDocumento;
    }

    @AssertTrue(message = "Debe proporcionar exactamente uno de idSolicitante o numeroDocumento")
    private boolean isIdentificacionValida() {
        return (idSolicitante != null) ^ (numeroDocumento != null);
    }

    public Long getIdSolicitante() {
        return idSolicitante;
    }

    public void setIdSolicitante(Long idSolicitante) {
        this.idSolicitante = idSolicitante;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }
}

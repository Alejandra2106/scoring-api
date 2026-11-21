package com.crediticio.evaluations.domain;

/** US 5.1.1: una evaluación solo se registra con el usuario evaluador autenticado; nunca con uno inventado. */
public class EvaluacionSinEvaluadorException extends RuntimeException {

    public EvaluacionSinEvaluadorException() {
        super("El registro de una evaluación requiere un usuario autenticado; la evaluación no se registró");
    }
}

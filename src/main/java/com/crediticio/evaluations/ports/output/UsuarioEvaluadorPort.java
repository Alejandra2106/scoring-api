package com.crediticio.evaluations.ports.output;

import java.util.Optional;
import java.util.UUID;

/**
 * US 5.1.1: identidad del usuario autenticado que ejecuta la evaluación (claim sub del JWT validado).
 * Vacío cuando no hay un usuario autenticado; nunca devuelve un identificador predeterminado.
 */
public interface UsuarioEvaluadorPort {

    Optional<UUID> idUsuarioEvaluador();
}

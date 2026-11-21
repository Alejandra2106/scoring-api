package com.crediticio.evaluations.infrastructure.output;

import com.crediticio.evaluations.ports.output.UsuarioEvaluadorPort;
import com.crediticio.scoring.ports.output.ActorActualPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * US 5.1.1: reutiliza {@link ActorActualPort} de {@code scoring} (sub del JWT ya validado por la cadena
 * de seguridad, HU10) sin duplicar la lectura del contexto de seguridad, y lo traduce al contrato propio
 * de {@code evaluations} (mismo patrón que {@link SolicitanteConsultaAdapter}).
 */
@Component
public class UsuarioEvaluadorAdapter implements UsuarioEvaluadorPort {

    private final ActorActualPort actorActualPort;

    public UsuarioEvaluadorAdapter(ActorActualPort actorActualPort) {
        this.actorActualPort = actorActualPort;
    }

    @Override
    public Optional<UUID> idUsuarioEvaluador() {
        return actorActualPort.idUsuarioAutenticado();
    }
}

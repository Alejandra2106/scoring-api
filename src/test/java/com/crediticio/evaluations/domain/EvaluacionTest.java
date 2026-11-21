package com.crediticio.evaluations.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluacionTest {

    private static final ClasificacionEvaluacion CLASIFICACION = new ClasificacionEvaluacion("MEDIO", 3L, 2);
    private static final UUID EVALUADOR = UUID.fromString("7b1d1c2e-3a4f-4e5d-8c9b-0a1b2c3d4e5f");

    @Test
    void debeCrearEvaluacionNuevaConEstadoInicialSinIdNiFecha() {
        Evaluacion evaluacion = Evaluacion.nueva(1L, EVALUADOR, List.of(), CLASIFICACION);

        assertThat(evaluacion.getIdEvaluacion()).isNull();
        assertThat(evaluacion.getIdSolicitante()).isEqualTo(1L);
        assertThat(evaluacion.getFechaEvaluacion()).isNull();
        assertThat(evaluacion.getDetalles()).isEmpty();
        assertThat(evaluacion.getClasificacion()).isEqualTo(CLASIFICACION);
    }

    @Test
    void scoreTotalDebeSerCeroCuandoNoHayDetalles() {
        Evaluacion evaluacion = Evaluacion.nueva(1L, EVALUADOR, List.of(), CLASIFICACION);

        assertThat(evaluacion.getScoreTotal()).isZero();
    }

    @Test
    void scoreTotalDebeSumarUnicamenteLosPuntajesObtenidosDeLosDetallesCumplidos() {
        List<DetalleEvaluacion> detalles = List.of(
                DetalleEvaluacion.nuevo(1L, ">=", "3000000", 20, true, 20),
                DetalleEvaluacion.nuevo(2L, "<", "5", 10, false, 0),
                DetalleEvaluacion.nuevo(3L, "=", "BUENO", 30, true, 30));

        Evaluacion evaluacion = Evaluacion.nueva(1L, EVALUADOR, detalles, CLASIFICACION);

        assertThat(evaluacion.getScoreTotal()).isEqualTo(50);
        assertThat(Evaluacion.sumarPuntajes(detalles)).isEqualTo(50);
    }

    @Test
    void scoreTotalDebeAdmitirPuntajesNegativos() {
        List<DetalleEvaluacion> detalles = List.of(
                DetalleEvaluacion.nuevo(1L, "=", "MALO", -30, true, -30),
                DetalleEvaluacion.nuevo(2L, ">=", "3000000", 20, true, 20));

        Evaluacion evaluacion = Evaluacion.nueva(1L, EVALUADOR, detalles, CLASIFICACION);

        assertThat(evaluacion.getScoreTotal()).isEqualTo(-10);
    }

    @Test
    void debeRechazarIdSolicitanteNulo() {
        assertThatThrownBy(() -> Evaluacion.nueva(null, EVALUADOR, List.of(), CLASIFICACION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void unaEvaluacionNuevaDebeEstarClasificada() {
        assertThatThrownBy(() -> Evaluacion.nueva(1L, EVALUADOR, List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clasificacion");
    }

    @Test
    void reconstruirDebePreservarTodosLosCampos() {
        LocalDateTime fecha = LocalDateTime.of(2026, 1, 1, 10, 0);
        List<DetalleEvaluacion> detalles = List.of(DetalleEvaluacion.reconstruir(1L, 1L, "=", "10", 20, true, 20));

        Evaluacion evaluacion = Evaluacion.reconstruir(5L, 1L, fecha, 20, CLASIFICACION, detalles);

        assertThat(evaluacion.getIdEvaluacion()).isEqualTo(5L);
        assertThat(evaluacion.getFechaEvaluacion()).isEqualTo(fecha);
        assertThat(evaluacion.getDetalles()).hasSize(1);
        assertThat(evaluacion.getScoreTotal()).isEqualTo(20);
        assertThat(evaluacion.getClasificacion()).isEqualTo(CLASIFICACION);
    }

    @Test
    void unaEvaluacionAnteriorAHu12DebeReconstruirseSinClasificacion() {
        List<DetalleEvaluacion> detalles = List.of(DetalleEvaluacion.reconstruir(1L, 1L, "=", "10", 20, true, 20));

        Evaluacion evaluacion = Evaluacion.reconstruir(5L, 1L, LocalDateTime.now(), null, null, detalles);

        assertThat(evaluacion.getClasificacion()).isNull();
        assertThat(evaluacion.getScoreTotal()).isEqualTo(20);
    }

    @Test
    void reconstruirDebeRechazarUnPuntajeAlmacenadoDistintoDeLaSumaDeLosDetalles() {
        List<DetalleEvaluacion> detalles = List.of(DetalleEvaluacion.reconstruir(1L, 1L, "=", "10", 20, true, 20));

        assertThatThrownBy(() -> Evaluacion.reconstruir(5L, 1L, LocalDateTime.now(), 25, CLASIFICACION, detalles))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no coincide");
    }

    @Test
    void reconstruirDebeRechazarDatosDeClasificacionIncompletos() {
        assertThatThrownBy(() -> Evaluacion.reconstruir(5L, 1L, LocalDateTime.now(), 0, null, List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Evaluacion.reconstruir(5L, 1L, LocalDateTime.now(), null, CLASIFICACION, List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- HU14: estado de la saga ----

    @Test
    void unaEvaluacionNuevaDebeNacerPendiente() {
        assertThat(Evaluacion.nueva(1L, EVALUADOR, List.of(), CLASIFICACION).getEstado())
                .isEqualTo(EstadoEvaluacion.PENDIENTE);
    }

    @Test
    void soloUnaEvaluacionPendientePuedeCompletarse() {
        Evaluacion pendiente = Evaluacion.reconstruir(5L, 1L, LocalDateTime.now(), 0, CLASIFICACION,
                EstadoEvaluacion.PENDIENTE, List.of());

        Evaluacion completada = pendiente.completada();

        assertThat(completada.getEstado()).isEqualTo(EstadoEvaluacion.COMPLETADA);
        assertThat(completada.getIdEvaluacion()).isEqualTo(5L);
        assertThat(completada.getClasificacion()).isEqualTo(CLASIFICACION);
        assertThatThrownBy(completada::completada).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unaEvaluacionAnteriorALaSagaNoPuedeIncorporarseAElla() {
        Evaluacion historica = Evaluacion.reconstruir(5L, 1L, LocalDateTime.now(), 0, CLASIFICACION, List.of());

        assertThat(historica.getEstado()).isNull();
        assertThatThrownBy(historica::completada).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unaEvaluacionDeLaSagaDebeEstarClasificada() {
        assertThatThrownBy(() -> Evaluacion.reconstruir(5L, 1L, LocalDateTime.now(), null, null,
                EstadoEvaluacion.PENDIENTE, List.of())).isInstanceOf(IllegalStateException.class);
    }

    // ---- US 5.1.1: usuario evaluador del historial ----

    @Test
    void unaEvaluacionNuevaDebeRegistrarSuUsuarioEvaluador() {
        Evaluacion evaluacion = Evaluacion.nueva(1L, EVALUADOR, List.of(), CLASIFICACION);

        assertThat(evaluacion.getUsuarioEvaluador()).isEqualTo(EVALUADOR);
    }

    @Test
    void unaEvaluacionNuevaSinUsuarioEvaluadorDebeRechazarse() {
        assertThatThrownBy(() -> Evaluacion.nueva(1L, null, List.of(), CLASIFICACION))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("usuarioEvaluador");
    }

    @Test
    void elUsuarioEvaluadorDebeConservarseAlReconstruirYAlCompletar() {
        Evaluacion pendiente = Evaluacion.reconstruir(5L, 1L, LocalDateTime.now(), 0, CLASIFICACION,
                EstadoEvaluacion.PENDIENTE, EVALUADOR, List.of());

        assertThat(pendiente.getUsuarioEvaluador()).isEqualTo(EVALUADOR);
        assertThat(pendiente.completada().getUsuarioEvaluador()).isEqualTo(EVALUADOR);
    }

    @Test
    void unaEvaluacionAnteriorAUs511NoTieneUsuarioEvaluador() {
        Evaluacion historica = Evaluacion.reconstruir(5L, 1L, LocalDateTime.now(), 0, CLASIFICACION,
                EstadoEvaluacion.PENDIENTE, List.of());

        assertThat(historica.getUsuarioEvaluador()).isNull();
    }
}

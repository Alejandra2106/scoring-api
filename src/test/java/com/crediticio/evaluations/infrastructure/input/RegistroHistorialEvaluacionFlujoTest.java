package com.crediticio.evaluations.infrastructure.input;

import com.crediticio.applicants.ports.output.RegistrarAltaSolicitanteEnProyeccionPort;
import com.crediticio.config.JwtConfig;
import com.crediticio.evaluations.ports.output.TrabajoRecomendacionPort;
import com.crediticio.support.ClavesRsaPrueba;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US 5.1.1 de punta a punta sobre H2 en memoria (perfil test, sin PostgreSQL ni Flyway): el cálculo registra
 * automáticamente la evaluación en el historial con solicitante, score, nivel de riesgo, fecha y usuario
 * evaluador del JWT; una petición sin identificador válido se rechaza con 400 sin persistir nada; y un fallo
 * posterior a la inserción revierte la transacción completa. El trigger de V12 solo existe en PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistroHistorialEvaluacionFlujoTest {

    private static final String RUTA_EVALUACIONES = "/api/v1/evaluaciones";
    private static final String POLITICA = """
            {"rangos": [
              {"categoria": "BAJO", "limiteInferior": -100, "limiteSuperior": 29},
              {"categoria": "MEDIO", "limiteInferior": 30, "limiteSuperior": 79},
              {"categoria": "ALTO", "limiteInferior": 80, "limiteSuperior": 130}
            ]}""";

    @DynamicPropertySource
    static void configurarClavePublica(DynamicPropertyRegistry registro) {
        registro.add("scoring.jwt.clave-publica", () -> {
            Path claves;
            try {
                claves = Files.createTempDirectory("us511-flujo-claves");
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            claves.toFile().deleteOnExit();
            Path clave = ClavesRsaPrueba.escribirClavePublica(ClavesRsaPrueba.generar(), claves);
            clave.toFile().deleteOnExit();
            return "file:" + clave;
        });
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private RegistrarAltaSolicitanteEnProyeccionPort registrarAltaEnProyeccionPort;

    @MockitoSpyBean
    private TrabajoRecomendacionPort trabajoRecomendacionPort;

    private long idSolicitante;

    private static RequestPostProcessor conRol(String rol, UUID sub) {
        return jwt().jwt(token -> token.subject(sub.toString()).claim(JwtConfig.CLAIM_ROL, rol))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    /** Misma preparación que ClasificacionEvaluacionFlujoTest: DEFAULT de V1–V5 y la fila única de V6. */
    @BeforeAll
    void prepararDatos() throws Exception {
        ejecutar("ALTER TABLE solicitante ALTER COLUMN fecha_registro SET DEFAULT CURRENT_TIMESTAMP");
        ejecutar("ALTER TABLE riesgo ALTER COLUMN fecha_creacion SET DEFAULT CURRENT_TIMESTAMP");
        ejecutar("ALTER TABLE regla_scoring ALTER COLUMN fecha_creacion SET DEFAULT CURRENT_TIMESTAMP");
        ejecutar("ALTER TABLE evaluacion ALTER COLUMN fecha_evaluacion SET DEFAULT CURRENT_TIMESTAMP");
        ejecutar("INSERT INTO configuracion_clasificacion_actual (id_configuracion, id_politica) VALUES (1, NULL)");

        long idRiesgo = crearYObtenerId("/api/v1/variables-riesgo", JwtConfig.ROL_ADMINISTRADOR, """
                {"variable": "INGRESOS_MENSUALES", "descripcion": "Ingresos mensuales del solicitante"}""",
                "$.idVariableRiesgo");
        crearYObtenerId("/api/v1/reglas-scoring", JwtConfig.ROL_ADMINISTRADOR, """
                {"idRiesgo": %d, "operador": ">=", "valorCondicion": "3000000", "puntaje": 50}""".formatted(idRiesgo),
                "$.idRegla");
        crearYObtenerId("/api/v1/politicas-clasificacion", JwtConfig.ROL_ADMINISTRADOR, POLITICA, "$.idPolitica");
        idSolicitante = crearYObtenerId("/api/v1/solicitantes", JwtConfig.ROL_ANALISTA, """
                {"nombreCompleto": "Solicitante US 5.1.1", "numeroDocumento": "900000511",
                 "ingresosMensuales": 4000000, "deudasMensuales": 1200000, "numeroMoras": 0,
                 "historialCrediticio": "BUENO", "antiguedadLaboral": 2}""", "$.idSolicitante");
    }

    /** Escenario 1: registro automático en el historial con todos sus datos y 201 Created. */
    @Test
    void elCalculoDebeRegistrarLaEvaluacionEnElHistorialConElUsuarioEvaluadorAutenticado() throws Exception {
        UUID analista = UUID.randomUUID();

        String respuesta = mockMvc.perform(evaluar("{\"idSolicitante\": " + idSolicitante + "}", analista))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idSolicitante").value(idSolicitante))
                .andExpect(jsonPath("$.scoreTotal").value(50))
                .andExpect(jsonPath("$.categoria").value("MEDIO"))
                .andExpect(jsonPath("$.fechaEvaluacion").exists())
                .andExpect(jsonPath("$.usuarioEvaluador").value(analista.toString()))
                .andReturn().getResponse().getContentAsString();
        long idEvaluacion = ((Number) JsonPath.read(respuesta, "$.idEvaluacion")).longValue();

        assertThat(texto("SELECT CONCAT(id_solicitante, '|', score_total, '|', categoria, '|', "
                + "CASE WHEN fecha_evaluacion IS NULL THEN 'SIN_FECHA' ELSE 'CON_FECHA' END, '|', usuario_evaluador) "
                + "FROM evaluacion WHERE id_evaluacion = " + idEvaluacion))
                .isEqualToIgnoringCase(idSolicitante + "|50|MEDIO|CON_FECHA|" + analista);
        assertThat(texto("SELECT SUM(puntaje_obtenido) FROM detalle_evaluacion WHERE id_evaluacion = "
                + idEvaluacion)).isEqualTo("50");
    }

    /** Escenario 2: sin identificador del solicitante se rechaza con 400 sin guardar datos. */
    @Test
    void sinIdentificadorDelSolicitanteDebeResponder400SinPersistirNada() throws Exception {
        long evaluaciones = contar("evaluacion");
        long detalles = contar("detalle_evaluacion");

        mockMvc.perform(evaluar("{}", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        assertThat(contar("evaluacion")).isEqualTo(evaluaciones);
        assertThat(contar("detalle_evaluacion")).isEqualTo(detalles);
    }

    @Test
    void unIdentificadorDelSolicitanteInvalidoDebeResponder400SinPersistirNada() throws Exception {
        long evaluaciones = contar("evaluacion");
        long detalles = contar("detalle_evaluacion");

        mockMvc.perform(evaluar("{\"idSolicitante\": -1}", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mockMvc.perform(evaluar("{\"idSolicitante\": \"abc\"}", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        assertThat(contar("evaluacion")).isEqualTo(evaluaciones);
        assertThat(contar("detalle_evaluacion")).isEqualTo(detalles);
    }

    /**
     * Integridad: la evaluación y sus detalles ya se insertaron (con flush) cuando falla la creación del trabajo
     * de la saga; la transacción completa se revierte y no quedan registros parciales.
     */
    @Test
    void unFalloPosteriorALaInsercionDebeRevertirLaTransaccionSinRegistrosParciales() throws Exception {
        long evaluaciones = contar("evaluacion");
        long detalles = contar("detalle_evaluacion");
        doThrow(new IllegalStateException("fallo simulado al crear el trabajo de la saga"))
                .when(trabajoRecomendacionPort).crear(anyLong(), any(), any());

        mockMvc.perform(evaluar("{\"idSolicitante\": " + idSolicitante + "}", UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));

        assertThat(contar("evaluacion")).isEqualTo(evaluaciones);
        assertThat(contar("detalle_evaluacion")).isEqualTo(detalles);
    }

    private MockHttpServletRequestBuilder evaluar(String cuerpo, UUID analista) {
        return post(RUTA_EVALUACIONES).with(conRol(JwtConfig.ROL_ANALISTA, analista))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private long crearYObtenerId(String ruta, String rol, String cuerpo, String campoId) throws Exception {
        String respuesta = mockMvc.perform(post(ruta).with(conRol(rol, UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, campoId)).longValue();
    }

    private long contar(String tabla) throws SQLException {
        return Long.parseLong(texto("SELECT COUNT(*) FROM " + tabla));
    }

    private String texto(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private void ejecutar(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}

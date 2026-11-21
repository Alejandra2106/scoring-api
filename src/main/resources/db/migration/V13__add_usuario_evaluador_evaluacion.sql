-- US 5.1.1: usuario evaluador de cada evaluación registrada en el historial (claim sub del JWT del
-- analista autenticado, UUID; mismo tipo que politica_clasificacion.publicado_por de V7).
-- Las evaluaciones anteriores a esta migración no tienen evaluador y conservan NULL.
--
-- La obligatoriedad se exige con un trigger de inserción y no con un CHECK NOT VALID: PostgreSQL
-- comprueba un CHECK NOT VALID también en los UPDATE, y bloquearía el paso PENDIENTE -> COMPLETADA
-- de la saga (V11) en las evaluaciones PENDIENTE registradas antes de esta migración.
-- Todas las funciones fijan search_path = esquema de la migración, pg_temp (como V8 y V11).

ALTER TABLE evaluacion ADD COLUMN usuario_evaluador UUID;

-- =====================================================================================
-- 1. Toda evaluación nueva se inserta con su usuario evaluador. AFTER INSERT: ve la fila final.
-- =====================================================================================
CREATE FUNCTION fn_tg_evaluacion_usuario_evaluador_inicial() RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ${flyway:defaultSchema}, pg_temp
AS $$
BEGIN
    IF NEW.usuario_evaluador IS NULL THEN
        RAISE EXCEPTION 'La evaluacion % debe registrarse con su usuario evaluador', NEW.id_evaluacion
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_evaluacion_usuario_evaluador';
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER tg_evaluacion_usuario_evaluador_inicial
    AFTER INSERT ON evaluacion
    FOR EACH ROW EXECUTE FUNCTION fn_tg_evaluacion_usuario_evaluador_inicial();

-- =====================================================================================
-- 2. Trazabilidad: el usuario evaluador registrado no cambia (tampoco se asigna a las históricas).
-- =====================================================================================
CREATE FUNCTION fn_tg_evaluacion_usuario_evaluador_inmutable() RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ${flyway:defaultSchema}, pg_temp
AS $$
BEGIN
    IF NEW.usuario_evaluador IS DISTINCT FROM OLD.usuario_evaluador THEN
        RAISE EXCEPTION 'El usuario evaluador de la evaluacion % es inmutable', OLD.id_evaluacion
            USING ERRCODE = 'object_not_in_prerequisite_state';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_evaluacion_usuario_evaluador_inmutable
    BEFORE UPDATE OF usuario_evaluador ON evaluacion
    FOR EACH ROW EXECUTE FUNCTION fn_tg_evaluacion_usuario_evaluador_inmutable();

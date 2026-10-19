# Pendientes tras el despliegue del 2026-10-19

Estado de `motor_scoring`: `public` en V11, `consulta` en V1, roles de ejecución creados y saga **deshabilitada**
(registro en `despliegue-v10-v11.md`). Nada de lo siguiente se ha hecho; ningún secreto se escribe en archivos
ni en el repositorio.

## 1. Credenciales de base de datos

| Paso | Responsable | Detalle |
|---|---|---|
| Asignar contraseñas | Administración de la base | `\password scoring_app` y `\password consulta_app` (y `scoring_propietario` y `consulta_propietario` solo si van a ejecutar migraciones) |
| Configurar `scoring-api` | Quien despliega | `DB_USERNAME=scoring_app` y su contraseña. Hoy la configuración local usa un rol superusuario |
| Configurar `evaluation-query-api` | Quien despliega | `EVALUATION_QUERY_DB_USERNAME=consulta_app` y su contraseña |
| `auth-api` | — | Sin cambios: ya usa `auth_api` (ejecución) y `auth_migrator` (propietario) |
| Migraciones futuras | Quien despliega, con autorización | Solo con `*_FLYWAY_MIGRAR_AL_ARRANCAR=true` y `SPRING_FLYWAY_USER` del propietario, tras un respaldo verificado |

## 2. Claves e identidad de servicio de la saga (HU14)

1. Generar un par RSA de servicio, distinto del de `auth-api` (README de `evaluation-query-api`), y elegir su `kid`.
2. `scoring-api`: `SCORING_SERVICE_JWT_KEY_ID`, `SCORING_SERVICE_JWT_PRIVATE_KEY_LOCATION`,
   `SCORING_SERVICE_JWT_PUBLIC_KEY_LOCATION`, `EVALUATION_QUERY_API_BASE_URL` y `SCORING_SAGA_HABILITADA=true`.
3. `evaluation-query-api`: `SCORING_SERVICE_JWT_KEY_ID`, `SCORING_SERVICE_JWT_PUBLIC_KEY_LOCATION` y
   `EVALUATION_QUERY_SAGA_HABILITADA=true` (el catálogo aprobado versión 1 ya va incluido).
4. Decidir quién custodia la clave privada y cada cuánto se rota (rotación sin corte con la clave anterior).

Hasta entonces, cada evaluación nueva queda PENDIENTE: no aparece en el historial y su explicación y sus
recomendaciones responden 409. Al habilitar la saga, el reenvío las completa; las que hayan agotado sus 10
intentos quedan en ERROR_PERMANENTE y requieren `reactivar_trabajo_recomendacion`.

## 3. Política de clasificación y reglas de scoring (decisión del negocio)

- `motor_scoring` no tiene ninguna política de clasificación publicada: toda evaluación responde 409
  `POLITICA_VIGENTE_NO_DISPONIBLE`.
- Con las 42 reglas activas actuales, el solicitante usado en el ensayo sumó 425 puntos, fuera del rango
  [-100, 130] que exige HU11; esas evaluaciones se rechazarían con 409 `PUNTAJE_SIN_CLASIFICACION`.
- Pendiente: revisar las reglas (puntajes y variables activas) y publicar la política. No se ha modificado
  ninguna regla ni publicado ninguna política en `motor_scoring`.

## 4. HU14: criterios y decisiones sin resolver

| Elemento | Estado |
|---|---|
| P-HU14-01 catálogo y P-HU14-02 factor desfavorable | Resueltas el 2026-10-19 |
| P-HU14-03 (409 si la evaluación está PENDIENTE), P-HU14-04 (lista vacía con `generadas: false` para evaluaciones anteriores a la saga), P-HU14-05 (rutas), P-HU14-08 (códigos de error) | Implementadas tal como se propusieron; pendientes de confirmación formal |
| CA17 / P-HU14-06 (esquema `consulta` y sus dos tablas) | Implementado y desplegado; falta aprobar la justificación |
| CA01 y CA13 en producción | Verificados en ensayo; en `motor_scoring` no se cumplen de forma efectiva hasta habilitar la saga (sección 2) |
| CA12 (2 s) | Medido en un entorno aislado (p95 ≤ 35 ms con unas 1.400 evaluaciones); falta medirlo en el servidor definitivo |
| CA14 y CA16 (operación) | Falta designar quién vigila los trabajos en ERROR_PERMANENTE y ejecuta la reactivación |
| P-HU10-04 (ventana de 15 min frente a la tolerancia de reloj de 60 s) | Sin decisión |

Detalle de cada decisión: `evaluation-query-api/docs/quality/HU14-decisiones-pendientes.md`.

---
inclusion: auto
name: Seguridad y PII EDF
description: Controles obligatorios de seguridad, proteccion de PII, OWASP para el codigo generado, manejo de secretos y protocolo de respuesta ante hallazgo de seguridad en la iniciativa EDF. Activar al clasificar campos, al generar codigo o configuracion, al escribir logs, o ante cualquier sospecha de exposicion de datos o credenciales.
---

# 08 — Seguridad y PII

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-09-23 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`; su §4 (credencial en texto plano) es rector y se repite aquí solo por referencia |
| Fuentes | Las 6 fuentes vinculantes (abreviaturas en `02` §Pie); decisión H-9 (OWASP) y H-14 del 2026-09-23 |
| Aprobador | PENDIENTE-MANUAL |

## 1. Objetivo y alcance

Los controles de seguridad y protección de datos personales que aplican a todo lo que la iniciativa produce.

**Cubre:** régimen regulatorio, clasificación y tratamiento de PII, OWASP sobre el código generado, secretos, y el protocolo de respuesta ante hallazgo.
**No cubre:** la seguridad de la infraestructura, que es de Experian y Cloud Engineering y está fuera del perímetro (`01` §2, Z3).

## 2. Régimen aplicable `[C]`

**Ley 1581/2012** de protección de datos personales de Colombia. No hay marco SOC 2, ISO 27001, HIPAA ni PCI-DSS en alcance.

Aplica porque el archivo SISBEN lleva **identificadores directos de ~28M personas colombianas**. La clasificación como PII está resuelta: no es una discusión abierta.

## 3. Controles obligatorios sobre datos

| # | Control | Alcance | Estado |
|---|---|---|---|
| S1 | **Cifrado en reposo con KMS** sobre S3 y sobre el XPM | Plataforma | `[C]` |
| S2 | **Cifrado en tránsito** con TLS | Plataforma | `[C]` |
| S3 | **Acceso por roles IAM**, sin credenciales embebidas | Plataforma | `[C]` |
| S4 | **Sin PII en logs**, CloudWatch, Splunk, SNS ni SES | Código generado y agentes | `[C]` |
| S5 | **Enmascaramiento `***`** en mensajes de nivel WARN y ERROR | Código generado | `[C]` |
| S6 | **Exclusión de campos PII de las muestras** y de los reportes | Código generado y análisis | `[C]` |
| S7 | **Retención máxima de 6 meses** en S3 | Plataforma | `[C]` |
| S8 | **Fixtures sintéticos por defecto.** Ningún registro real en desarrollo | Todos | `[C]` |
| S9 | **Sin PII en la bitácora** ni en el expediente de la ingesta | Agentes | `[C]` |

`snappy` es compresión, **no cifrado**. No se cita como control. `[C]`

### 3.1 Clasificación campo a campo `[C]`

Todo campo se clasifica **público o PII** en el análisis de la etapa 0, como una de las nueve dimensiones obligatorias (`07` §3). Es un insumo directo de S4, S5 y S6: sin la clasificación, no se puede decidir qué se enmascara.

Regla dura: **un campo sin clasificar se trata como PII** hasta que se clasifique. El default es restrictivo, no permisivo.

### 3.2 Énfasis en State Calculation `[C]`

El XPM es **visible al cliente**. La HU de origen declara riesgo MUY ALTO. Ningún mensaje de WARN o ERROR de esta etapa puede contener PII, y toda generación aquí es riesgo N4 (`06` §2.2).

## 4. OWASP sobre el código generado `[C]`

Todo código Scala y toda configuración HOCON/JSON que el agente genere cumple estos controles. (decisión H-9, 2026-09-23)

| # | Control | Qué significa aquí |
|---|---|---|
| O1 | **Validación de entrada en la frontera** | Doble nivel del framework: contrato de código por registro (encabezado, pie, nombre de archivo, archivo vacío, llaves duplicadas, validación contextual) **y** DSL declarativo (`validate[]`/`metrics[]`, `fileActions`/`recordAcceptance`) |
| O2 | **Sin inyección** | Nada de concatenar entrada externa en consultas, rutas, expresiones ni predicados. Parametrizar siempre |
| O3 | **Sin log forging** | **Nunca interpolar entrada externa directamente en un mensaje de log.** Pasarla como campo estructurado o sanitizar `\r` y `\n` antes. Un salto de línea inyectado fabrica entradas falsas en el log |
| O4 | **Manejo explícito de errores** | `OxygenException` / `MalformedFileException`. Prohibido silenciar una excepción. Prohibido usar nivel `error` para depuración |
| O5 | **Sin secretos embebidos** | Ni credenciales, ni tokens, ni endpoints con credencial. AWS Secrets Manager / Parameter Store |
| O6 | **Sin datos sensibles en mensajes de error** | El mensaje describe el problema, no muestra el dato |
| O7 | **Configuración, no constantes** | `qualityThreshold` en configuración. Rutas y `generationId` resueltos en ejecución (`01` P4) |
| O8 | **Dependencias fijadas** | Versiones exactas: Scala 2.12.15, Spark 3.3.1, pureconfig 0.17.3, ScalaTest 3.2. Sin rangos abiertos |
| O9 | **Principio de menor privilegio** | El código no asume ni solicita permisos más allá de los del rol de instancia |
| O10 | **Sin criptografía propia** | El cifrado lo provee la plataforma. Prohibido implementar cifrado en el processor |

### 4.1 Antipatrones detectados en el material de referencia `[C]`

Presentes en el corpus genérico y **prohibidos de copiar** (ver también `07` §6.1):

- Logs de depuración emitidos con nivel `error` — viola O4.
- Puntajes de calidad generados aleatoriamente — falsifica la certificación.
- Lectura de un campo distinto al pretendido en el matcher de identidad — defecto funcional silencioso.

## 5. Secretos `[C]`

- Origen autorizado: **AWS Secrets Manager / Parameter Store**. Nunca el código, nunca la configuración, nunca el repositorio.
- Los servicios acceden por rol/identidad, sin llaves de acceso embebidas.
- Validar al arranque que los secretos requeridos existen; fallar rápido si faltan.
- **Prohibido** que un agente lea, copie, cite o emita el valor de un secreto, incluso para verificarlo.

### 5.1 Hallazgo activo: `settings-artifactory.xml` `[C]`

Los repositorios compartidos contienen **una credencial de cuenta de servicio en texto plano**. Sin evidencia de remediación ni de rotación.

Prohibición absoluta y acciones pendientes: ver `01-restricciones-principios.md` §4, que es el texto rector. Resumen operativo:

| Acción | Dueño | Estado |
|---|---|---|
| No abrir, leer, copiar, citar ni incluir el archivo en ningún corpus | Agentes | `[C]` en vigor |
| Reportar a DevSecOps de Experian | Humano | `[PM]` |
| **Rotar la credencial** | Experian | `[PM]` |
| Excluir el archivo del corpus y del control de versiones | Humano | `[PM]` |

## 6. Protocolo de respuesta ante hallazgo de seguridad `[C]`

Si un agente encuentra un secreto, una credencial, PII fuera de lugar o una vulnerabilidad:

1. **Detenerse.** No continuar la tarea en curso.
2. **No reproducir el valor.** Referenciarlo por nombre de clave, ruta o campo — nunca por contenido.
3. **Clasificar como N4** (`06` §2.2).
4. **Escalar** al desarrollador y, si es un secreto o PII expuesta, a DevSecOps de Experian (`06` §5).
5. **Registrar en la bitácora** el hallazgo sin el valor: qué tipo, dónde, qué alcance.
6. Si el hallazgo es un secreto: **exigir rotación**, no solo remoción. Un secreto removido pero no rotado sigue comprometido.
7. **Buscar el mismo patrón** en el resto del corpus y reportar el alcance completo.

Regla dura: **un hallazgo de seguridad no se resuelve por el agente.** Se documenta y se escala. Los issues críticos se resuelven antes de continuar.

## 7. Datos del piloto `[A:OQ-07(a)]`

Sobre qué datos y en qué ambiente corre la aceptación del piloto **no está decidido**. Las tres opciones registradas son: sintéticos con forma SISBEN, ejecución de Experian con datos reales, o datos liberados.

Hasta que haya decisión escrita de Experian, **rige el default: fixtures sintéticos únicamente** (S8). Un agente no puede proponer el uso de datos reales.

Pendiente relacionado: la política de Experian sobre el uso de herramientas de IA externas sobre su código y documentación. Tratada como **no verificada**, no como inexistente. `[A:OQ-07(b)]`

## 8. Lista de verificación previa a entregar un artefacto `[C]`

Ningún artefacto generado se entrega si alguna casilla queda sin marcar:

- [ ] Sin secretos, credenciales, tokens ni endpoints con credencial.
- [ ] Sin PII en logs, mensajes de error, reportes ni notificaciones.
- [ ] Enmascaramiento `***` en WARN y ERROR donde pueda aparecer un campo PII.
- [ ] Entrada externa validada en la frontera.
- [ ] Entrada externa **no interpolada** en mensajes de log (O3).
- [ ] Errores manejados explícitamente, ninguno silenciado.
- [ ] Sin valores hardcodeados que deban ser configuración.
- [ ] Dependencias con versión exacta.
- [ ] Campos PII excluidos de muestras y reportes.
- [ ] Fixtures sintéticos, declarados como tales.
- [ ] Aditividad verificada: nada renombrado ni eliminado.
- [ ] Archivos base `*-common-application.conf` intactos.

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Rotación de la credencial de `settings-artifactory.xml` | `[PM]` |
| 2 | Reporte formal a DevSecOps de Experian | `[PM]` |
| 3 | Ruta de datos del piloto | `[A:OQ-07(a)]` / `[A:PT-02]` |
| 4 | Política de Experian sobre IA externa | `[A:OQ-07(b)]` |
| 5 | Política de retención de la bitácora (distinta de la retención de datos en S3) | `[A:OQ-RO-3]` |
| 6 | Herramientas de análisis estático y seguridad (Veracode, Sonar, Archer figuran como decisión pendiente) | `[A:OQ-T-05]` |

## Pie de trazabilidad

- Régimen y controles S1-S9: `PD/tech` §Security §Compliance; `11/tech` §Compliance; `13/tech` §Security.
- OWASP sobre el código generado: decisión H-9 (2026-09-23); validación de entrada de `PD/tech` §Input Validation y `13/tech` §Input Validation.
- Antipatrones: `PD/tech` §Example Code (nota sobre los ejemplos `Credit*`).
- Secretos y credencial en texto plano: `PD/tech` §Secrets Management; `PD/open-questions` §Actions (HALLAZGO-22); decisión H-14.
- Énfasis en State Calculation: `13/tech` §Encryption; `13/vision` §Non-Functional.
- Datos del piloto: `PD/open-questions` OQ-07(a), OQ-07(b), PT-02.
- Última verificación de referencias: 2026-09-23.

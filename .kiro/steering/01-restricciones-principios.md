---
inclusion: always
---

# 01 — Restricciones y principios (documento rector)

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-09-23 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | **Rector. Gana sobre cualquier otro steering de esta iniciativa.** |
| Fuentes | `Product-Definition/vision-document.md`, `Product-Definition/technical-environment.md`, `Product-Definition/open-questions.md`, `EXP-023_Paquete_Tecnico_Desarrollo/11_ingesta_limpieza/*`, `.../12_requerimientos_y_orquestador/*`, `.../13_transformadores/*`, decisiones H-1…H-14 del 2026-09-23 |
| Aprobador | PENDIENTE-MANUAL (ver `06`, OQ-06) |

## 0. Precedencia y resolución de conflictos

### 0.1 Orden de precedencia

| # | Autoridad | Nota |
|---|---|---|
| 1 | **Este documento** (`01-restricciones-principios.md`) | Rector |
| 2 | **Regla de anclaje de entorno** (§0.2) | Decide dónde corre el framework |
| 3 | Restricciones duras N1-N5 del `vision-document.md` global | Perímetro, human-in-the-loop, bitácora, transferibilidad, genericidad |
| 4 | Decisiones de sub-grupo, la más reciente primero | `13_transformadores` (2026-09-22) > `11`/`12` (2026-09-21) |
| 5 | `Product-Definition/` global (2026-09-18) | Snapshot; cede ante 2, 3 y 4 |
| 6 | Recomendación de cualquier agente | Cede siempre |

### 0.2 Regla de anclaje de entorno `[C]`

**El framework y todos sus skills corren dentro de Kiro, en la máquina del desarrollador.** No corren en AWS, no corren en un ambiente compartido, no son un job Spark.

Consecuencias, y son vinculantes:

- Cuando el `technical-environment.md` global dice "Cloud only (AWS)", describe **la infraestructura del pipeline EDF**, no la del framework. Toda afirmación que atribuya al framework un runtime en nube queda subordinada a esta regla. `[C]`
- Lo que el framework produce son **artefactos** (código Scala, configuración HOCON/JSON, documentos). El despliegue y la ejecución de esos artefactos en AWS son actos humanos, posteriores y fuera del framework.
- Esta regla cierra localmente `OQ-T-01` para esta iniciativa. No cierra la pregunta a nivel del registro global, que sigue vigente hasta decisión escrita.

### 0.3 Regla de desempate

**Ante conflicto entre una recomendación y una restricción, prevalece la restricción.** Sin excepción, sin interpretación, sin optimización.

Si el conflicto es entre este pack y un pack de steering global de la casa (`kd-common-*`), el agente **no resuelve por su cuenta**: escala según `06-riesgo-y-aprobaciones.md` §5 y se detiene.

### 0.4 Registro único de preguntas abiertas

`Product-Definition/open-questions.md` es **el único registro vigente**. 27 preguntas abiertas al 2026-09-18 (11 de negocio, 16 técnicas). `[C]`

Este registro no viaja con el framework. Si no está en el workspace, los IDs `OQ-*`, `PT-*`, `CQ-*`, `GAP-*` se citan tal como constan en el steering, sin verificarlos, y eso no se reporta como hallazgo.

Prohibido crear un registro paralelo de preguntas abiertas. Prohibido responder una pregunta abierta por inferencia. Una pregunta abierta se cierra solo con decisión humana registrada.

## 1. Principios de diseño

| # | Principio | Consecuencia práctica |
|---|---|---|
| P1 | **Humano en el ciclo, siempre** | Ningún agente avanza de etapa sin aprobación registrada |
| P2 | **Fricción proporcional al riesgo** | Variación simple → revisión corta; decisión de alto riesgo → revisión profunda (`06`) |
| P3 | **Aditividad pura** | Se agrega processor, bloque, sección y catálogo nuevos. Nunca se renombra ni elimina campo curado, Field Handler global, entrada de `foundation-stage.json` ni el SP compartido |
| P4 | **Nada hardcodeado** | `qualityThreshold = 0.90` vive en configuración. `generationId` y rutas S3 se resuelven en ejecución, nunca se fijan en código |
| P5 | **Fechas en epoch UTC como `Long`** | Toda fecha, sin excepción |
| P6 | **Fail fast en la frontera** | Validación estricta en la entrada de cada etapa y de cada análisis |
| P7 | **Trazabilidad por construcción** | Toda afirmación con cita y marcador de estado (`05` §3, §6) |
| P8 | **No asumir: preguntar** | La ambigüedad se registra y bloquea el cierre; no se resuelve por probabilidad |
| P9 | **Genericidad por construcción, probada con SISBEN** | SISBEN es el piloto, no el diseño. Lo específico de SISBEN no entra a los guard-rails |
| P10 | **Inmutabilidad de los archivos base** | `*-common-application.conf` de cada etapa es inmutable. Solo se toca `*-inner.conf` |

## 2. Perímetro: zonas de escritura

El perímetro no se expresa como un veto global sino por zona. Esta es la tabla operativa del guard-rail.

| Zona | Qué contiene | Permiso del agente |
|---|---|---|
| **Z1 — Workspace del desarrollador** | Documentos `.md`, análisis, mapeos, expediente, bitácora, **y los artefactos generados de código Scala y configuración HOCON/JSON** | **Leer y escribir.** Es donde vive el entregable |
| **Z2 — Repositorios protegidos** | `buro.colombia_innersource`, repos Oxygen InnerSource, `umbrella/` | **Solo lectura.** Sin commit, sin push, sin PR, sin branch, sin merge |
| **Z3 — Infraestructura y operación** | AWS (IAM, S3, EMR, Glue, KMS, Redshift), HBase, redes, VPN/Transit Gateway, DAGs de Airflow/MWAA, Secrets Manager, CloudWatch | **Sin acceso de escritura.** Ni directo ni como comando ejecutable propuesto |

### 2.1 Lo que sí hace el agente con código `[C]`

El agente **genera y modifica código y configuración HOCON/JSON en Z1**. Eso es el producto, no una excepción al perímetro: las features F3, F4 y F10 del alcance dependen de ello.

Condiciones obligatorias sobre todo código y configuración generados:

- Cumplen **OWASP** (ver `08-seguridad-y-pii.md` §4): validación de entrada, sin inyección, manejo explícito de errores, sin secretos embebidos, sin datos sensibles en log.
- Son **aditivos** (P3) y no tocan los archivos base (P10).
- Llevan prueba asociada según el régimen de calidad por ámbito de `04-reglas-analisis.md` §8.
- Se entregan como **propuesta para revisión humana**. El agente no los transcribe a Z2 ni los ejecuta.

### 2.2 Lo que nunca hace

- Escribir en Z2 o Z3, por ningún medio, incluido `git`, `aws`, `terraform`, `kubectl`, `spark-submit` o equivalente.
- Disparar un DAG de Airflow, un job de EMR o un despliegue.
- Leer, copiar o generar datos reales de personas. Fixtures sintéticos por defecto, siempre.
- Emitir, copiar o citar credenciales. Ver §4.
- Cerrar una pregunta abierta, congelar un supuesto o elegir entre alternativas contradictorias de las fuentes.
- Modificar o refactorizar el propio framework después de la entrega (fuera de contrato, E6).

## 3. Actividades permitidas y prohibidas

### 3.1 Permitidas

Analizar, proponer, documentar, mapear, detectar ambigüedad, preguntar, generar artefactos en Z1, diseñar pruebas, registrar estado y bitácora, clasificar riesgo, bloquear su propio avance.

### 3.2 Prohibidas

| # | Prohibición | Origen |
|---|---|---|
| X1 | Ejecutar o modificar infraestructura AWS | N1, Disallow List |
| X2 | Ejecutar o modificar redes, VPN, Transit Gateway | N1 |
| X3 | Modificar IAM, roles, políticas o permisos | N1 |
| X4 | Escribir en `buro.colombia_innersource` o en cualquier repo Oxygen | Z2, decisión H-6/H-9 |
| X5 | Modificar los DAGs de Airflow o la orquestación existente | E10 |
| X6 | Modificar bases de datos, esquemas, HBase o Redshift | Disallow List |
| X7 | Ejecutar el pipeline, Spark, o cualquier despliegue | N2 |
| X8 | Usar datos reales de personas | E15, C-06, Ley 1581/2012 |
| X9 | Emitir PII en logs, bitácora, reportes, SNS/SES o CloudWatch | Ley 1581/2012, `08` |
| X10 | Copiar, leer o emitir `settings-artifactory.xml` | §4 |
| X11 | Generar IaC | E8 |
| X12 | Cerrar preguntas abiertas por inferencia | P8, §0.4 |
| X13 | Avanzar de etapa sin aprobación humana registrada | P1, N2 |
| X14 | Modificar processors o configuración de ingestas ya certificadas | P3, P10 |

## 4. Prohibición con acción: credencial en texto plano `[C]`

Los repositorios compartidos contienen `settings-artifactory.xml` con una **credencial de cuenta de servicio en texto plano** (HALLAZGO-22 / riesgo B). No consta como remediada ni rotada en ninguna fuente.

Prohibición y acciones, obligatorias:

1. **Prohibido** abrir, leer, copiar, citar, incluir en un corpus de patrones o emitir el contenido de cualquier `settings-artifactory.xml`. Si una tarea parece requerirlo, la tarea se detiene y se escala.
2. **Prohibido** incluir cualquier `settings-artifactory.xml` en ejemplos, plantillas o artefactos generados.
3. **Acción pendiente — dueño humano:** reportar a DevSecOps de Experian y **rotar la credencial**. `[PM]`
4. **Acción pendiente — dueño humano:** excluir el archivo del corpus de patrones y del control de versiones. `[PM]`
5. Si un agente detecta cualquier otro secreto en texto plano, aplica el protocolo de `08-seguridad-y-pii.md` §6: detenerse, no reproducir el valor, escalar.

## 5. Responsabilidades

| Actividad | Agente | Desarrollador humano |
|---|---|---|
| Analizar la fuente y mapear campos | Ejecuta | Revisa y aprueba |
| Detectar ambigüedad y preguntar | Ejecuta | Responde o escala |
| Generar código Scala / HOCON / JSON en Z1 | Ejecuta | **Revisa, aprueba, transcribe a Z2 y despliega** |
| Diseñar pruebas y fixtures sintéticos | Ejecuta | Aprueba y ejecuta |
| Ejecutar el pipeline / Spark / DAG | **Nunca** | Ejecuta |
| Commit, PR, merge en repos protegidos | **Nunca** | Ejecuta |
| Cambios de infraestructura, IAM, redes | **Nunca** | Ejecuta (Cloud Engineering / Experian) |
| Mantener expediente y bitácora | Ejecuta | Audita |
| Clasificar riesgo | Propone | Confirma |
| Cerrar una pregunta abierta | **Nunca** | Decide y registra |
| Aprobar el patrón generado | **Nunca** | Equipo Ingesta 3 / aprobador designado `[A:OQ-06]` |

Regla de cierre: **el análisis, por sí mismo, no produce código ejecutable ni modificación automática de nada.** El paso de artefacto a sistema es siempre humano.

## 6. Criterios de escalamiento

El detalle operable (taxonomía de riesgo, umbrales, matriz de aprobadores, rutas) vive en `06-riesgo-y-aprobaciones.md`. Aquí solo el umbral de parada, que es obligatorio en todo momento:

Un agente **se detiene y escala** cuando:

1. La acción tocaría Z2 o Z3.
2. Una fuente vinculante contradice a otra y no hay regla de precedencia aplicable.
3. La tarea depende de una afirmación marcada `[A:<ID>]`.
4. La tarea requeriría datos reales, PII o un secreto.
5. Aparece un secreto, una credencial o PII donde no debería estar.
6. El mismo enfoque falló dos veces.
7. La tarea exige cerrar una pregunta abierta para poder continuar.

Al detenerse: registra el motivo en la bitácora (`09`), nombra la pregunta abierta, y propone las alternativas sin elegir.

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Aprobador del patrón, nominal y con fecha, antes del 2026-11-19 | `[A:OQ-06]` |
| 2 | Rotación de la credencial de `settings-artifactory.xml` | `[PM]` §4 |
| 3 | Política de Experian sobre uso de IA externa sobre su código | `[A:OQ-07(b)]` |
| 4 | Ruta de datos del piloto (sintéticos / reales de Experian / liberados) | `[A:OQ-07(a)]` / `[A:PT-02]` |
| 5 | Versión de Java del repo `buro.colombia_innersource` (11 vs 17) y su efecto sobre Nashorn | `[A:limpieza §3]` |

## Pie de trazabilidad

- Restricciones N1-N5, E1-E15 y la Disallow List: `Product-Definition/vision-document.md`, `Product-Definition/technical-environment.md`.
- Zonas de escritura y generación de código bajo OWASP: decisiones H-2, H-6, H-9, H-10 (2026-09-23).
- Perímetro de solo lectura sobre Oxygen/Bureau: `12_requerimientos_y_orquestador/technical-environment.md` §T5, §T6.
- Credencial en texto plano: `Product-Definition/open-questions.md` §Actions (HALLAZGO-22).
- Última verificación de referencias: 2026-09-23.

---
inclusion: auto
name: Reglas de analisis EDF
description: Metodologia obligatoria de analisis de la iniciativa EDF: tipos de analisis, criterios de completitud, manejo de incertidumbre, gestion de supuestos, trazabilidad de conclusiones y regimen de calidad por ambito. Activar al analizar una fuente, mapear campos, evaluar reglas de negocio, o antes de generar cualquier artefacto de etapa.
---

# 04 — Reglas de análisis

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-09-23 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`; formato según `05-convenciones.md` |
| Fuentes | Las 6 fuentes vinculantes (abreviaturas en `02` §Pie); decisiones H-7 y vacío-04 del 2026-09-23 |
| Aprobador | PENDIENTE-MANUAL |

## 1. Objetivo y alcance

Que dos agentes distintos, con el mismo insumo, produzcan análisis equivalentes y auditables — y que ninguno cierre un análisis con supuestos no aprobados.

**Cubre:** los cuatro tipos de análisis de §2, su metodología, criterios de completitud, manejo de incertidumbre, gestión de supuestos, trazabilidad y el régimen de calidad aplicable.
**No cubre:** la generación de artefactos de etapa, que se rige por `07-contratos-frontera.md`; ni la ejecución, que está prohibida (`01` §2.2).

Regla dura heredada: **el análisis no genera código en ninguna forma.** Su salida es `.md`. `[C]` Ampliación por decisión del usuario (2026-11-04): el análisis técnico también produce los `.yaml` de insumo de cada etapa (`source-spec-*.yaml`, `yaml-skill-*.yaml`). Son datos declarativos para la skill de etapa, no código ni configuración del pipeline. `[P]` hasta la firma del aprobador del pack.

## 2. Tipos de análisis

Se gobierna por **tipo de análisis**, con la nomenclatura actual de los skills. Los tipos cuyo skill aún no existe se definen aquí con la nomenclatura vigente y quedan `[PM]` a nivel de skill. (decisión vacío-04, 2026-09-23)

| Tipo | Nombre del proceso | Skill asociado | Estado del skill |
|---|---|---|---|
| T1 | **Skill-Analysis** — análisis de requerimiento y contexto (etapa 0) | `edf-analisis`, modo etapa 0 | `[P]` construido, pendiente de certificar |
| T2 | **Skill-Curation** — análisis de selección de reglas, catálogos y homologaciones | `edf-analisis`, modo análisis técnico (produce `source-spec-*.yaml` de las etapas 1–3) | `[P]` construido, pendiente de certificar |
| T3 | **Skill-Pinning** — análisis de fijación de supuestos y decisiones | `edf-analisis`, modo análisis técnico (produce `yaml-skill-*.yaml` de las etapas 4–6) | `[P]` construido, pendiente de certificar |
| T4 | **Análisis de frontera y contratos** — costuras entre etapas | `edf-analisis` (contrato Curation → Pinning, `11`); resto transversal | `[PM]` |

`edf-analisis` no genera código: su salida es `.md` y `.yaml` de insumo (regla dura de arriba).

### 2.1 Advertencia de colisión de nombres `[C]`

`Skill-Curation` y `Skill-Pinning` **comparten nombre con las etapas 3 y 4 del pipeline y no son lo mismo**:

| Nombre | Como etapa del pipeline | Como tipo de análisis |
|---|---|---|
| Curation | Etapa 3: estandariza, homologa, convierte a epoch, enriquece | T2: decide qué reglas y catálogos aplican a la fuente |
| Pinning | Etapa 4: resuelve identidad y asigna PIN | T3: fija y congela un supuesto para poder avanzar |

Al escribir, es obligatorio calificar: "etapa Curation" / "análisis Skill-Curation"; "etapa Pinning" / "análisis Skill-Pinning" (`02` §4).

## 3. Metodología común

Los cuatro tipos siguen la misma secuencia. Ningún paso se salta.

| Paso | Qué se hace | Salida |
|---|---|---|
| 1 | **Verificar insumos.** La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene. Opcionales: archivo de muestra y layout. Se buscan en `expedientes/<id>/fuentes/` (compuerta `.kiro/hooks/compuerta-etapa0.json`) | Declaración de insumos usados |
| 2 | **Leer antes de afirmar.** Prohibido afirmar sobre un documento no leído | — |
| 3 | **Extraer y mapear.** Todos los campos, todas las reglas, todas las dependencias | `mapeo-campos.md`, `contexto-ingesta.md` |
| 4 | **Confrontar fuentes.** Buscar activamente contradicción entre fuentes, no solo confirmación | Lista de contradicciones |
| 5 | **Marcar estado.** Cada afirmación con `[C]`/`[P]`/`[A:ID]`/`[S]`/`[PM]` (`05` §3) | — |
| 6 | **Registrar ambigüedades y preguntar.** Sin asumir (§5) | Preguntas abiertas con ID |
| 7 | **Declarar supuestos.** Con dueño, fecha y consecuencia si son falsos (§6) | Registro de supuestos |
| 8 | **Presentar y esperar aprobación.** El análisis no se cierra solo | Aprobación registrada en bitácora |

Regla de cierre: **el Orquestador no habilita la etapa siguiente hasta que el análisis esté aprobado.** `[C]`

## 4. Criterios de completitud y evaluación

### 4.1 Completitud del mapeo `[C]`

**Todos los campos de la fuente, no una muestra.** Un mapeo parcial es un análisis fallido, no un análisis preliminar.

Cada campo se documenta con las **nueve dimensiones obligatorias** de `07-contratos-frontera.md` §3: nombre en origen y curado, clasificación público/PII, tipo de dato, regla de transformación (incluida EPOCH), catálogo asociado, obligatoriedad, efecto de la violación, pertenencia a la llave de identidad, y Field Handler de reconciliación.

### 4.2 Completitud del contrato del archivo `[C]`

Seis dimensiones, ninguna opcional: patrón de nombre, delimitador, encoding, presencia de encabezado y pie, tipo de evento, número de columnas.

### 4.3 Checklist de cierre `[C]`

Un análisis **no se declara cerrado** si alguna casilla está vacía:

- [ ] Insumos obligatorios presentes y leídos. La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene.
- [ ] Todos los campos de la fuente mapeados, con las nueve dimensiones.
- [ ] Contrato del archivo completo (seis dimensiones).
- [ ] Todas las reglas de negocio extraídas y trazadas a su fuente.
- [ ] Dependencias listadas, con dueño donde se conozca.
- [ ] Toda afirmación con marcador de estado y cita.
- [ ] Ambigüedades registradas como preguntas abiertas con ID.
- [ ] Supuestos declarados con dueño y consecuencia.
- [ ] Contradicciones entre fuentes explicitadas, **no resueltas por el agente**.
- [ ] Clasificación público/PII campo a campo completa.
- [ ] Ningún dato real usado; fixtures sintéticos únicamente.
- [ ] Plan de ruta por etapas propuesto.
- [ ] Riesgos identificados con impacto y mitigación.

### 4.4 Lo que el análisis nunca hace `[C]`

- Generar código, en ninguna forma.
- Mapear a XPM. **El XPM se arma al final del proceso**, no en el análisis.
- Elegir entre dos fuentes que se contradicen.
- Cerrar una pregunta abierta.
- Rellenar un hueco con el valor más probable.

## 5. Manejo de la incertidumbre

**Detectar y preguntar. Nunca asumir.** El análisis se bloquea hasta resolver. `[C]`

Protocolo, en orden:

1. Detectar la ambigüedad.
2. Registrarla como pregunta abierta con ID (`05` §4.3) en el registro único (`01` §0.4).
3. Marcar como `[A:<ID>]` toda afirmación que dependa de ella.
4. **Bloquear el cierre** del análisis si la ambigüedad afecta un entregable.
5. Presentar las alternativas al humano, con la consecuencia de cada una, **sin recomendar una salida como si fuera la respuesta**.

### 5.1 Casos reales de referencia `[C]`

Estos casos ya ocurrieron en el proyecto. Son el patrón de lo que hay que detectar:

| Caso | Naturaleza de la ambigüedad |
|---|---|
| `fec_digitacion` numérico vs. carácter | Tipo de dato contradictorio entre fuentes |
| SISBEN y la fuente judicial comparten el tipo de evento 30 | Identificador no discriminante |
| ¿SISBEN es fuente oficial? | Clasificación con efecto en jerarquía de fuentes |
| Catálogos que cambian "a mitad de camino" | Fuente inestable durante el análisis |
| Regla de concatenación de nombres en Curation | Transcripción de sesión confusa |
| "no cross subscriber validation special" | Regla **retirada en vivo por su autor**; no consta en ninguna HU |
| Semántica de los Field Handlers LFP / LAP / EUC | El dueño del dominio mostró confusión verbal |
| Delimitador `;` vs `\|` y 17/18/22 campos | Fuentes igual de específicas en contradicción directa |

Lección operativa registrada: **cuatro discrepancias documentales serias habrían generado código incorrecto** si no se hubieran resuelto antes de construir. La detección temprana no es una cortesía del proceso: es su función. `[C]`

## 6. Gestión de supuestos

Todo supuesto se declara. Un supuesto no declarado es un defecto. `[C]`

Formato obligatorio por supuesto:

| Campo | Contenido |
|---|---|
| ID | `SUP-<tipo>-<NNN>` |
| Enunciado | Qué se está asumiendo, en una frase |
| Por qué se asume | Y qué evidencia falta para confirmarlo |
| Dueño | Quién puede confirmarlo o negarlo, nominal |
| Fecha límite | Cuándo se necesita la respuesta |
| Consecuencia si es falso | Qué se rompe, y si es reversible |
| Artefactos que dependen de él | Lista |

Reglas:

- Un supuesto con consecuencia irreversible **no habilita generación**: se escala antes.
- Un supuesto se convierte en `[C]` solo con decisión humana registrada, nunca por antigüedad ni por ausencia de objeción.
- Al cerrar cada ingesta se registra el **conteo real de reglas** encontradas, para contrastar contra el multiplicador estimado (campos × 3-5), que ya tiene una medición en contra. `[C]`

## 7. Trazabilidad de conclusiones

Cadena obligatoria para toda conclusión: **fuente citada → afirmación marcada → conclusión → artefacto afectado.** `[C]`

- Formato de cita según `05` §6. Documento con `§sección`; sesión con `[<código> | hh:mm:ss]`; decisión con `(decisión <ID>, <fecha>)`; código con `ruta::símbolo`.
- **Toda afirmación `[C]` lleva cita.** Sin cita, no puede ser `[C]`.
- Jerarquía de autoridad de la evidencia, para este proyecto:

| Nivel | Fuente | Nota |
|---|---|---|
| 1 | HU formales (`09_SISBEN_HU/HU-SISBEN-00X`) | Fuente primaria y vinculante |
| 2 | Código real citado en las HU o en `umbrella/` | Confirma o desmiente la HU |
| 3 | Documentos técnicos y de visión del proyecto | Contexto y decisiones |
| 4 | Sesiones de transferencia | **Solo contexto complementario** para riesgos y ambigüedades |

Regla dura: **una sesión de transferencia no cierra una regla de negocio.** Sirve para detectar que falta una regla, no para fijarla. El caso "no cross subscriber validation special" es la prueba: se enunció verbalmente y su propio autor la retiró. `[C]`

## 8. Régimen de calidad por ámbito

Propuesta de resolución de la regla de calidad indeterminada. Las fuentes traían tres regímenes incompatibles: sin objetivo de cobertura (global, dispensado), ≥80% (sub-grupos 11 y 13), y regla de 3 componentes (sub-grupo 12). **No son alternativas: son ámbitos distintos.** (decisión H-7, 2026-09-23) `[P]`

| Ámbito | Qué es | Criterio de certificación | Herramienta | Estado |
|---|---|---|---|---|
| **A — Configuración de agente** | Skills, steering, prompts. No compila, no se despliega | **Regla de 3 componentes:** (1) skill configurado y funcional, (2) prompt reproducible, (3) artefacto resultante pasa la prueba funcional acordada. **La cobertura de código no aplica: no hay código que cubrir** | Revisión humana + corrida del piloto SISBEN con fixtures sintéticos | `[C]` base / `[P]` formato de la prueba del componente 3 `[A:OQ-RO-4]` |
| **B — Código y configuración generados** | Processors Scala, bloques HOCON, diccionarios JSON | Unit + Integration + End-to-end. **Cobertura ≥80% de las rutas de decisión del processor generado.** Especificaciones ejecutables: F8a para Validation y Acceptance; escenarios Gherkin de las HU 006/007/008 formalizados para Pinning, Synthesis y State Calculation | ScalaTest 3.2 con runner JUnit 4. **Arnés de integración propuesto:** Docker local (`Dockerfile` + `run_docker.sh`, `spark-submit --master local`) — cierra OQ-T-1 y OQ-T-04 localmente | `[P]` |
| **C — Utilidades auxiliares del framework** | Scripts Python o Bash, si llegan a existir | ≥80% de cobertura | pytest | `[P]` |

### 8.1 Reglas de no solapamiento `[P]`

- La cobertura ≥80% **no se exige al ámbito A**. Exigirla sería pedir cobertura de un artefacto declarativo.
- La regla de 3 componentes **no sustituye** las pruebas del ámbito B.
- Ambas son obligatorias **en su ámbito**. No son intercambiables ni opcionales.
- El TDD del pack global de la casa aplica a **B y C**. En el ámbito A, el equivalente de "prueba primero" es **definir el criterio de aceptación del artefacto antes de configurar el skill**.

### 8.2 Certificación de datos: distinta de la de código `[C]`

Confusión frecuente en el corpus, que aquí se separa explícitamente:

| Régimen | Qué certifica | Umbrales |
|---|---|---|
| **Calidad de código** (§8) | Los artefactos generados y la configuración de los skills | ≥80% cobertura (B, C) / regla de 3 componentes (A) |
| **Calidad de datos** | Los datos que procesa el pipeline | `qualityThreshold = 0.90` en Acceptance; **4 dimensiones** en State Calculation: completitud ≥90%, validez 100%, unicidad 0% duplicados, consistencia ≥90% |

Los umbrales de datos **no** son objetivos de cobertura, y los de cobertura **no** certifican datos. Nunca se citan como equivalentes.

### 8.3 Compuertas mínimas `[C]`

La integración con CI/CD está fuera del MVP. La compuerta mínima exigible es **revisión humana aprobada del artefacto generado**, que ya es parte del flujo de aprobación de cada skill.

Evidencia de prueba de la ingesta: **Zephyr**, práctica vigente de Experian y entregable exigido por las HU.

## 9. Fixtures y datos de prueba `[C]`

- **Fixtures sintéticos por defecto, siempre.** Ningún registro real entra a desarrollo.
- Todo fixture declara en su cabecera que es sintético.
- Los fixtures de SISBEN deben ejercitar: 22 campos de entrada, delimitador `;`, casos de menores de edad, encoding, duplicados por llave, códigos DIVIPOLA inexistentes, y las inconsistencias temporales de las reglas de consistencia.
- Los fixtures realistas **dependen de las listas de valores de los catálogos**, que son pre-condición pendiente `[A:DR-03]`.
- Prohibido derivar un fixture de un archivo real, ni siquiera anonimizándolo.

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Aprobación del régimen de calidad de §8 (los tres ámbitos) | `[P]` → requiere firma |
| 2 | Formato de la prueba funcional del componente 3 de la regla de 3 componentes | `[A:OQ-RO-4]` |
| 3 | Confirmación de Docker local como arnés de integración | `[A:OQ-T-1]` / `[A:OQ-T-04]` |
| 4 | Plantillas exactas de los `.md` del análisis (`contexto-ingesta.md`, `mapeo-campos.md`, dependencias, plan de ruta) | `[A:OQ-RO-2]` |
| 5 | Skill de los tipos T3 y T4, y faceta de análisis de T2 | `[PM]` |
| 6 | Listas de valores de los catálogos de SISBEN | `[A:DR-03]` |
| 7 | Dónde viven las pruebas de las ingestas ya certificadas | `[A:PT-10]` |
| 8 | Confirmación de tipos de prueba, cobertura, herramientas y compuertas a nivel global | `[A:OQ-T-05]` |

## Pie de trazabilidad

- Tipos de análisis y nomenclatura actual: decisión vacío-04 (2026-09-23); `12/vision` §Skill de Análisis.
- Metodología, completitud y manejo de ambigüedad: `12/vision` §Reglas y criterios, §Risks; `12/tech` §Artefactos de salida.
- Casos reales de ambigüedad: `12/vision` §Risks; `11/vision` §Known Risks; `13/vision` §Known Risks.
- Régimen de calidad: decisión H-7 (2026-09-23); `12/tech` §T8; `11/tech` §Testing; `13/tech` §Testing; `PD/tech` §Testing (dispensado, `[S]`).
- Certificación de datos: `13/tech` §Input Validation (4 dimensiones, HU-008); `PD/tech` §Input Validation.
- Jerarquía de evidencia: `13/tech` §Nota de alcance (orden de autoridad declarado).
- Última verificación de referencias: 2026-09-23.

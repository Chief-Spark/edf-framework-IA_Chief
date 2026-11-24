---
inclusion: always
---

# 05 — Convenciones documentales y de entregables

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-09-23 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md` |
| Naturaleza | **Normativo puro.** No se deriva de las fuentes: se decide. |
| Aprobador | PENDIENTE-MANUAL — sin designar (ver `06`, OQ-06) |

## 1. Objetivo y alcance

Que cualquier entregable de la iniciativa EDF sea reconocible, referenciable y comparable sin leerlo completo.

**Aplica a:** todo artefacto `.md`, `.json` y `.hocon` producido por un agente o por un humano dentro de esta iniciativa — steerings, análisis, mapeos de campos, expedientes de ingesta, bitácoras, propuestas de configuración.

**No aplica a:** las convenciones del código Scala/HOCON generado para el pipeline. Esas son externas e inmutables (convención del fork de Colombia) y se referencian desde `07-contratos-frontera.md`; este documento no las redefine.

## 2. Estructura documental obligatoria

Todo documento `.md` de la iniciativa lleva, en este orden:

1. **Front-matter** (solo steerings): `inclusion` y, si es `auto`, `name` y `description`.
2. **Título H1** con el número y el nombre del documento.
3. **Bloque de identidad** en tabla: Versión, Fecha, Estado, Precedencia, Fuentes, Aprobador.
4. **Objetivo y alcance** — qué cubre y qué no cubre, explícitamente.
5. **Cuerpo** en secciones numeradas.
6. **Pendientes de este documento** — lista de lo que falta, con el ID de la pregunta abierta.
7. **Pie de trazabilidad** — fuentes consultadas y fecha de última verificación de referencias.

Un documento sin bloque de identidad, sin fuentes o sin marcado de estado **no se acepta**.

## 3. Marcado de estado por afirmación

Toda afirmación factual en una tabla o lista lleva uno de estos cinco marcadores. Es el mecanismo que impide que un steering cierre por decreto algo que nunca fue aprobado.

| Marcador | Significado | Consecuencia operativa |
|---|---|---|
| `[C]` | CONFIRMADO por una fuente vinculante o por decisión humana registrada | Se puede construir sobre esto |
| `[P]` | PROPUESTO por un agente, sin aprobación humana | Se puede diseñar, **no** se puede certificar |
| `[A:<ID>]` | ABIERTO, con la pregunta abierta que lo bloquea | **Prohibido** generar artefactos que dependan de esto |
| `[S]` | STALE — la fuente existe pero fue superada por una decisión posterior | No citar como autoridad; citar la decisión que la superó |
| `[PM]` | PENDIENTE-MANUAL — el equipo debe completarlo a mano | Hueco declarado, no defecto |

Regla dura: **ninguna afirmación puede quedar sin marcador.** Si un agente no sabe qué marcador poner, pone `[A:sin-clasificar]` y lo escala.

## 4. Convenciones de nombres

### 4.1 Archivos y carpetas

- Formato: `NN-nombre-en-kebab-case.md` para steerings; `nombre-en-kebab-case.md` para el resto.
- **Prohibidos:** espacios, tildes, eñes, mayúsculas y sufijos de copia (`… 1.md`, `… (2).md`, `copia de …`).
- Prefijo numérico de dos dígitos solo cuando el orden de lectura importa.
- Un número, una carpeta. **Prohibido** reutilizar el mismo prefijo en dos carpetas hermanas.

### 4.2 Nombres de sub-grupo de trabajo

Las carpetas `11_ingesta_limpieza`, `12_requerimientos_y_orquestador` y `13_transformadores` son **nomenclatura interna de sub-grupos de trabajo del equipo de construcción**, no taxonomía del pipeline. `[C]`

| Sub-grupo | Carpeta | Etapas que cubre |
|---|---|---|
| Ingesta y Limpieza | `11_ingesta_limpieza` | Validation, Acceptance, Curation |
| Requerimientos y Orquestador | `12_requerimientos_y_orquestador` | Análisis de Requerimiento (etapa 0), Orquestador |
| Transformes | `13_transformadores` | Pinning, Synthesis/Consistency, State Calculation |

Regla dura: **la taxonomía del pipeline nunca se deriva del nombre de la carpeta.** Se deriva exclusivamente de `03-pipeline.md`.

### 4.3 Identificadores de pregunta abierta

Los IDs de pregunta abierta **colisionan hoy** entre sub-grupos: `OQ-B-1` y `OQ-T-1` significan cosas distintas en `11_ingesta_limpieza` y en `13_transformadores`. `[C]`

Convención obligatoria desde esta versión: **prefijo de sub-grupo**.

- `OQ-IL-<N>` — Ingesta y Limpieza
- `OQ-RO-<N>` — Requerimientos y Orquestador
- `OQ-TR-<N>` — Transformes
- Sin prefijo (`OQ-NN`, `OQ-T-NN`, `PT-NN`, `FINDING-NN`, `CQ-NNN`, `GAP-NN`) — reservado al registro global `Product-Definition/open-questions.md`, cuya numeración histórica **no se renumera**.

## 5. Versionado y estado del documento

- Versión semántica `MAYOR.MENOR.PARCHE`: MAYOR cambia el perímetro o una restricción dura; MENOR agrega secciones o afirmaciones; PARCHE corrige redacción o referencias.
- Estados: `PROPUESTO` → `EN REVISIÓN` → `APROBADO` → `OBSOLETO`.
- Un documento superado **no se borra**: se marca `OBSOLETO` en el bloque de identidad, con puntero al que lo reemplaza.
- Todo cambio en un steering `APROBADO` exige aprobación humana registrada en la bitácora (`09-estado-y-trazabilidad.md`).

## 6. Trazabilidad y citas

Formato obligatorio de cita, según el tipo de fuente:

| Tipo | Formato | Ejemplo |
|---|---|---|
| Documento | `archivo §sección` | `HU-SISBEN-008 §6.1` |
| Sesión de transferencia | `[<código> \| hh:mm:ss]` | `[A2 \| 00:24:29]` |
| Decisión del equipo | `(decisión <ID>, <fecha>)` | `(decisión H-5, 2026-09-23)` |
| Código | `ruta/archivo::símbolo` | `umbrella/oxygen-curation/.../CurationApp.scala::main` |

Regla dura: **toda afirmación `[C]` lleva cita.** Una afirmación sin cita no puede ser `[C]`.

## 7. Referencias cruzadas

- Usar `#[[file:<ruta-relativa-al-workspace>]]`. Rutas relativas, nunca absolutas.
- **Prohibido copiar** contenido de otro documento: cifras, fechas, listas de alcance y tablas de etapas se referencian, no se duplican. La duplicación es la causa raíz de la desincronización ya registrada en el proyecto.
- **Prohibido citar rutas que no existan.** Toda referencia se verifica antes de publicar; el pie de trazabilidad registra la fecha de esa verificación.
- Las referencias rotas conocidas y su destino corregido viven en `.kiro/limpieza-y-vacios.md`.

## 8. Formato de tablas

- Una fila = una afirmación. Nunca dos hechos en una celda.
- **Excepción explícita: tablas en formato de contrato** (hoy solo la tabla de `mapeo-campos.md` de la etapa 0, plantilla `.kiro/skills/edf-analisis/assets/templates/mapeo-campos.md.tpl`). Una fila = un campo con sus nueve dimensiones (`07-contratos-frontera.md` §3), y una celda puede juntar los hechos de una misma dimensión (tipo en origen → destino; obligatoriedad y qué pasa si falta). Cada hecho de la celda que no sea `[C]` lleva su propio marcador junto a él (`[A:<ID>]`, `[PM]`), y la columna `Estado` resume la fila: `[C]` solo si todas las dimensiones son `[C]`; si no, enumera los marcadores abiertos. No se aplica a ninguna otra tabla.
- Toda tabla factual lleva las columnas `Estado` (marcador de §3) y `Fuente`.
- Tablas de decisión llevan además `Fecha` y `Quién decidió`.
- Sin celdas vacías: usar `—` (no aplica) o `[PM]` (pendiente manual). La celda vacía es ambigua entre las dos.

## 9. Reglas de redacción

- Español. Voz activa. Frases cortas.
- Sin superlativos ni hipérbole. Se muestra el dato, no se califica.
- Expansión obligatoria de sigla en primer uso por documento, con la forma canónica de `02-lexico-edf.md`.
- **Prohibido colapsar una decisión abierta en una afirmación.** Si el delimitador o la cobertura están abiertos, se escribe abierto, no se elige el más probable.
- Regla vinculante heredada: los tres ejes de capacidad **nunca** se colapsan en una sola frase. La capacidad de generación y la capacidad demostrada son afirmaciones distintas y se escriben separadas.
- No publicar cifras de reducción porcentual fuera del equipo mientras la línea base siga sin instrumentar (OQ-01).

## 10. Tamaño y cohesión

- 200-400 líneas típico. **800 líneas máximo.**
- Al exceder el máximo, el documento se parte por concern, no por tamaño arbitrario.
- Un documento = un propósito. Si el título necesita una "y" que une dos dominios, probablemente son dos documentos.

## 11. Requisitos mínimos de documentación por entregable

| Entregable | Mínimo exigido |
|---|---|
| Análisis de requerimiento | Bloque de identidad + mapeo de **todos** los campos + dependencias + preguntas abiertas + citas |
| Propuesta de código/configuración | Artefacto + justificación + cita del patrón de referencia + prueba asociada + riesgos |
| Expediente de ingesta | Estado por etapa + artefactos + aprobaciones + riesgos (ver `09`) |
| Bitácora | Qué / quién / cuándo / por qué / resultado, append-only (ver `09`) |
| Steering | Este documento, completo |

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Aprobador designado de los steerings | `[A:OQ-06]` |
| 2 | Migración de los IDs `OQ-B-*` / `OQ-T-*` de los sub-grupos a la convención `OQ-IL/RO/TR-*` | `[PM]` |
| 3 | Política de retención de la bitácora | `[A:OQ-RO-3]` |
| 4 | Fuentes faltantes y referencias rotas del corpus | ver `.kiro/limpieza-y-vacios.md` |

## Pie de trazabilidad

- Fuentes: decisiones del equipo del 2026-09-23 (H-1 a H-14, M-1 a M-8); `Product-Definition/open-questions.md` §Summary.
- Naturaleza normativa: este documento no requiere evidencia en las fuentes; requiere aprobación.
- Última verificación de referencias: 2026-09-23.

---
inclusion: auto
name: Estado y trazabilidad EDF
description: Expediente de la ingesta, esquema de estado, bitacora auditable append-only y reglas de bloqueo de avance entre etapas en la iniciativa EDF. Activar al registrar avance, pedir o registrar una aprobacion, consultar el estado de una ingesta, o auditar que se hizo y quien lo aprobo.
---

# 09 — Estado y trazabilidad

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-09-23 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`; niveles de riesgo y aprobadores en `06` |
| Fuentes | `12/tech` §T4 §T7, `12/vision` §Skill Orquestador, `PD/vision` N3 |
| Aprobador | PENDIENTE-MANUAL |

## 1. Objetivo y alcance

Que en cualquier momento se pueda responder, con evidencia: en qué etapa está una ingesta, qué artefactos existen, quién aprobó qué, y por qué.

**Cubre:** el expediente de la ingesta, el esquema de estado, la bitácora auditable y las reglas de bloqueo de avance.
**No cubre:** el estado del pipeline en AWS, que se materializa como movimiento de archivos en S3 y se describe en `03-pipeline.md` §6. El expediente **refleja** ese avance; no lo controla.

Restricción dura de origen: **bitácora auditable obligatoria** por cumplimiento de Experian (N3). `[C]`

## 2. El expediente de la ingesta

El Orquestador mantiene el expediente. Es la fuente de verdad del avance del trabajo y **sobrevive entre sesiones**. `[C]`

| Artefacto | Formato | Propósito | Consumidor |
|---|---|---|---|
| `estado-ingesta.md` | Markdown | Vista legible: etapa actual, artefactos, aprobaciones, riesgos | Desarrollador |
| Estado estructurado | **JSON con esquema** | Representación consumible por los agentes | Agentes del framework |
| `bitacora.md` | Markdown **append-only** | Auditoría de cada interacción y cada aprobación | PO / Auditoría |
| `contexto-ingesta.md` | Markdown | Contexto de la fuente, salida de la etapa 0 | Desarrollador, Orquestador |
| `mapeo-campos.md` | Markdown | Mapeo de **todos** los campos | Desarrollador, etapas siguientes |
| Dependencias y plan de ruta | Markdown | Insumo de planificación | Orquestador |

Sin bases de datos ni almacenes externos: **todo son archivos en el workspace del desarrollador** (`01` §2, Z1). `[C]`

## 3. Esquema del estado estructurado `[P]`

**Esquema vigente en el framework:** `.kiro/skills/edf-orquestador/templates/estado-ingesta.schema.json` (versión 1.1.0, JSON Schema 2020-12). Implementa la propuesta de esta sección con nombres en `snake_case`, identificadores de etapa en texto (`00-framework-edf.md` §2) y riesgo según la correspondencia de `00` §5. Lo valida `tools/validar-expediente.py`, y el hook `validar-expediente` lo ejecuta al guardar. Sigue `[P]` hasta la firma de `[A:OQ-RO-1]`.

Preguntas abiertas en el expediente `[P]`: las preguntas **propias de una ingesta** (p. ej. "¿`fec_digitacion` es numérico?") se registran en `preguntas_abiertas[]` del expediente con ID `PA-nnn`. Las preguntas **del framework o de la iniciativa** no se copian: se referencian por su ID del registro único (§0.4 de `01`). Esta separación requiere confirmación del aprobador del pack.

La tabla siguiente es la propuesta original, conservada como referencia de diseño.

| Campo | Tipo | Obligatorio | Nota |
|---|---|---|---|
| `schemaVersion` | string | Sí | Versionado del propio esquema |
| `ingesta` | string | Sí | Nombre de la ingesta-fuente |
| `etapaActual` | enum 0-6 | Sí | Según la tabla de equivalencias de `02` §2 |
| `contratoArchivo` | objeto | Sí | Las seis dimensiones de `07` §2 |
| `etapas[]` | lista | Sí | Una entrada por etapa |
| `etapas[].id` | enum 0-6 | Sí | — |
| `etapas[].estado` | enum | Sí | `no-iniciada` / `en-analisis` / `propuesta` / `aprobada` / `bloqueada` |
| `etapas[].artefactos[]` | lista de rutas | Sí | Rutas relativas al workspace |
| `etapas[].aprobacion` | objeto o `null` | Sí | `{quien, cuando, nivelRiesgo, referenciaBitacora}` |
| `etapas[].nivelRiesgo` | enum N1-N4 | Sí | Según `06` §2 |
| `etapas[].bloqueadaPor[]` | lista de IDs | Sí | IDs de pregunta abierta; vacía si no hay bloqueo |
| `preguntasAbiertas[]` | lista de IDs | Sí | Referencia al registro único; **no se duplica el contenido** |
| `supuestos[]` | lista | Sí | Formato de `04` §6 |
| `riesgos[]` | lista | Sí | Con nivel y dueño |
| `ultimaActualizacion` | timestamp | Sí | — |

Reglas del esquema:

- **Sin PII en ningún campo** (`08` S9).
- Las preguntas abiertas se referencian **por ID**, nunca se copian: el registro único es `Product-Definition/open-questions.md` (`01` §0.4).
- El estado se actualiza en la misma operación que produce el artefacto, no después.
- `schemaVersion` cambia cuando cambia la estructura, y el cambio se registra en la bitácora.

## 4. Bitácora auditable `[C]`

**Formato:** Markdown, **append-only**. Nunca se edita ni se borra una entrada previa. Una corrección se escribe como entrada nueva que referencia la anterior.

**Contenido obligatorio por evento** — los cinco campos, siempre:

| Campo | Contenido |
|---|---|
| **Qué** | La acción o la decisión |
| **Quién** | Agente o persona, nominal |
| **Cuándo** | Timestamp |
| **Por qué** | La razón, con cita de la fuente o del ID de decisión |
| **Resultado** | Aprobado / rechazado / bloqueado / ejecutado, y qué produjo |

**Eventos que se registran obligatoriamente:**

- Toda interacción que produce o modifica un artefacto.
- Toda aprobación y todo rechazo, con el nivel de riesgo clasificado.
- Todo bloqueo de avance, con el ID que lo bloquea.
- Todo escalamiento, con la ruta usada (`06` §5).
- Todo hallazgo de seguridad, **sin el valor** (`08` §6).
- Toda clasificación de riesgo N3 o N4.
- Todo cambio a un steering `APROBADO`.
- Todo supuesto declarado y todo supuesto convertido en confirmado.

**Prohibido en la bitácora:** PII, valores de secretos, datos reales, y entrada externa interpolada sin sanitizar (`08` O3).

### 4.1 Formato de entrada sugerido `[P]`

```
### <timestamp> — <tipo de evento>
- **Qué:** ...
- **Quién:** ...
- **Por qué:** ... (cita o ID de decisión)
- **Riesgo:** N<1-4>
- **Resultado:** ...
- **Artefactos:** ruta/relativa/...
- **Bloqueado por:** OQ-... | —
```

### 4.2 Retención `[A:OQ-RO-3]`

La política de retención de la bitácora **no está definida**. Pendiente de alinearse con la política de Experian.

Aclaración necesaria porque el corpus las confunde: la **retención máxima de 6 meses** que consta en las fuentes es de **datos en S3**, no de la bitácora. No se citan como equivalentes.

## 5. Bloqueo de avance entre etapas

El Orquestador **exige aprobación entre cada par de etapas** y bloquea el avance sin aprobación registrada. `[C]`

### 5.1 Condiciones de bloqueo `[C]`

Una etapa queda `bloqueada` y no se habilita la siguiente si:

1. La aprobación de la etapa actual no está registrada en la bitácora.
2. Un artefacto de la etapa depende de una afirmación `[A:<ID>]` cuya pregunta abierta (`<ID>`) es bloqueante y tiene `etapa_afectada` igual a esa etapa (o a la siguiente que se quiere habilitar). Un `[A:<ID>]` cuya pregunta afecta a una etapa posterior bloquea esa etapa, no la actual; un `[A:<ID>]` sin pregunta registrada en el expediente bloquea siempre (registrarla primero). Mismo criterio que la condición 4 de `edf-orquestador/references/03` §3.
3. La clasificación de riesgo es N4 y no hay escalamiento resuelto (`06` §3).
4. El checklist de cierre del análisis tiene casillas vacías (`04` §4.3).
5. La lista de verificación de seguridad tiene casillas vacías (`08` §8).
6. Un supuesto con consecuencia irreversible sigue sin confirmar (`04` §6).
7. Hay un hallazgo de seguridad sin escalar.

Regla dura: **el bloqueo no se levanta por antigüedad, por presión de cronograma ni por ausencia de objeción.** Se levanta con una aprobación registrada.

### 5.2 La etapa 0 es compuerta `[C]`

Compuerta de entrada: La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene. La hace cumplir el hook `.kiro/hooks/compuerta-etapa0.json` (`tools/verificar-insumos-etapa0.py`); la casilla de insumos del checklist de cierre está en `04` §4.3.

El análisis de la etapa 0 se presenta y **espera aprobación** antes de que el Orquestador habilite la etapa 1. No es una recomendación: es la primera compuerta del flujo.

### 5.3 Correspondencia con el avance real `[C]`

El expediente debe reflejar el avance real del pipeline, que se materializa como movimiento de archivos en S3 (`03` §6). Si el expediente y S3 discrepan, **gana S3** y la discrepancia se registra como incidente en la bitácora.

El Orquestador **lee** ese estado; no lo produce, no mueve archivos y no dispara DAGs (`01` §2, Z3).

## 6. Certificación de un skill `[C]`

La **regla de 3 componentes** se registra en el expediente como evidencia de certificación (`04` §8, ámbito A):

| Componente | Evidencia que va al expediente |
|---|---|
| 1. Skill configurado | Ruta de la definición del skill y del steering asociado |
| 2. Prompt reproducible | El prompt exacto y la referencia a la corrida |
| 3. Artefacto que pasa la prueba | El artefacto, la prueba y su resultado |

Caso de prueba: piloto **SISBEN**, con **fixtures sintéticos**. El formato exacto de la prueba del componente 3 sigue abierto `[A:OQ-RO-4]`.

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Firma del esquema JSON vigente (`estado-ingesta.schema.json` 1.1.0) y su relación con `estado-ingesta.md` | `[A:OQ-RO-1]` — implementado, pendiente de firma |
| 2 | Política de retención de la bitácora | `[A:OQ-RO-3]` |
| 3 | Formato de la prueba funcional del componente 3 | `[A:OQ-RO-4]` |
| 4 | Plantillas exactas de los `.md` del análisis | `[A:OQ-RO-2]` |
| 5 | Aprobación del esquema propuesto en §3 | `[P]` → requiere firma |

## Pie de trazabilidad

- Expediente, esquema y bitácora: `12/tech` §Estado/expediente (T4), §Auditoría/Bitácora (T7), §Data Patterns.
- Bloqueo de avance y compuerta de la etapa 0: `12/vision` §MVP Scope, §Human-in-the-Loop.
- Bitácora obligatoria: `PD/vision` N3.
- Regla de 3 componentes: `12/tech` §Testing (T8).
- Avance real por movimiento en S3: `12/tech` §Estado/expediente; `03-pipeline.md` §6.
- Retención de datos en S3 (no de la bitácora): `PD/tech` §Compliance.
- Última verificación de referencias: 2026-09-23.

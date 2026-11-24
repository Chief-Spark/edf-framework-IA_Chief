# Flujo de etapas EDF — conocimiento operativo del Orquestador

Subordinado a `.kiro/steering/00-framework-edf.md` §2 (modelo canónico de etapas) y a `03-pipeline.md`.
Trazabilidad: `requirements-orquestador.md` R1 · `hallazgos-sesiones.md` §5–§7 · `arquitectura_framework.md` §10 · decisiones H-3 y H-4.

## 1. Las etapas y la frontera del pipeline configurado

El framework configura **6 etapas** del pipeline más la **etapa 0** de análisis, que es habilitador y no procesa datos. Data Reception y Preparation **no** son etapas del framework (H-3, H-4): el expediente no las modela.

| # | Etapa | ID | ¿En el pipeline configurado? | DAG de Airflow | Riesgo típico (`06`) | Compromiso |
|---|-------|----|------------------------------|----------------|---------------|------------|
| 0 | Análisis de Requerimiento y Contexto | `analisis` | No (habilitador) | — | N1 · `bajo` (solo `.md`) | Habilitador |
| 1 | Validation | `validation` | Sí | `daily-validation` | N2 · `medio` (mínimo; la regla del caso puede subirlo) | Aceptación |
| 2 | Acceptance | `acceptance` | Sí | `acceptance-curation-flow` | N2 · `medio`; N3 si cambia el umbral de calidad | Aceptación |
| 3 | Curation — Flow | `curation_flow` | Sí | `acceptance-curation-flow` | N2 · `medio` | Éxito |
| 3b | Curation — Stock | `curation_stock` | Sí | `collection-stock` | N2 · `medio` | Éxito |
| 4 | Pinning | `pinning` | Sí | `state-generation` | N3 · `alto` | Éxito |
| 5 | Synthesis / Consistency | `synthesis` | Sí | `state-generation` | N3 · `alto` | Éxito |
| 6 | State Calculation | `state_calculation` | Sí | `state-generation` | **N4 · `critico`** (clasificación fija, `06` §2.2) | Éxito |

> Curation se modela como **dos entradas** (`curation_flow` y `curation_stock`) porque corren en DAGs distintos y con alcances distintos: Flow procesa solo la fuente en curso; Stock procesa todo lo acumulado en `accepted/` de todas las fuentes [DEMO | 00:13:59]. Un mismo processor de `edf-curation` sirve a los dos modos (`runMode` lo fija la orquestación).

## 2. Orden con dependencias (R1.2)

```
analisis (gate 0)
   └─> validation ──> acceptance ──┬─> curation_flow ──┐
                                   └─> curation_stock ─┴─> pinning ──> synthesis ──> state_calculation
                                                                                         │
                                                                       (fuera del pipeline) └─> hidratación HBase
```

Reglas de dependencia que el Orquestador **debe** verificar antes de habilitar una transición (R1.4):

- `validation` requiere `analisis` **aprobada** (contexto cerrado, sin preguntas bloqueantes abiertas con `etapa_afectada` ∈ {`analisis`, `validation`}) y el insumo `insumos/source-spec-validation.yaml` aprobado.
- `acceptance` requiere `validation` aprobada.
- `curation_flow` y `curation_stock` requieren `acceptance` aprobada.
- `pinning` requiere `curation_flow` **y** `curation_stock` aprobadas.
- `synthesis` requiere `pinning` aprobada.
- `state_calculation` requiere `synthesis` aprobada.

Cada etapa necesita además su **insumo aprobado** producido por `edf-analisis` (ver `04-contratos-por-skill.md`). Una pregunta bloqueante abierta detiene solo su `etapa_afectada`: el insumo, la generación, la aprobación y la transición hacia esa etapa (`03-gobierno-riesgo-aprobacion.md` §3, condición 4). No detiene las etapas anteriores.

## 3. Lo que queda FUERA (el Orquestador lo reconoce y lo rechaza como trabajo propio)

| Fuera de alcance | Evidencia |
|------------------|-----------|
| Data Reception y Preparation / prevalidación (AVRO→JSON) | H-3; [CUR \| 00:02:14]; "preparación ya no hace parte del pipeline" [A2 \| 00:24:29] |
| Hidratación a HBase (automática, 2–3 h tras State Calculation) | [A1 \| 00:06:04, 00:05:17] |
| Airflow y sus 4 DAGs — ya implementados, **no se modifican** | [A2 \| 00:38:47] |
| Validación de cédula contra Registraduría / Migración (posterior al pipeline) | [VAL \| 00:04:38] |
| Archivos RT (reestructuración total) y CUPES (eliminación parcial) → ingesta aparte | [A1 \| 00:23:29] |
| High frequency (el alcance es solo high throughput / batch) | §10 hallazgos |
| Los 3 forks de reportes (report generation, subscriber WS, frontend DOT) | §10 hallazgos |

## 4. Los 4 DAGs de Airflow (contexto, solo lectura)

| # | DAG | Hora aprox. | Cubre | Duración |
|---|-----|-------------|-------|----------|
| 1 | Daily Validation | ~08:00 | `validation` | 30–50 min |
| 2 | Acceptance + Curation Flow | ~10:00 | `acceptance`, `curation_flow` | 30–50 min |
| 3 | Collection Stock | — | `curation_stock` | 30–50 min |
| 4 | State Generation | ~14:00 | `pinning`, `synthesis`, `state_calculation` | 2–6 h |

Ejecución **secuencial con verificación manual entre DAGs** [DEMO | 00:02:45] → cada frontera de DAG es un punto natural de aprobación humana. El Orquestador **no dispara** ningún DAG (R5.1).

## 5. Contratos entre etapas y evidencia en S3 (R2.4)

El estado del pipeline real **no vive en una base de datos: se materializa por movimiento de archivos entre carpetas de S3**. El expediente debe reflejar ese avance.

| Etapa | Entrada | Salida / ruta de evidencia | Señal de avance |
|-------|---------|----------------------------|-----------------|
| `validation` | Archivo crudo en `Colombia Data/<Fuente>` (carga manual al bucket [DEMO \| 00:05:40]) | `validation/validated/` | **Si un archivo QUEDA en `validated/` es que tuvo error** [DEMO \| 00:08:13] |
| `acceptance` | Eventos validados | `acceptance/accepted/` (**permanece**, no se mueve [DEMO \| 00:10:06]), `acceptance/rejects/files`, `acceptance/rejects/records` | Aparición en `accepted/` + conteos de rechazo |
| `curation_flow` | Salida de Acceptance (solo la fuente en curso) | `curation/curated/` (`curatedDir/flow`) + marcador `ready-curate` | Marcador `ready-curate` presente |
| `curation_stock` | Todo lo acumulado en `accepted/` (todas las fuentes) | `curatedDir/stock` | — |
| `pinning` | `curatedDir/*/*` (contrato en steering `11`) | Carpeta de **generación** nombrada por época EPOC | Época EPOC **activada** (se crea como candidato y no se activa hasta ejecutar Pinning [DEMO \| 00:15:02]) |
| `synthesis` | Salida de Pinning | Misma carpeta de generación (EPOC) | Artefactos de consistency en la carpeta EPOC |
| `state_calculation` | Salida de Synthesis | Misma carpeta de generación (EPOC) → XPM | XPM consultable por Data Provisioning (CA1) |

Otros anclajes de evidencia:
- **Logs**: `JobLogs/<fecha>` [DEMO | 00:08:47].
- **Ejecuciones concurrentes**: distinguir por **cluster/step**, no solo por fecha [DEMO | 00:11:16]. El expediente registra `cluster_id` / `step_id` cuando se conozca.
- **`reusePIN`**: `true` en operación normal; **`false` para una ingesta NUEVA** [DEMO | 00:26:46]. Es un campo obligatorio del expediente.

## 6. Cómo registra el Orquestador la evidencia

El Orquestador **no lista S3 ni consulta Airflow por sí mismo**. La evidencia la aporta el Desarrollador (captura, ruta, conteo) y el Orquestador la transcribe al expediente con su origen:

```
evidencia_pipeline: { ruta_s3_esperada, observada: <lo reportado|null>, reportada_por: "Desarrollador", verificada_en: <ISO8601>, marcador, joblog, cluster_id, step_id }
```

Si no hay evidencia, el campo queda `null` y la etapa **no** puede marcarse aprobada por avance de pipeline, salvo que el Desarrollador la apruebe con **evidencia diferida** (`references/03` §3b). Si el expediente y S3 discrepan, gana S3 y la discrepancia se registra como incidente (`09` §5.3).

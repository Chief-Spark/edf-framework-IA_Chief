<!--
  VISTA DERIVADA — NO EDITAR A MANO.
  Generada por el Skill Orquestador desde estado-ingesta.json (fuente de verdad, R2.5).
  Si editas este archivo, el Orquestador lo sobrescribe en la siguiente regeneración.
  Prohibido escribir valores de campos PII en este documento (GR-6 / R4.3).
-->

# Expediente de ingesta — {{ingesta.nombre}}

| | |
|---|---|
| **ID del expediente** | `{{ingesta.id}}` |
| **Etapa actual** | **{{etapa_actual}}** |
| **Estado de la etapa actual** | {{etapa_actual.estado}} |
| **Avance** | {{n_aprobadas}} / 8 entradas de etapa aprobadas |
| **Ingesta nueva** | {{ingesta.es_ingesta_nueva}} · `reusePIN = {{ingesta.reuse_pin}}` |
| **Fuente oficial** | {{ingesta.fuente_oficial}} |
| **Business Light Code** | {{ingesta.business_light_code}} |
| **Época EPOC** | {{ingesta.epoca_epoc}} (activada: {{ingesta.epoca_activada}}) |
| **Período** | {{ingesta.periodo}} |
| **Creado** | {{ingesta.creada_en}} |
| **Última actualización** | {{generado_por.actualizado_en}} |
| **Bitácora** | [`bitacora.md`](bitacora.md) — {{bitacora.eventos_totales}} eventos, último {{bitacora.ultimo_evento}} |

> ⛔ **{{n_bloqueantes}} pregunta(s) abierta(s) bloqueante(s).** El avance está detenido hasta resolverlas.

## 1. Contrato del archivo

| Atributo | Valor |
|----------|-------|
| Patrón de nombre | `{{contrato.patron_nombre}}` |
| Delimitador | `{{contrato.delimitador}}` |
| Encoding | {{contrato.encoding}} |
| Header / Footer | {{contrato.header}} / {{contrato.footer}} |
| Event type | {{contrato.event_type}} |
| Columnas | {{contrato.num_columnas}} |

## 2. Avance por etapa

| # | Etapa | En pipeline | DAG | Compromiso | Estado | Riesgo | Artefactos aprob. | Aprobación | Transición |
|:-:|-------|:-----------:|-----|------------|--------|:------:|:-----------------:|------------|:----------:|
| 0 | Análisis | — | — | habilitador | {{estado}} | {{riesgo}} | 0/4 | — | 🔒 |
| 1 | Validation | ✅ | daily-validation | aceptación | {{estado}} | {{riesgo}} | 0/6 | — | 🔒 |
| 2 | Acceptance | ✅ | acceptance-curation-flow | aceptación | {{estado}} | {{riesgo}} | 0/4 | — | 🔒 |
| 3 | Curation Flow | ✅ | acceptance-curation-flow | éxito | {{estado}} | {{riesgo}} | 0/5 | — | 🔒 |
| 3b | Curation Stock | ✅ | collection-stock | éxito | {{estado}} | {{riesgo}} | 0/1 | — | 🔒 |
| 4 | Pinning | ✅ | state-generation | éxito | {{estado}} | {{riesgo}} | 0/4 | — | 🔒 |
| 5 | Synthesis | ✅ | state-generation | éxito | {{estado}} | {{riesgo}} | 0/3 | — | 🔒 |
| 6 | State Calculation | ✅ | state-generation | éxito | {{estado}} | {{riesgo}} | 0/4 | — | 🔒 |

Cada etapa incluye su **insumo** de `edf-analisis` como primer artefacto obligatorio.

Leyenda de transición: 🔒 bloqueada · ✅ habilitada · ⏸ pendiente de aprobación · ✋ completada manualmente.

## 3. Checklist de bloqueo de la etapa actual

Evaluado {{checklist.evaluado_en}} → **{{checklist.resultado}}**

- [{{c1}}] 1. Etapa previa `aprobada` o `completada_manualmente`
- [{{c2}}] 2. Aprobación registrada que referencia esta transición
- [{{c3}}] 3. Dependencias declaradas satisfechas
- [{{c4}}] 4. Sin preguntas abiertas bloqueantes con `etapa_afectada` en la etapa previa o en esta
- [{{c5}}] 5. Evidencia de pipeline observada (si la transición la exige)
- [{{c6}}] 6. Justificación presente si el riesgo es alto o crítico (en crítico, aprobado por PO o Aprobador designado)

Condiciones fallidas: {{checklist.condiciones_fallidas}}

## 4. Artefactos por etapa

### {{etapa.nombre}}
| ID | Artefacto | Tipo | Oblig. | Estado | Riesgo | Prevalidación |
|----|-----------|------|:------:|--------|:------:|---------------|
| {{art.id}} | {{art.nombre}} | {{art.tipo}} | {{art.obligatorio}} | {{art.estado_artefacto}} | {{art.riesgo}} | compila={{..}} lint={{..}} |

## 5. Evidencia del pipeline (solo lectura, reportada por el Desarrollador)

| Etapa | Ruta esperada | Observada | Marcador | JobLog | Cluster/Step | Reportada por | Verificada |
|-------|---------------|-----------|----------|--------|--------------|---------------|------------|
| {{etapa}} | `{{ruta_s3_esperada}}` | {{observada \| "— no verificada"}} | {{marcador}} | {{joblog}} | {{cluster}}/{{step}} | {{rol+id}} | {{verificada_en}} |

> Recordatorio: si un archivo **queda** en `validation/validated/` es que tuvo error. `acceptance/accepted/` **permanece**, no se mueve.

## 6. Aprobaciones registradas

| ID | Tipo | Transición | Decisión | Riesgo | Fricción | Actor (rol · id) | Cuándo | Resultado | Bitácora |
|----|------|-----------|----------|:------:|----------|------------------|--------|-----------|----------|
| {{APR-nnn}} | {{tipo}} | {{desde}} → {{hacia}} | {{decision}} | {{riesgo}} | {{friccion}} | {{rol}} · `{{id}}` | {{ts}} | {{resultado}} | {{BIT-nnnn}} |

_Sin aprobaciones registradas: ninguna transición está habilitada._

## 7. Preguntas abiertas

| ID | Pregunta | Etapa | Bloqueante | Estado | Origen | Respuesta |
|----|----------|-------|:----------:|--------|--------|-----------|
| {{PA-nnn}} | {{pregunta}} | {{etapa}} | {{sí/no}} | {{estado}} | {{origen}} | {{respuesta \| "—"}} |

## 8. Riesgos

| ID | Riesgo | Nivel | Etapa | Mitigación | Estado |
|----|--------|:-----:|-------|------------|--------|
| {{RG-nnn}} | {{descripcion}} | {{nivel}} | {{etapa}} | {{mitigacion}} | {{estado}} |

## 9. Backlog evolutivo (regla de corte)

| ID | Etapa | Pendiente | Motivo | Aprobación | Creado |
|----|-------|-----------|--------|------------|--------|
| {{BKL-nnn}} | {{etapa}} | {{pendiente}} | {{motivo}} | {{APR-nnn}} | {{creado_en}} |

_Vacío: ninguna etapa se marcó como completada manualmente._

---
_Vista generada por `edf-orquestador` v{{generado_por.version}} desde `estado-ingesta.json` (schema {{schema_version}}) el {{generado_por.vista_md_regenerada_en}}._

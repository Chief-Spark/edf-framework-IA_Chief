# Design — Skill Orquestador
## EXP-023 "Célula para Ingestas" (KD-EXP-034)

> Fase AI-DLC: **Construction / diseño funcional**. Insumos: `requirements-orquestador.md` (R1–R7), `vision-document.md`, `technical-environment.md`, `especificacion_requerimientos_skills.md` (G3, G4), `hallazgos-sesiones.md`.
> Este documento **cierra las preguntas abiertas** OQ-1/OQ-T-1, OQ-T-3 y OQ-T-4.

---

## 1. Naturaleza del componente

Agente **puramente configurado** en Kiro: `SKILL.md` + steering + prompts + templates. No compila, no se despliega, no expone API. Corre local en la máquina del Desarrollador. Toda su lógica vive en configuración declarativa.

```
.kiro/                              (en el repo: `kiro/`, visible; ver README)
├── steering/                       ← steering unificado del framework (00–11)
├── skills/edf-orquestador/
│   ├── SKILL.md                    ← definición del agente
│   ├── references/                 ← conocimiento operativo (antes era steering propio)
│   │   ├── 00-principio-rector.md          invariantes, roles, fuentes de verdad
│   │   ├── 01-flujo-etapas.md              8 entradas de etapa, dependencias, DAGs, evidencia S3
│   │   ├── 02-guardrails-perimetro.md      GR-1..GR-9 + protocolo de rechazo + higiene PII
│   │   ├── 03-gobierno-riesgo-aprobacion.md riesgo N1–N4, fricción, checklist de bloqueo, regla de corte, bitácora
│   │   └── 04-contratos-por-skill.md       insumo y artefactos que exige cada etapa antes de aprobar
│   ├── prompts/00..07              ← componente ② de la certificación
│   └── templates/                  ← esquema + seed + plantillas de salida
├── hooks/validar-expediente.json   ← corre tools/validar-expedientes.py tras escribir el expediente
└── specs/orquestador/              ← este design, tasks, certificación, trazabilidad
```

## 2. Modelo de etapas: etapa 0 + 6 etapas, 8 entradas de expediente

Versión 1.1.0. Por decisiones H-3 y H-4 (steering `02` §2, `03` §2), Data Reception y Preparation **no** son etapas del framework. El expediente usa **8 entradas**:

- **`analisis` (orden 0)** es el habilitador de etapa 0: no procesa datos, pero es el gate que todo lo precede, y necesita estado y aprobación propios.
- **6 etapas del pipeline**: `validation`, `acceptance`, Curation, `pinning`, `synthesis`, `state_calculation`.
- **Curation se parte en `curation_flow` y `curation_stock`**: corren en DAGs distintos (`acceptance-curation-flow` vs `collection-stock`) con alcances distintos (la fuente en curso vs todo lo acumulado en `accepted/`), y `pinning` depende de **ambas**. Modelarlas como una sola etapa haría imposible expresar esa dependencia.

**Decisión de diseño (D-1):** `en_pipeline_configurado` es `false` solo para `analisis`: el pipeline configurado va de Validation a State Calculation (R1.3).

> Historial: la versión 1.0.0 modelaba 9 entradas, incluida `reception`. Se retiró al unificar el steering del framework (ver `CHANGELOG.md` del repo).

## 3. OQ-T-1 — Esquema del estado JSON (CERRADO)

Archivo: `templates/estado-ingesta.schema.json` (JSON Schema draft 2020-12). Instancia de referencia: `templates/estado-ingesta.seed.json` (SISBEN, validada).

### 3.1 Estructura de primer nivel

| Campo | Tipo | Requisito |
|-------|------|-----------|
| `schema_version` | `const "1.0.0"` | Versiona el **esquema**, no la instancia |
| `generado_por` | objeto | Skill, versión, `actualizado_en`, `vista_md_regenerada_en` → soporta R2.5 |
| `ingesta` | objeto | Identidad, decisiones de apertura, `contrato_archivo`, `rutas_pipeline` |
| `etapa_actual` | enum de 9 | Dónde está detenido el flujo |
| `etapas[]` | ≥9 objetos | R2.2: artefactos, aprobaciones y riesgos por etapa |
| `aprobaciones[]` | append-only | R3.1, R3.2 |
| `preguntas_abiertas[]` | objetos | Con `bloqueante: boolean` → condición 4 del checklist |
| `riesgos[]` | objetos | R2.2 |
| `backlog_evolutivo[]` | objetos | Regla de corte (G3-ORQ-RF-04) |
| `patrones_conocidos[]` | objetos | Feedback loop del harness (G4-RF-08) |
| `bitacora` | objeto | Ruta, último evento, total, `append_only: const true`, `retencion_meses` |

### 3.2 Decisiones de diseño del esquema

| # | Decisión | Por qué |
|---|----------|---------|
| **D-2** | `additionalProperties: false` en **todos** los objetos | Un campo mal escrito falla la validación en vez de perderse silenciosamente. El expediente es evidencia de auditoría. |
| **D-3** | `transicion_habilitada` por defecto `false`, con `if/then` que **exige** `ref_aprobacion` y `estado ∈ {aprobada, completada_manualmente}` para ponerlo en `true` | **El bloqueo (R3.2) queda codificado en el esquema**, no solo en el prompt. Un estado que avanza sin aprobación es un JSON inválido. |
| **D-4** | `aprobacion.justificacion` obligatoria por `if/then` cuando `riesgo = alto`, `decision ∈ {rechazada, condicional}` o `tipo = regla_de_corte` | R3.5 (fricción proporcional) verificable mecánicamente. |
| **D-5** | `nota_regla_de_corte` obligatoria y `clasificacion_compromiso: const "exito"` cuando `estado = completada_manualmente` | Impide aplicar la regla de corte a una etapa de compromiso contractual. |
| **D-6** | `actor` = `{rol, identificador}`, nunca nombre ni correo | R4.2 exige "quién"; R4.3/GR-6 prohíben PII. Un identificador corporativo (usuario de red) satisface ambos. **Supuesto a confirmar con Experian.** |
| **D-7** | `evidencia_pipeline.conteos` solo admite agregados (total, aceptados, rechazados, menores, quality_score) | GR-6: nunca registros individuales. |
| **D-8** | `evidencia_pipeline.observada` admite `null` + `reportada_por` + `verificada_en` | El Orquestador no lista S3 (GR-2): distingue "no verificado" de "verificado por X en T". |
| **D-9** | `estado` incluye `pendiente_evidencia` | Separa "el artefacto está listo" de "el pipeline ya avanzó". Son dos condiciones distintas del checklist (1 y 5). |
| **D-10** | `checklist_bloqueo` se **persiste** en la etapa con `evaluado_en` | La decisión de bloqueo queda auditable, no solo en la bitácora. |
| **D-11** | IDs con patrón fijo: `APR-\d{3,}`, `BIT-\d{4,}`, `PA-\d{3,}`, `RG-\d{3,}`, `ART-\d{3,}`, `BKL-\d{3,}` | Referencias cruzadas verificables entre JSON y bitácora. |

### 3.3 Relación JSON ↔ `estado-ingesta.md` (R2.5)

- El **JSON es la fuente de verdad**; el `.md` es una **vista derivada** generada desde él.
- El `.md` lleva un encabezado HTML que prohíbe editarlo a mano.
- `generado_por.vista_md_regenerada_en` permite detectar divergencia: si es anterior a `actualizado_en`, el `.md` está rancio y el prompt 01 lo regenera, registrando `BIT` tipo `regeneracion_vista`.
- Ante cualquier divergencia, **manda el JSON**.

## 4. El checklist de bloqueo: dónde vive el gobierno

R3.2 es el requisito más duro del skill. Se implementa en **tres capas redundantes**:

1. **Esquema** (D-3): un estado con `transicion_habilitada: true` sin `ref_aprobacion` **no valida**.
2. **Steering** `03` §3: las 6 condiciones, evaluadas siempre antes de habilitar.
3. **Prompt** `02`: el formato de respuesta obliga a mostrar el checklist marcado y a terminar en BLOQUEADA o PENDIENTE.

No hay override en ninguna de las tres. La regla de corte **no es un override**: cambia el estado de la etapa a `completada_manualmente` con aprobación, justificación y deuda registrada, y el checklist se vuelve a correr completo.

## 5. OQ-T-3 — Retención de la bitácora (CERRADO con supuesto)

**Decisión:** la bitácora vive con el expediente y se conserva **mientras la ingesta esté activa en EDF, con un mínimo de 24 meses** desde el último evento. Se versiona en git junto al expediente. No se rota, no se trunca, no se comprime con pérdida.

**Supuesto declarado:** 24 meses es el *default operativo del framework*, elegido por ser el horizonte típico de auditoría de un ciclo anual con margen. **Si Experian tiene una política de retención de evidencia de auditoría, prevalece esa** y el Mantenedor (KIDAR) ajusta `bitacora.retencion_meses` en el esquema y el valor en `references/03` §5. Queda como pregunta a confirmar con el PO.

## 6. OQ-T-4 — Prueba funcional de la certificación (CERRADO)

`certificacion.md` define 7 casos ejecutables sobre el piloto SISBEN con datos sintéticos. El criterio central: **4 de los 7 casos (C-2, C-5, C-6, C-7) deben BLOQUEAR**. Un solo avance no autorizado invalida la certificación. Ver `certificacion.md`.

## 7. Qué NO hace este diseño

| No hace | Requisito |
|---------|-----------|
| Listar S3, consultar Airflow, disparar DAGs | GR-2 / R5.1 |
| Escribir en repos Oxygen/Bureau | GR-3 / R5.2 |
| Generar procesadores Scala ni configs de etapa | GR-7 / G3-ORQ-RF-05 |
| Compilar o hacer linting (exige la evidencia reportada) | `references/04` §Validation |
| Aprobar, ni avanzar sin aprobación | GR-8 / R3.2 |
| Leer datos reales | GR-5 / R6.3 |

## 8. Preguntas abiertas que quedan vivas

| ID | Pregunta | Dueño |
|----|----------|-------|
| OQ-D-1 | ¿El `identificador` del actor (D-6) es el usuario de red corporativo? Confirmar que no se considera PII bajo la política de Experian. | PO / Seguridad |
| OQ-D-2 | ¿Aplica la política de retención de evidencia de Experian a la bitácora? Si sí, reemplaza los 24 meses. | PO |
| OQ-D-3 | ¿El expediente se versiona en un repo propio o dentro de `bureau.colombia_innersource`? GR-3 impide escribir en el segundo. | KIDAR / Experian |
| OQ-D-4 | ¿La validación del JSON contra el esquema la corre el Desarrollador (`tools/validar-expediente.py`) o un hook de Kiro? | KIDAR |

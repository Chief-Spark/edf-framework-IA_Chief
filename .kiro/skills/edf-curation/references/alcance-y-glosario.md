# Alcance de Curation y glosario

## Qué responde la etapa

> ¿Cómo se expresa este dato en el modelo canónico de Experian?

Curation toma, uno a uno, los eventos que Acceptance ya aceptó y los **transforma y persiste** en el
formato canónico. No diagnostica (eso es Validation), no decide aceptar o rechazar (Acceptance) y no
infiere eliminaciones (Acceptance, `contextualOverride`).

| Etapa | Qué hace con el dato | Persiste transformaciones |
|---|---|---|
| Validation | Diagnostica: códigos de error, severidades, quality scores. Puede normalizar **en memoria** solo para validar (p. ej. `NUM_DOCUMENTO`, RV-GEN-07) | No |
| Acceptance | Decide por archivo y por registro con expresiones sobre QS y códigos | No |
| **Curation** | Normaliza nombres, convierte tipos, fechas a epoch, **homologa** `TIP_DOCUMENTO`, normaliza documento, padding DANE, limpia por severidad, enriquece | **Sí** |

## Hasta dónde llega el ingeniero con este skill

1. Completar o revisar el source-spec (SISBEN viene prellenado).
2. Generar los 12 artefactos (CUR-TPL-01..12) con el generador Python (`scripts/render_templates.py`). La skill no compila nada.
3. Revisar la salida esperada del fixture sintético y el checklist.
4. Presentar la propuesta y **esperar aprobación** (HITL).
5. Copiar los artefactos al fork de Colombia de oxygen-curation, fusionar los fragmentos (regla
   aditiva) y compilar/probar allí. La compilación desde el skill es **no certificada** (F-01).
6. Ejecutar según el runbook (local, Docker o EMR) e interpretar la salida.

Lo que **no** hace: modificar el motor (`SparkCurator`), cambiar claves de etapa
(`runMode`, `excludes`...), decidir campos abiertos (D11-a, P2), reportes `Log_Sisben/RPT_*`.

## Automatizado vs. guiado

| Tarea | Modo |
|---|---|
| Validación del source-spec, coherencia nombres ↔ domain dictionary | Automático (generador Python) |
| Generación de Capa A, B, evento, lector, processor, HOCON, lookups, dictionary, fixture, tests | Automático |
| Salida esperada del fixture | Automático (implementación de referencia `scripts/curation_reference.py`) |
| Parse-check de HOCON generado | Automático (parser mínimo `hocon_check.py`; `pyhocon` si está instalado). Typesafe Config: en el fork |
| Enriquecimiento sin valores (Business Line Code, Type of Data) | Guiado: `TODO(manual)` en el processor |
| Nombre completo | Bloqueado hasta cerrar P2 |
| Fusión en el inner del fork, compilación y ejecución | Guiado (runbook + checklist) |

## Glosario

| Término | Significado |
|---|---|
| EDF / Oxygen | Framework de ingesta de Experian (Preparation → Validation → Acceptance → Curation → Synthesis) |
| `TaggedValidatedEvent` | Evento de entrada de Curation: `lineage`, `eventId`, `validatedRecordJson`, `payload`, `qualityScores`, `errorCodes`, guard-rails, `inferredAsDeleted` |
| `TaggedCuratedEventWithMap` | Evento de salida: guard-rails copiados + `curatedRecordJson` (mapa con los campos publicados) |
| `CurationProcessor` | Clase abstracta a extender; único miembro abstracto `curateEvent` |
| `CuratedEvent` | Clase del evento curado con `write` (JSON) y `read` |
| Capa A / B / C | Acciones genéricas puras / especificación de mapeo de la fuente / processor delgado |
| Guard-rails | `asOfDate`, `subscriber`, `subscriberRelation`, `lineage`: vienen de Validation; Curation los copia |
| Domain dictionary | Archivo con nombre y tipo de cada campo curado; contrato con Synthesis |
| FLOW / STOCK | Modo incremental del periodo / regeneración completa del histórico aceptado |
| Inner | Archivo de configuración aditivo del fork (`*-inner.conf`); el base no se toca |
| HITL | Human in the loop: el skill propone y espera aprobación antes de entregar |
| SUPUESTO / `ASSUMPTION:` | Valor provisional sin evidencia; se marca en código y en el reporte |

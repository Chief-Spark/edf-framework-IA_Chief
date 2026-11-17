---
inclusion: manual
---

# 10 — Piloto SISBEN: contexto histórico

Subordinado a `01-restricciones-principios.md`. Inclusión manual: se invoca con `#10-piloto-sisben` solo para consultar la historia del piloto.

**Este steering no fija ningún valor de la ingesta SISBEN.** Desde la versión 1.3.0 del framework (2026-11-17), el contrato del archivo, las reglas, los catálogos y los datos de apertura de SISBEN están únicamente en sus fuentes:

- `expedientes/sisben/fuentes/hu-sisben.md`
- `expedientes/sisben/fuentes/spec-sisben.md`

Si algo de este documento contradice esas fuentes, mandan las fuentes. **Todos los datos de prueba son sintéticos** (GR-5).

## Qué se dejó de hacer y por qué

| Antes | Ahora |
|---|---|
| Este steering fijaba el contrato del archivo de SISBEN (delimitador `\|`, 22 columnas, ~1,2 M registros/semana) | El contrato lo declara la spec de cada ingesta (`07` §2). El framework no tiene delimitador por defecto |
| Se sembraban 4 preguntas abiertas fijas (PA-001 a PA-004) en todo expediente de SISBEN | No se siembran preguntas predefinidas: cada expediente registra solo las que dejen abiertas su HU y su spec (`edf-orquestador/prompts/00` paso 4) |
| "¿Es fuente oficial?" era pregunta de apertura obligatoria y bloqueante, y declarar la jerarquía elevaba el riesgo | Es un dato informativo y opcional; no bloquea ni eleva el riesgo |

Las cuatro preguntas históricas del piloto (tipo de `fec_digitacion`, event type 30 compartido, fuente oficial, reglas en vuelo) quedaron respondidas en `hu-sisben.md` 1.0.0 (§3 y R-05).

## Contexto técnico de referencia (solo lectura)

- Repo principal: `bureau.colombia_innersource`. Las carpetas sueltas Validation/Curation/Pinning son Java 8 y **no** son las del pipeline actual [DEMO | 00:24:46].
- Procesadores: `SISBENValidationProcessor.scala`, `SisbenCurationProcessor`.
- Config: `validation-common-application.inner.conf` (umbral de calidad).
- Stack: Scala/Spark/EMR, S3, Kafka (MSK), Airflow, ECS Fargate, Hive + Redshift, Maven + Jenkins + Bitbucket, catálogos maestros Collibra → S3.

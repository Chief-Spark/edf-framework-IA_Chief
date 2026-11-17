---
inclusion: manual
---

# 10 — Piloto SISBEN: datos de referencia del expediente

Subordinado a `01-restricciones-principios.md`. Inclusión manual: se invoca con `#10-piloto-sisben` al trabajar el piloto.

Contexto de solo lectura para sembrar y validar el expediente del piloto. **Todos los datos de prueba son sintéticos** (GR-5).

## Contrato del archivo [A1 | 00:19:29–00:21:08]

| Atributo | Valor |
|----------|-------|
| Patrón de nombre | `SISBEN_EXPERIAN_AAAAMM` |
| Delimitador | pipe `\|` |
| Encoding | UTF-8 |
| Header | sí |
| Footer | no |
| Event type / código | **30** (compartido con judicial — ambigüedad abierta [A2 \| 00:57:28]) |
| Columnas | 22 |
| Ruta | `Colombia Data/Sisben` |
| Volumen | ~1.2 M registros/semana |

## Catálogos y reglas por campo

| Regla | Detalle |
|-------|---------|
| **EPOCH** | Todas las fechas se almacenan en epoch [A1 \| 00:14:02] |
| **DIVIPOLA** | Depto / municipio; si el código no existe → **rechazo** [A1 \| 00:44:57] |
| **Tipo de documento — catálogo 202** | SISBEN 1→Exp 10 · 2→7 (menor, no se almacena) · 3→1 (valida contra Registraduría) · 4→4 · 5→8 · 6→5 · 7→nuevo tipo |
| **Nombres estandarizados — catálogos 111/103** | Reglas de caracteres y preposiciones en español |
| **Grupo / nivel / estado / marca** | ~12 estados (válido, excluido, en verificación 10, por distancia 20, …) |
| **Llave de identidad** | tipo doc + número doc + nombre estandarizado; tipo/número no nulos → si nulos, rechazo |
| **Business Light Code** | Obligatorio; indica por dónde llegó el dato (batch / oficial / high frequency) |

## Clasificación PII / público [A1 | 00:07–00:12]

- **Público:** ID fecha origen, ID hogar, código y nombre de depto y municipio, código de clase, grupo, nivel.
- **PII:** ID persona, ID nacional, nombres y apellidos, tipo y número de documento. Cifrado/enmascarado en ambientes previos (KMS / Secret Manager) [A2 | 00:41:44].

## Jerarquía de fuente oficial [A4 | 00:37:12]

- Nacionales: Registraduría > Cámaras de comercio > **SISBEN**
- Extranjeros: Migración > **SISBEN**
- Pregunta de apertura obligatoria de toda ingesta: **"¿es fuente oficial o no?"** — en SISBEN está **indeterminada**: "es como un mixto" [A1 | 00:38:48].

## Preguntas abiertas vivas del piloto (deben sembrarse en el expediente)

| ID | Pregunta | Bloqueante | Evidencia |
|----|----------|:----------:|-----------|
| PA-001 | ¿`fec_digitacion` es numérico o carácter? | ✅ | [A1 \| 00:15:43] |
| PA-002 | ¿Por qué SISBEN y judicial comparten el código 30? | ✅ | [A2 \| 00:57:28] |
| PA-003 | ¿SISBEN es fuente oficial? | ✅ | [A1 \| 00:38:48] |
| PA-004 | Reglas en vuelo: campos obligatorios que pasarán a aceptar null, 2 campos nuevos, catálogos modificables a mitad de camino | ➖ | [A4 \| 00:30:07] |

## Configuración técnica de referencia (solo lectura)

- Repo principal: `bureau.colombia_innersource` (Java 17). Las carpetas sueltas Validation/Curation/Pinning son Java 8 y **no** son las del pipeline actual [DEMO | 00:24:46].
- Procesadores: `SISBENValidationProcessor.scala`, `SisbenCurationProcessor`.
- Config: `validation-common-application.inner.conf` (umbral de calidad).
- `reusePIN = false` (ingesta nueva) [DEMO | 00:26:46].
- Stack: Scala/Spark/EMR, S3, Kafka (MSK), Airflow, ECS Fargate, Hive + Redshift, Maven + Jenkins + Bitbucket, catálogos maestros Colibra → S3.

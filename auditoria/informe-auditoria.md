# Informe de auditoría del framework EDF · versión 1.9.0

| Campo | Valor |
|---|---|
| Fecha | 2026-11-24 |
| Rama | `claude/auditoria-framework-edf` (desde 1.8.0) |
| Alcance | Las 8 skills, las herramientas de `tools/`, los hooks y los instaladores |
| Resultado | 37 hallazgos: 35 corregidos con prueba, 1 descartado tras verificarlo, 1 conocido y documentado. `tools/auditar-skills.py` sin hallazgos |
| Sin verificar aquí | Compilación y pruebas Scala, verificadores PowerShell e `instalar.ps1` (no hay JAR del framework ni PowerShell en este entorno) |

## Por qué

Al comparar lo que generó `edf-validation` con la implementación manual de `oxygen-validation` aparecieron dos defectos del framework (corregidos en la 1.8.0). Tenían patrones generales, y esta auditoría los buscó en todas las skills:

| Revisión | Qué busca |
|---|---|
| A · Decisión ↔ código | Una decisión documentada que el código contradice, que no tiene prueba o la tiene en la capa equivocada |
| B · Errores silenciados | `Try…getOrElse`, `catch` que devuelve un valor por defecto, `.toOption` donde el código toca el motor |
| C · Contrato con el motor | Miembros o claves que el motor lee y la plantilla no genera ni deja declarar |
| D · Contrato entre etapas | La salida de una etapa no es lo que lee la siguiente |
| E · Pruebas ciegas | Pruebas que no pasan por el código real |
| F · Implementaciones manuales | Diferencias con lo que ya está en producción (fuera de este entorno: `prompt-comparacion-manual.md`) |

Clasificación: **defecto del framework** (se corrige en la skill), **hueco del framework** (se agrega al esquema), **problema de la spec** (va a la spec de la ingesta), **defecto del motor o del manual** (se informa a Experian). Severidad: **P0** dato incorrecto sin aviso, contrato roto o gobierno que se puede saltar; **P1** obliga a editar a mano lo generado; **P2** documentación.

## Herramienta repetible

`python tools/auditar-skills.py` revisa todas las skills y sale con código 1 si encuentra:

- un error silenciado sin la marca `SILENCIO-INTENCIONAL <ID>` (la decisión que lo justifica), en la línea o en el comentario anterior;
- una referencia al generador Java deprecado fuera de `DEPRECADOS.md` y `NO-USAR.md`.

También lista, como dato, las decisiones sin rastro en el código. Línea base antes de la auditoría: 19 silencios sin marca y 11 referencias obsoletas. Al final: 0 y 0.

## Hallazgos y correcciones

Cada corrección tiene una fila `AUD-xx` en el `references/decisiones-y-abiertos.md` de su skill (en Pinning, `decisiones-pendientes.md`).

### Validation

| ID | Rev. | Sev. | Hallazgo | Corrección | Estado |
|---|---|---|---|---|---|
| V1 | B | P0 | `Try(debeFiltrar).getOrElse(false)`: si el filtro fallaba, un menor de edad se validaba en vez de filtrarse | `debeFiltrar`, `esHeader` y `Campos.separar` son totales; sin `Try` | Corregido |
| V2 | B | P0 | `asOfDate` caía a `0L` (1970) sin aviso | `DomainData(None, …)`: el motor usa la fecha de recepción y marca `didCreateAsOfDateFromReceivedDate` (`Validator.scala:180-183`) | Corregido |
| V3 | C | P1 | Con footer declarado, el footer nunca se validaba | `validateFooter`, `validate[].footer`, código y casos del fixture | Corregido |
| V4 | B | P1 | El regex del footer solo se validaba con Python | Se rechaza la sintaxis exclusiva de Python; el patrón se compila una vez | Corregido |
| V5 | E | P2 | Las pruebas del processor no usaban `CatalogoDeConsulta` | Catálogo sintético sobre el adaptador real | Corregido |
| V6 | A | P2 | La prueba de ADR-01b solo buscaba texto | Documentado que el comportamiento lo cubre `FieldValidatorsSpec` | Corregido |
| V7 | A | P2 | Documentos citaban el generador Java | Referencias a las funciones y pruebas Python | Corregido |

### Acceptance

| ID | Rev. | Sev. | Hallazgo | Corrección | Estado |
|---|---|---|---|---|---|
| A1 | E | P1 | La prueba evaluaba con un mapa de severidades vacío; el motor usa `buildReasonCodeSeverityMap` | Mapa armado desde la spec, como el motor | Corregido |
| A2 | E | P2 | Casos de rechazo automático que no podían fallar | Se comprueban con la regla `shouldAutomaticReject` del motor | Corregido |
| A3 | E | P2 | Una expresión `accept` rota pasaba como "pendiente" | Prueba de que toda expresión parsea; el `Try` de la decisión queda marcado (así decide el motor) | Corregido |
| A4 | C | P2 | `fileActions.manualReview` no se podía declarar | Clave opcional en el esquema y la plantilla | Corregido |

### Curation

| ID | Rev. | Sev. | Hallazgo | Corrección | Estado |
|---|---|---|---|---|---|
| C1 | C/D | P0 | El domain dictionary usaba claves que el motor ignora (`type`, `nullable`): todo campo quedaba String/LFP sin aviso | Formato v2 del motor (`DomainDictionaryV2.java`): `name` y `description`. `dataType` y `handler` los fija Synthesis | Corregido |
| C2 | B | P0 | La red de seguridad tragaba el error de todo el registro sin log; un regex de derivación con un grupo vaciaba todos los registros | Log sin datos (eventId y clase de la excepción); regex validado al generar y protegido en tiempo de ejecución | Corregido |
| C3 | D | P0 | Sin un componente, `subscriberRelation` mezclaba formatos sin aviso | Conserva el valor aceptado y el registro recibe `subscriberRelation:COMPONENTE_FALTANTE` (ver "Decisiones aplicadas") | Corregido |
| C4 | D | P0 | Claves de identidad nulas que Pinning lee con `.get.toString` | `omitir_si_nulas` (por defecto `standardizedId*`): esas claves se omiten si son nulas | Corregido |
| C5 | E | P1 | La referencia Python y el Scala leían distinto el JSON (`True`/`true`, listas) | Python imita a Gson; errores nulos tolerados en los dos | Corregido |
| C6 | B | P1 | Un lookup de homologación parcial reemplazaba la tabla embebida | Se mezclan: el lookup manda y la tabla completa, con aviso | Corregido |
| C7 | B | P1 | Un nivel de error nulo dejaba el campo sin aviso | Advertencia `<campo>:NIVEL_DESCONOCIDO` | Corregido |
| C8 | A | P2 | D14 decía que la firma del gancho no estaba verificada | Verificada en `CurationProcessor.scala:59` y `Curator.scala:89,179` | Corregido |
| C9 | E | P2 | La prueba del processor no pasa por `curateEvent` (requiere el JAR) | Documentada como prueba del fork | Corregido |

### Pinning

| ID | Rev. | Sev. | Hallazgo | Corrección | Estado |
|---|---|---|---|---|---|
| P1 | C | P1 | Clave `clusterSplitter` sin evidencia; `paths.domainDictionaryFile` de Pinning sin cubrir | "Verificar en el fork" y clave agregada con su comentario | Corregido |
| P2 | D | P1 | Nulos en atributos de identidad (ver C4) | Contrato: pueden faltar, nunca llegan nulos | Corregido |
| P3 | — | P2 | `ISIdentityMatcher` lee el apellido con la clave del nombre | Defecto del motor: informe para Experian; las plantillas no lo reproducen | Informado |

### Synthesis

| ID | Rev. | Sev. | Hallazgo | Corrección | Estado |
|---|---|---|---|---|---|
| S1 | C | P1 | `customRulesFile = null` tumba la etapa (el motor hace `.head`) | Nunca `null`: `["custom-rules-empty.json"]` sin reglas propias; verificadores | Corregido |
| S2 | D | P0 | Un `nombre_en_gdd` errado perdía su handler sin aviso | `verificar-claves.py` cruza `nombre_en_gdd` y `campos_a_generar` con los campos curados | Corregido |
| S3 | A | P2 | No se documentaban los campos EUC que agrega el motor ni las claves reales del GDD v2 | Documentado; el verificador los rechaza | Corregido |

### State Calculation

| ID | Rev. | Sev. | Hallazgo | Corrección | Estado |
|---|---|---|---|---|---|
| SC1 | D | P0 | El `value` de un campo DERIVED y los `field.X` de los CALCULATED no se cruzaban: el motor devuelve el valor por defecto | `verificar-claves.py` los cruza | Corregido |
| SC2 | C | P1 | AGGREGATES: `select` fuera de `groupBy` y `type` en minúsculas (el validador del motor compara literal) | Reglas en `verificar-artefactos-etapa.py` y en el `.ps1` | Corregido |
| SC3 | A | — | Se creyó que `pin` y `parentEventIds` no los inyecta el motor | **Descartado:** sí los agrega (`FoundationStateCalculator.scala:46,51`) | Descartado |

### Orquestador, herramientas e instaladores

| ID | Rev. | Sev. | Hallazgo | Corrección | Estado |
|---|---|---|---|---|---|
| O1 | A | P0 | El validador no exigía C2 (transición desde la etapa), C3 (dependencias e insumo aprobado), C5 (evidencia o diferida), State Calculation siempre crítico, artefactos aprobados ni regla de corte con backlog | Reglas 7a a 7f en `validar-expediente.py`, una prueba por regla | Corregido |
| O2 | E | P0 | Hooks con `python` literal: sin ese comando la compuerta de la etapa 0 falla abierta; la prueba lo ocultaba | Los instaladores configuran el intérprete que existe; prueba con el hook tal como queda escrito | Corregido |
| O3 | A | P1 | Sin `jsonschema` o con posible PII el validador daba OK | Hallazgo salvo `--permisivo` | Corregido |
| O4 | E | P1 | `validar-expediente.py` y `verificar-claves.py` sin pruebas | Pruebas nuevas (cobertura 82 % y 94 %) | Corregido |
| O5 | A | P1 | Los instaladores copiaban desde `kiro/`, pero el repo usa `.kiro/`: no copiaban nada | Copian desde `.kiro/` (o `kiro/` en copias antiguas) | Corregido |
| O6 | A | P2 | La bitácora no se cruzaba con `eventos_totales` y `ultimo_evento` | Conteo, último evento y orden creciente | Corregido |

### Conocido y documentado

| ID | Hallazgo | Estado |
|---|---|---|
| EJ-1 | Los ejemplos SISBEN de las skills no están alineados entre sí: Pinning y State Calculation citan `standardizedId*`, que el ejemplo de Curation no publica a propósito | Documentado; `tests/test_verificar_claves.py` fija el comportamiento. Alinear los ejemplos es un trabajo aparte |

## Decisiones aplicadas

1. **`subscriberRelation` sin un componente (C3).** El plan aprobado proponía dejarlo vacío con advertencia. Al verificar el motor se vio que eso es peligroso: Pinning considera iguales dos registros del mismo suscriptor con el mismo `subscriberRelation` (`IdentityMatcher.scala:55`) y agrupa candidatos por él, así que un valor vacío fusionaría personas distintas. Se aplicó la variante segura: se conserva el valor aceptado, que es único por persona, y se agrega la advertencia.
2. **Claves de identidad nulas (C4).** Se omiten (`omitir_si_nulas`, por defecto `standardizedId*`). El resto de los campos sigue publicando `null`.
3. **Domain dictionary (C1).** Curation publica `name` y `description`. Synthesis es dueño de `handler` y de `dataType`. El enum `Datatype` está en `oxygen-global`, fuera del repo; un valor inválido haría fallar la lectura de todo el diccionario, así que no se escribe sin confirmación.

## Para informar a Experian

| # | Defecto | Evidencia | Efecto |
|---|---|---|---|
| 1 | `ISIdentityMatcher` lee el apellido con la clave del nombre | `oxygen-pinning/.../extension/ISIdentityMatcher.scala:47,52` | El término nombre + apellido ignora el apellido |
| 2 | `.get.toString` sobre atributos de identidad | `ISIdentityMatcher.scala:46-54,78-80` | Un atributo nulo rompe la ejecución |
| 3 | `domainDictionaryFile` ausente en Pinning solo deja un `warn` | `SparkPinner.scala:192-198` | Falla de configuración silenciosa |
| 4 | Constantes de exclusión de RQS de la implementación manual de Validation que no coinciden con los códigos que genera (`SBENVB01705` frente a `SBENVB01205`) | Comparación de Validation, rama `feature/colombia_ifr_java17` | Esas exclusiones nunca se aplican |

## Pendiente de verificar en el fork

- Compilar el código generado y correr las pruebas Scala de cada skill (`TransformacionesSpec`, `FieldValidatorsSpec`, los `*ProcessorSpec`).
- Los valores del enum `Datatype` (Synthesis) y el nombre de la clave `clusterSplitter` (Pinning), que viven en `oxygen-global`.
- `instalar.ps1` y los verificadores `.ps1` de Synthesis y State Calculation: se editaron, pero no se ejecutaron aquí. Las mismas reglas están probadas en Python (`tools/verificar-artefactos-etapa.py`, `tests/test_instalador.py`).
- La revisión F con las implementaciones manuales (`auditoria/prompt-comparacion-manual.md`).

## Pruebas

| Conjunto | Antes | Después |
|---|---|---|
| `edf-validation` | 148 | 160 |
| `edf-acceptance` | 116 | 127 |
| `edf-curation` | 88 | 106 |
| Herramientas (`tests/`) | 42 | 85 |
| **Total** | **394** | **478** |

Sin contar las pruebas omitidas por no tener `pyhocon`. Cobertura de los scripts nuevos o cambiados: entre 82 % y 99 %.

# Inventario de plantillas (implementa `curation_template.config`)

Sintaxis de hueco `{{nombre}}`. Todos los huecos están declarados en
`assets/schema/source-spec.schema.yaml` (`x-placeholders`) con su origen en el source-spec; un test
falla si una plantilla usa un hueco no declarado o si un hueco declarado queda vacío.

Raíces en el fork (SISBEN): `raiz_fuente = src/main/scala/com/experian/edf/oxygen/curation/source/sisben/`,
`raiz_tests = src/test/scala/.../curation/source/sisben/`, código compartido (SUPUESTO CUR-PT01) en
`.../curation/source/common/`.

| ID | Plantilla | Capa | Tipo | Genera | Cumple |
|---|---|---|---|---|---|
| CUR-TPL-01 | `FieldMapping.scala.tpl` | A | fija | `common/FieldMapping.scala` (tipos, `Resultado`, `Motivos`) | CUR-RC03 |
| CUR-TPL-02 | `Transformations.scala.tpl` | A | fija | `common/Transformaciones.scala` | CUR-P08, RC03, AP03, AP04 |
| CUR-TPL-03 | `CurationSpec.scala.tpl` | B | parametrizada | `config/SISBENCurationSpec.scala` | RC05, RF-CUR-13 |
| CUR-TPL-04 | `CuratedEvent.scala.tpl` | — | parametrizada | `SISBENCuratedEvent.scala` (record + evento) | CUR-P15, RC05, T05 |
| CUR-TPL-05 | `AcceptedEventReader.scala.tpl` | — | parametrizada | `SISBENAcceptedRecord.scala` | RC03, CUR-PT04 |
| CUR-TPL-06 | `CurationProcessor.scala.tpl` | C | esqueleto | `processor/SISBENCurationProcessor.scala` | P05, P06, P09, RC01-06, AP08, AP10 |
| CUR-TPL-07 | `curation-block.conf.tpl` | — | parametrizada | fragmento para `oxygen.stage.curation.configs[]` del inner | P05, AP02, AP05, AP12 |
| CUR-TPL-08 | `domain-dictionary.json.tpl` | — | datos | fragmento para `colombia-domain-dictionary-inner.json` | RC05 |
| CUR-TPL-09 | `lookups.conf.tpl` | — | datos | fragmento para `oxygen.lookups[]` | RC04 |
| CUR-TPL-10 | `TransformationsSpec.scala.tpl` | A | fija | `common/TransformacionesSpec.scala` (ScalaTest 3.2 + JUnit 4) | — |
| CUR-TPL-11 | `SourceProcessorSpec.scala.tpl` | — | esqueleto | `sisben/SISBENCurationProcessorSpec.scala` | AP01 |
| CUR-TPL-12 | `accepted-events.json.tpl` | — | datos | `src/test/resources/sisben/accepted-events.json` (JSON lines) | sintético |

Además el renderer escribe `src/test/resources/sisben/lookups/*.csv` (7 tablas sintéticas; la 202 se
genera desde el source-spec), `src/test/resources/sisben/lookups/LEEME.md` (marca de sintético copiada
del fixture, desde el 2026-10-21; `MANIFEST.json` la lista en `marcas_sintetico`) y `expected/` (salida
esperada). Desde el 2026-10-21 (ADR-G3) `MANIFEST.json` lista los CSV de lookups y los de `expected/` en
`otros_archivos`, con `ruta` y `sha256`; `archivos` sigue con una entrada por plantilla y el manifest no se
lista a sí mismo.

## Orden de generación

1. CUR-TPL-01, 02, 10 (solo la primera vez; luego se reutilizan entre ingestas).
2. CUR-TPL-03 (fuente de verdad).
3. CUR-TPL-04, 05, 08, 12.
4. CUR-TPL-06, 11 (incluye la parte manual).
5. CUR-TPL-07, 09.

El generador (`scripts/render_templates.py`) sigue este orden (función `plan`).

## Qué es fijo, qué es parámetro y qué es manual

- **Fijo**: contrato del processor, manejo sin excepciones, `enrichEventWithPayload` passthrough,
  carga única de la homologación, `write`/`read`.
- **Parámetro** (source-spec): identidad, paquetes, patrón de archivo, mapeos, homologación, reglas
  de documento, códigos de error → campo, niveles de severidad, formatos y zona de fechas, catálogos.
  La zona de `asOfDate` de la etapa anterior (`parametros.zona_asofdate_etapa_anterior`) y el origen de la
  zona (`parametros.zona_horaria_fechas_origen`) no llenan ningún hueco: el generador los usa para el
  fixture, para `MANIFEST.json` y para la advertencia de zona (ADR-G4).
- **Manual** (`TODO(manual)`): Business Line Code, Type of Data (CUR-PT06), nombre completo (P2),
  casos de test de enriquecimiento (CUR-PA03).

## Huecos y su origen (resumen)

| Hueco | Origen |
|---|---|
| `Fuente`, `fuente`, `output_event_type` | `fuente.*` |
| `paquete_fuente`, `paquete_compartido`, `processor_clase`, `processor_fqcn` | `paquetes.*`, `processor.nombre_clase` |
| `file_pattern_hocon`, `regex_derivacion_scala`, `tipo_por_prefijo_scala`, `archivo_base` | `archivo.*` |
| `mapeos_scala`, `campos_record_scala`, `campos_to_map_scala`, `campos_from_map_scala`, `claves_publicadas_scala`, `domain_dictionary_entradas` | `campos_curados` (Capa B) |
| `homologacion_scala`, `pendientes_scala`, `claves_homologacion_scala`, `tabla_homologacion`, `columna_*` | `homologacion_tipo_documento` |
| `regla_documento_*`, `campo_tipo_documento` | `reglas_documento` |
| `codigos_error_scala`, `niveles_*` | `codigos_error`, `parametros` |
| `lookups_hocon_entradas` | `catalogos` con `registrar_en_curation: true` |
| `eventos_jsonl`, `casos_spec_scala`, `evento_base_json_scala` | fixture sintético + implementación de referencia |
| `ruta_*` | `rutas_destino` |

Lista completa: `x-placeholders` del esquema.

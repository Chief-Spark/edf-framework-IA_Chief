# Inventario de plantillas (VAL-TPL-01..11)

Sintaxis de hueco `{{nombre}}`. Cada hueco está declarado en `assets/schema/source-spec.schema.yaml`
(propiedad `x-placeholders`) y lo rellena el generador `scripts/render_templates.py` (función `build_context`). Rutas destino relativas a la
raíz del fork de Colombia de oxygen-validation; `raiz_fuente` = `src/main/scala/<paquete_fuente>/`.

| Id | Plantilla (`assets/templates/`) | Capa | Genera | Frecuencia |
|---|---|---|---|---|
| VAL-TPL-01 | `capa-a/FieldSpec.scala.tpl` | A | `src/main/scala/<paquete_comun>/FieldSpec.scala` | una vez |
| VAL-TPL-02 | `capa-a/FieldValidators.scala.tpl` | A | `src/main/scala/<paquete_comun>/FieldValidators.scala` | una vez |
| VAL-TPL-03 | `SourceSpec.scala.tpl` | B | `raiz_fuente/config/<Fuente>Spec.scala` | por fuente |
| VAL-TPL-04 | `ValidatedEvent.scala.tpl` | — | `raiz_fuente/<Fuente>ValidatedEvent.scala` | por fuente |
| VAL-TPL-05 | `FileMetadata.scala.tpl` | — | `raiz_fuente/validator/<Fuente>FileMetadata.scala` | por fuente |
| VAL-TPL-06 | `ValidationProcessor.scala.tpl` | C | `raiz_fuente/processor/<Fuente>ValidationProcessor.scala` | por fuente |
| VAL-TPL-07 | `validation-block.conf.tpl` | — | `env/application-conf/innersource/propuesta/validation-configs-<fuente>.conf` | por fuente |
| VAL-TPL-08 | `lookups.conf.tpl` + `codigos-error.csv.tpl` + CSV de catálogos | — | `.../propuesta/lookups-<fuente>.conf`, `docs/codigos-error-<fuente>.csv` (documental, fuera de `propuesta/`, AUD-V-19), `src/test/resources/<fuente>/lookup/*.csv` | por fuente |
| VAL-TPL-09 | `capa-a/FieldValidatorsSpec.scala.tpl` | A | `src/test/scala/<paquete_comun>/FieldValidatorsSpec.scala` | una vez |
| VAL-TPL-10 | `SourceProcessorSpec.scala.tpl` | — | `src/test/scala/<paquete_fuente>/<Fuente>ValidationProcessorSpec.scala` | por fuente |
| VAL-TPL-11 | `fixture.txt.tpl` | — | `src/test/resources/<fuente>/<nombre del fixture>` | por fuente |

Los fragmentos HOCON se generan bajo `propuesta/`: **no** reemplazan el archivo inner. El técnico pega
cada objeto como elemento nuevo de `oxygen.stage.validation.configs[]` y `oxygen.lookups[]` del
`validation-common-application-inner.conf` (regla aditiva).

## Orden de generación

1. VAL-TPL-01, 02, 09 (solo si el fork no tiene ya la Capa A, VAL-PT01/PT02).
2. VAL-TPL-03 (fuente de verdad).
3. VAL-TPL-04, 05, 11.
4. VAL-TPL-06, 10.
5. VAL-TPL-07, 08.

El generador (`scripts/render_templates.py`) genera todo en una pasada; el orden importa para revisar y copiar.

## De esqueleto a parametrizada

`validation_template.config` declara VAL-TPL-06 y VAL-TPL-10 como *esqueleto* con `parte_manual`. Aquí
esa parte se expresa como datos del source-spec:

| `parte_manual` original | Dato del source-spec | Código generado |
|---|---|---|
| Reglas entre campos (DANE código↔nombre, salvoconducto, documento en ceros) | `reglas_entre_campos[]` (`correspondencia_catalogo`, `valor_con_codigo_propio`, `solo_ceros`) | `ReglaEntreCampos` en `<Fuente>Spec` |
| Reglas de fecha del nombre (periodo y recepción) | `archivo.nombre.{periodo_no_futuro, recepcion_no_futura, recepcion_desde_inicio_periodo}` | `NombreArchivoSpec` + `RecordValidator.validarNombre(spec, ruta, ahora)` |
| Fecha no futura | `campos[].no_futura` | `FieldValidators.noFutura` |
| `listDuplicateEvents` (EXTN) | `duplicados.{clave, modo, campo_fecha}` | `RecordValidator.indiceAConservar` + processor |
| Normalización en memoria (RV-GEN-07) | `campos[].normalizacion` | `FieldValidators.normalizar` |
| Casos de reglas, filtro y duplicados (tests) | `fixture.casos[]` | `bloque_casos_fixture` |

Si una fuente nueva necesita una regla que no cabe en estos tipos, se agrega un nuevo caso al `sealed
trait ReglaEntreCampos` (Capa A) y al esquema; no se escribe lógica a mano en el processor.

## Huecos por plantilla (resumen)

- Identidad: `Fuente`, `fuente`, `event_type`, `datasource`, `subscriber`, `zona_horaria`, `spec_version`.
- Paquetes y rutas: `paquete_comun`, `paquete_fuente`, `ruta_comun`, `ruta_fuente`, `processor_fqcn`.
- Archivo: `delimitador_scala`, `filas_header`, `filas_footer`, `file_format`, `linea_encoding`,
  `file_pattern_hocon`, `regex_nombre_scala`, `grupo_*`, `bloque_file_tags`, `expr_file_as_of_date`,
  `is_footer_row_expr`, `bloque_validate_footer` (vacío sin footer, AUD-V3), `nombre_periodo_no_futuro`,
  `nombre_recepcion_no_futura`, `nombre_recepcion_desde_inicio_periodo` (AUD-V-02).
- Campos y reglas: `bloque_constantes_campos`, `bloque_fieldspecs`, `bloque_reglas`, `bloque_accesores`,
  `bloque_mapa_json`, `header_linea`, `numero_campos`.
- Filtro, QS, duplicados: `filtro_*`, `bloque_quality_scores`, `dup_*`, `dedup_version`,
  `linea_duplicate_threshold`.
- Archivo/HOCON: `codigo_*`, `validate_file_name_hocon`, `validate_header_hocon`, `linea_validate_footer`
  (vacío sin footer), `bloque_metricas`,
  `bloque_lookups`, `bloque_codigos_error`.
- Tests y fixture: `bloque_catalogo_sintetico`, `registro_base_scala`, `bloque_casos_campo`,
  `bloque_casos_fixture`, `bloque_registros_fixture`, `fecha_referencia_scala`, `fixture_nombre_archivo`,
  `header_fuente_linea_scala` y `bloque_pruebas_nombre` (1.9.2, AUD-V-02/AUD-V-09).

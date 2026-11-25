# Decisiones, supuestos y puntos abiertos que afectan a Acceptance

Todo valor marcado **SUPUESTO** se emite con la etiqueta `ASSUMPTION` y su ID (p. ej. `ASSUMPTION S-ACC-02`)
en la spec o en el artefacto y se lista en el reporte de la propuesta. El skill **señala** las discrepancias documentales; no las
resuelve.

## Decisiones confirmadas

| Id | Decisión | Efecto en Acceptance | Estado |
|---|---|---|---|
| D7 | Umbral 0.90 + sintaxis nueva + `SISBFA00001` | `fileConditions`, `reject`, `accept` | cerrada |
| D2 | Borrado RT/EP en Acceptance (`contextualOverride`) | ACC-TPL-04; clave y ventana pendientes (D2-a, Ingesta 3 / Pinning) | propuesta de frontera / **abierto** |
| D8 / D8-a | Nombre con marca de tiempo; `filePattern` con `.*` | ACC-TPL-01, ACC-R03 | propuesta de frontera |
| D6.a / D14 | Menores filtrados en Validation (`shouldFilterRow`), fuera del FQS | Las expresiones de menores del diseño base no aplican | cerrada |
| D9 | Duplicados `TIP_DOCUMENTO + NUM_DOCUMENTO` marcados `OxygenDuplicate` en Validation (EXTN) | `errorcodeNotIn("OxygenDuplicate")` | cerrada |
| D12 | Códigos `SISBEN_ERR_0xx` (sev. 1) / `SISBEN_INC_0xx` (sev. 3), 14 caracteres | Compatibles con ACC-R01 | cerrada |
| D1-D5, D10, D11, D13 | Layout, encoding, guard-rails, mapeo curado, advertencias | No afectan la configuración de Acceptance | — |

## Hechos técnicos verificados que corrigen documentos

- C-ACC-01: la clave de las condiciones es `oxygen.stage.acceptance.fileConditions[]` con
  `{name, expression, reasonCode, isLogged}`. El bloque `RQS_BAJO_UMBRAL_SISBEN { expresion, codigo }`
  de los requisitos es ilustrativo (ACC-PT01 / ACC-PA01 **cerrados**).
- C-ACC-02: si falta una clave de `fileActions` queda `None`; no hay default `"true"/"false"`.
- C-ACC-03: la sintaxis nueva no admite `and`/`or`; la frase debe ser solo `anyOf/allOf(...) [hasErrorlevel…]`.
- C-ACC-04: HU-004 §7 rotula el bloque como `validation-common-application-inner.conf` y usa
  `acceptance { sisben { … } }`; el motor lee `oxygen.stage.acceptance.configs[]` (ACC-PT05). Se sigue el motor.
- C-ACC-05: el umbral `< 90` de HU-004 es un error de escala (ACC-PA04 relacionado: con `reject` en
  sintaxis antigua `SISBFA00001` nunca se registraría).

## Faltantes F-01..F-17 aplicables

| Id | Tema | Aplicación en Acceptance |
|---|---|---|
| F-01 | Sin acceso a JAR / Artifactory | **Compilación no certificada.** ACC-TPL-05 es esqueleto sin compilar; las verificaciones locales son Python (render, schema, subconjunto HOCON, simulador). Check opcional: compilar `oxygen-config` desde fuentes y parsear el HOCON con `AcceptanceConfigs` |
| F-02 | Credencial expuesta en `settings-artifactory.xml` | Prohibido leerlo, copiarlo o referenciarlo; pedir rotación |
| F-03 | Claves HOCON | **Resuelto** para Acceptance y lookups |
| F-04 | Flags de ambiente | `defaultOutputStructure` del ambiente real sin evidencia (SUPUESTO); el skill no lo cambia |
| F-07 | Prefijos RT/EP y `fileTag` | `filePattern` con `(RT_\|EP_)?`; `fileTag` por prefijo es SUPUESTO (solo en el fixture) |
| F-08 | `SISBEN_ERR_023` (salvoconducto) | Provisional; cuenta como sev. 1 en IN-05 |
| F-09 | Fila con ≠ 22 columnas | Llega como error sev. 1 (código provisional) ⇒ rechazada por registro |
| F-10 | Sin severidad 0 | `niveles` de IN-05 = `"1"`, `"3"`, `""` |
| F-11 | Marcadores de nombre/header | Validation solo los emite si existe expresión `validate[]`; si no, Acceptance no ve el marcador y el archivo se evalúa por calidad |
| F-12 / F-13 | Lookups sin estructura verificada | ACC-TPL-03: `tableName`, archivo, formato y delimitador son SUPUESTO |
| F-14 / F-15 | "Más reciente" y nivel de la marca | La marca `OxygenDuplicate` llega con nivel `""`: `errorlevelNotIn("1")` no la detecta, por eso existe `errorcodeNotIn` |
| F-05, F-06, F-16, F-17 | Fork, header literal, reglas duplicadas, UTF-8 | Sin efecto directo en Acceptance |

## Supuestos propios de este skill

Desde el 2026-10-21 (AC-27, ADR-G2) la spec los declara en el bloque opcional `supuestos` con el mismo ID y
el mismo impacto, y `scripts/render_templates.py` los lista: con `--audit`, después de los huecos; sin `--audit`,
después de `generado`/`omitido` (línea `supuesto  S-ACC-xx  impacto=…  donde=…  texto=…`). La prueba
`test_supuestos_de_la_spec_iguales_a_los_del_documento` compara esta tabla con la spec SISBEN. Las marcas `ASSUMPTION`
citan el ID (`ASSUMPTION S-ACC-xx`).

| Id | Supuesto | Impacto | Dónde |
|---|---|---|---|
| S-ACC-01 | `severidades.habilitado = false`: `reject` no usa `hasErrorlevelIn`, así que la severidad de `SISBFA00001` no es necesaria | No se generan ACC-TPL-03 (CSV y `lookup.conf`) ni `lookupMapping`. Si `reject` llega a usar `hasErrorlevelIn/NotIn`, hay que habilitar la tabla (`verifySpec` lo exige, ACC-TPL-03) y cerrar ACC-PT03 | spec (`severidades.habilitado`) |
| S-ACC-02 | Si se habilita: tabla `SISBEN_ACC_REASON_SEVERITY_NH`, CSV `,`, `SISBFA00001 → 1` | Hoy ninguno: la rama está deshabilitada y ACC-TPL-03 se omite. Si se habilita sin confirmar, `lookupMapping`, el CSV y su `lookup.conf` salen con nombre de tabla, formato y delimitador sin verificar (F-12/F-13) y con una severidad provisional (ACC-PT03): `hasErrorlevelIn/NotIn` clasificaría el rechazo de `SISBFA00001` con esa severidad, o lo omitiría si la tabla no se carga | spec (`severidades`, `ASSUMPTION S-ACC-02`) |
| S-ACC-03 | `average` de un QS ausente en todos los eventos es null ⇒ condición `false` ⇒ archivo pendiente | Un archivo sin RQS (contrato con Validation roto) no se rechaza ni se acepta: queda pendiente y no sale de `validated/`. El fixture 900005 y el simulador esperan "pendiente"; falta confirmarlo contra Spark (N4) | simulador, fixture `900005` |
| S-ACC-04 | `JsonUtils` en `com.experian.edf.oxygen.utils` para el Spec | Si el paquete es otro, el Spec Scala (ACC-TPL-05) no compila. No afecta el HOCON ni la decisión del motor; la compilación ya no está certificada (F-01) | ACC-TPL-05 (`ASSUMPTION S-ACC-04`) |
| S-ACC-05 | Fixture JSON Lines con campos de `TaggedValidatedEvent`; `payload`/`minFrequency` omitidos (deserialización tolerante) | Si `JsonUtils.fromJson` no tolera la falta de `payload`/`minFrequency`, el Spec no puede leer los 5 directorios del fixture y sus casos de registro y de archivo fallan al cargar los eventos. No afecta el HOCON ni la decisión en producción | ACC-TPL-06 |

El impacto de S-ACC-02, S-ACC-04 y S-ACC-05 se redactó el 2026-10-21 a partir del material del skill y está
pendiente de revisión humana (hallazgo de `CORRECCION_G_2026-10-21.md`, Acceptance).

## Puntos abiertos

| Id | Tema | Bloquea |
|---|---|---|
| ACC-PT02 / ACC-PA02 | Patrón de archivo definitivo (D8) | Confirmación de Ingesta; el skill entrega con `propuesta_de_frontera` |
| ACC-PT03 | Severidad de `SISBFA00001` | Solo ACC-TPL-03 |
| ACC-PT04 / ACC-PA03 | `contextualOverride`: modos, `deletePredicates`, `lookBackPeriod` | ACC-TPL-04 |
| ACC-PT06 | Herramienta de pruebas de integración (OQ-T-1) | Ejecución de ACC-TPL-05 |
| O-ACC-01 | Requisitos dicen que un duplicado "cuenta en el FQS", pero su `RQS` no baja (la marca se añade tras calcular el QS) | Señalado; no se resuelve aquí |
| O-ACC-02 | `ID_FICHA_ORIGEN` obligatorio vs opcional; `ID_GRUPO` admite null vs A-D | Validation; afecta qué llega como sev. 1 |

## Auditoría de silencios y referencias obsoletas (2026-11-24)

Convención: todo error tragado a propósito en el código generado (`Try(...).getOrElse(...)`,
`Try(...).toOption`, `case NonFatal(_) =>`) lleva `SILENCIO-INTENCIONAL <ID>` en la misma línea o en el
comentario de arriba. Los no intencionales se corrigen. Lo revisan `tools/auditar-skills.py` y la prueba
`test_silencios_intencionales_marcados`.

| Id | Fecha | Qué estaba mal | Qué cambió | Dónde |
|---|---|---|---|---|
| AUD-A1 | 2026-11-24 | El Spec llamaba `evaluator.apply(conditions, Map.empty)`, pero el motor pasa `buildReasonCodeSeverityMap(config.lookupMapping, conditions)` (`Motor-Oxigen/oxygen-acceptance/.../engines/SparkAcceptor.scala:208`, `:224`, `:840-865`): con severidades, `hasErrorlevelIn/NotIn` se evaluaba distinto en la prueba | `ReasonSeverities` se arma con `severidades.filas` cuando `severidades.habilitado` (solo los `reasonCode` de las condiciones, como el motor); si no, queda vacío, igual que el motor sin `lookupMapping` | `AcceptanceExpressionsSpec.scala.tpl`; `test_mapa_de_severidades_como_el_motor` |
| AUD-A2 | 2026-11-24 | Los casos de marcador automático solo afirmaban `expected == "rechazo_automatico"`: no podían fallar | El Spec aplica la regla del motor (`SparkAcceptor.shouldAutomaticReject`, `:190-198`) al nombre del directorio y la compara con la decisión esperada; el marcador debe estar en el directorio; sin `customReasonForAutomaticRejectionEnabled` no se espera `reasonCode`. `verify_spec` hace la misma comprobación sobre el fixture (`marcadores_coherentes`) | `AcceptanceExpressionsSpec.scala.tpl`, `render_templates.py`; `test_marcadores_automaticos_*`, `test_fixture_de_marcadores_coherente_con_el_motor` |
| AUD-A3 | 2026-11-24 | `Try(parse FileAccept).getOrElse(false)` hacía pasar un `accept` roto por "pendiente" | Prueba nueva: `accept` y cada condición deben parsear sobre los archivos del fixture que traen el QS declarado (falla si lanzan). La decisión conserva el `Try` porque así decide el motor (`SparkAcceptor.isFileActionValid`, `:649-654`) y el fixture 900005 (S-ACC-03) lo necesita; queda marcado `SILENCIO-INTENCIONAL AUD-A3` | `AcceptanceExpressionsSpec.scala.tpl`; `test_toda_expresion_declarada_parsea` |
| AUD-A4 | 2026-11-24 | `fileActions.manualReview` no se podía declarar, aunque el motor lo lee (`dsl/acceptance/SparkDatasetAcceptor.scala:71`; en `SparkAcceptor.scala:345-362` está comentado) | Clave opcional en el schema. Si está, se emite en `fileActions`, `verify_spec` exige sintaxis nueva y condiciones definidas, y el Spec prueba que compila. Sin la clave la salida no cambia. El motor de plantillas acepta `{{#si ruta?}}` (clave ausente = falsa) | schema, `acceptance-block.conf.tpl`, `AcceptanceExpressionsSpec.scala.tpl`, `condition_value` en `render_templates.py`; `test_manual_review_*`, `test_bloque_si_opcional_con_interrogacion` |
| AUD-A5 | 2026-11-24 | Referencias al generador Java deprecado como si fuera vigente (`test_simulador.py` decía que el renderizador y sus pruebas estaban en Java) | Apuntan a `render_templates.py` y a `test_render_templates.py`, o quedan marcadas como históricas | cabeceras de `test_render_templates.py`, `test_simulador.py` y `render_templates.py`; `assets/checklist-entrega.md` |
| AUD-A6 | 2026-11-24 | Silencio intencional sin marca | El único (`accept` en la decisión de archivo) queda marcado `SILENCIO-INTENCIONAL AUD-A3` | `AcceptanceExpressionsSpec.scala.tpl` |

## Corrida E2E SISBEN (1.9.5, 2026-11-25)

Defectos del framework que encontró la auditoría de la etapa Acceptance de la corrida E2E SISBEN
(`ejecuciones/sisben-e2e/auditorias/acceptance.md` §2-§3). Las claves nuevas son opcionales y la vieja sigue
aceptada: el insumo de la corrida sigue generando con rc 0.

| Id | Qué estaba mal | Qué cambió | Dónde |
|---|---|---|---|
| HF-19 (AUD-A-10, defecto 1) | La columna «SISBEN» del SKILL y `especificacion-sisben.md` describían otra versión de SISBEN (`RT_`/`EP_` válidos, sin `TXT`, `inferDelete`) y fijaban precedencias | La reference queda rotulada «EJEMPLO HISTÓRICO» y no se cita como fuente; la columna es «Ejemplo de forma (no es fuente)» y dice que los valores salen de la HU y la spec de la ingesta | `SKILL.md`, `references/especificacion-sisben.md`; `tests/test_regresion_e2e_acceptance.py` |
| HF-20 (defecto 2) | Plantillas, manifiesto, schema y script firmaban `edf-acceptance-stage` | Firman `edf-acceptance <version_skill>` | `assets/templates/*`, schema, `render_templates.py`; `test_hf20_*` |
| HF-21 (defecto 3) | El schema genérico exigía `expresiones_propuestas_sisben` | Clave `expresiones_propuestas`; la vieja se acepta como alias deprecado (una de las dos, no ambas) y el generador imprime un `aviso` | schema, `render_templates.avisos`; `test_hf21_*` |
| HF-22 (defecto 4) | `severidades` exigía todos sus campos con `habilitado: false` | `if habilitado: true then required […]`, como `contextual_override` | schema; `test_hf22_*` |
| HF-23 (AUD-A-01, defecto 5) | No había forma de probar otro umbral (HU §5.2 «Umbral parametrizable») sin otra configuración | `fixture.umbrales_alternativos` (opcional): ACC-TPL-05 evalúa `accept` y las condiciones con el umbral sustituido sobre registros sintéticos con QS binario; `simular_decision.py` hace lo mismo. La escala única de la configuración desplegable no cambia | schema, `AcceptanceExpressionsSpec.scala.tpl`, `render_templates.py` (`con_umbral`, `umbrales_alternativos_coherentes`), `simular_decision.py`; `test_hf23_*` |
| HF-24 (AUD-A-08, -09) | El simulador no comparaba con lo esperado (rc 0 aunque difiriera); el Spec decía que con `customReason…` el motor siempre registra el `reasonCode` | El simulador informa `coincide`/`diferencias` por archivo y sale con 1 si algo no coincide; el comentario dice que solo lo registra si alguna condición de `reject` es verdadera sobre los eventos del marcador | `simular_decision.py`, `AcceptanceExpressionsSpec.scala.tpl`, `SKILL.md`; `test_hf24_*` |


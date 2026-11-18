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

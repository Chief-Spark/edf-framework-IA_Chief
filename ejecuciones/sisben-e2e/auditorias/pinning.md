# Auditoría independiente — Etapa 4 (`pinning`) · ingesta `sisben`

| Campo | Valor |
|---|---|
| Auditor | Agente auditor independiente (corrida autónoma del framework EDF 1.9.7, sin Kiro). No corrige: dictamina con evidencia |
| Fecha | 2026-11-25 |
| Alcance | Insumo de Pinning ART-050 `insumos/yaml-skill-pinning.yaml` (versión 1.0.0, `prevalidado`, sha256 `c36f449a…`), preparado por `edf-analisis` (Modo 2, T3) para `edf-pinning`. Contraste con el contrato aprobado ART-034 (`insumos/contrato-curation-pinning.md` 2.0.0, sha256 `6bec3436…` = hash registrado) y con ART-030 (`insumos/source-spec-curation.yaml` v2, sha256 `506e8b9a…` = hash registrado). Verificación en el motor de las afirmaciones del analista, los 5 posibles defectos y las preguntas PA-008 y PA-009 |
| Fuentes válidas | `fuentes/hu-sisben.md` 1.0.2 (R-24 `:93`, §5.4 `:284-304`, §6 `:344`) y `fuentes/spec-sisben.md` 1.0.2 (§1 `:28`, §10.1 `:273-284`, §11 `:308-319`, §14 `:355`). Idénticas a `expedientes/sisben/fuentes/` (`diff -q` vacío) |
| Referencias de control | `.kiro/skills/edf-pinning/` (SKILL.md, `assets/`, `references/`, `evals/`); `.kiro/skills/edf-analisis/` (SKILL.md, `assets/templates/yaml-skill-pinning.yaml.tpl`, `assets/examples/sisben/yaml-skill-pinning.yaml`, `references/logica-pinning.md`, `references/fuentes/pinning-muric.md` nivel 3); steering `06`, `11`; `estado-ingesta.json` |
| Motor | Solo `Motor-Oxigen/` (no hay fork de Colombia). oxygen-core / oxygen-global no están en la copia (no hay fuentes ni jar): `TaggedIdentity`, `Identity`, `TaggedWritableEvent` y el mapeo de claves HOCON a `oxygenConf.oxygen.*` no son verificables |
| Riesgo de la etapa | **N3** (identidad; `06-riesgo-y-aprobaciones.md:36`, `:58`). La opción de escribir `reusePin = false` en la aplicación compartida es **N4** (`06:37`, `:75-77`) |
| **Dictamen global** | **AJUSTES.** Los valores de negocio del insumo son fieles a la spec §11, a R-24 y al contrato ART-034; no copia valores del ejemplo SISBEN; los bloqueos PA-008 y PA-009 están bien identificados y la mayoría de las citas del motor se reproducen. No hay P0. Hay **dos P1 del insumo**: (1) `reusePin` va como booleano `false` listo para generar y la orden de no escribirlo vive solo en un comentario YAML (AUD-P-01); (2) el valor `false` se marca `[C]` cuando, con la semántica del motor, contradice el escenario «Persona existente» de HU §5.4 (AUD-P-02). Hay además 1 P1 del framework (defecto 1) y varios P2 de precisión de evidencia. PA-008 y PA-009 siguen bloqueantes, y ninguna se puede cerrar sin el fork |

## 1. Dictamen de ART-050 — **AJUSTES**

| Bloque del insumo | Fuente | Verificación | Resultado |
|---|---|---|---|
| `modo_colombia.aplica`, `identity_matcher` (`:90-92`) | spec §11 `:313`; HU R-24 `:93` | `ColombiaIdentityMatcher`: decisión `[C]`; clase y FQCN `[A:PA-008]` | Fiel y bien marcado |
| `claves_obligatorias_en_evento_curado`, `clave_opcional` (`:93-96`) | spec §11 `:312`; ART-030 | `verificar-claves.py`: «claves coherentes» (34 claves, 9 citas) | Fiel |
| `bandera_fuente_regla_1` (`:97-99`) | spec §11 `:314`; spec §1 `:26` | Ninguna; regla 2 con nombre; SISBEN no publica dirección ni teléfono (spec `:282`) | Fiel. El efecto de la regla 2 sobre la prioridad 1 no se hace explícito (AUD-P-03) |
| `subscriber_relation_formato` (`:100`) | spec §10.1 `:284` | `<standardizedIdType>_<standardizedIdNumber>` | Fiel |
| `mapeo_identidad.atributos` (`:107-140`) | spec §10.1 `:273-275`, §11; ART-034 §1 | Tipos (entero / texto / texto), PII, normalización y omisión por nulo (RG-019) coinciden con el contrato | Fiel. Roles en el matcher bien marcados `[A:PA-008]` |
| `reglas_matching_por_campo` (`:149-157`) | spec §11 `:316`; `pinning-muric.md` §4.2 | `aplica: false`, `reglas: []`, `nota_spec` sobre el «umbral vigente» | Correcto. Los campos `aplica` y `nota_spec` no están en la plantilla (defecto 4) |
| `instruccion_generacion` (`:159-177`) | SKILL.md `:34` | `valor_resultante: []` (no se regenera la lista) | Correcto. La advertencia fail-silent llega a la conclusión correcta, pero el mecanismo que cita no es exacto (AUD-P-04) |
| `claves_configuracion_pinning` (`:179-195`) | spec §1 `:28`, §11 `:317`; `SparkPinner.scala:223,409` | `identityMatcher` e `identifyingAttributesFile` «no redefinir»; `reusePin: false` (booleano) | **P1** AUD-P-01 y AUD-P-02 |
| `artefactos_salida_hacia_synthesis` (`:202-210`) | spec §14 `:355`; HU §5.4 | Ruta `s3://colombia-data/sisben/pinning/pinned/` `[C]` | Transcripción fiel. No se señala que choca con un Pinning compartido (AUD-P-07) |
| `comportamiento_del_motor_*` (`:212-222`) | motor | 8 de 10 citas se reproducen. `EventPinner.scala:64-65` no prueba lo que se le atribuye (AUD-P-05) | Ajustar |
| Cabecera y `alcance` (`:1-79`) | estado-ingesta.json | PA-009 figura como «PROPUESTA»; en el expediente ya está registrada como abierta y bloqueante (BIT-0108) | P2 AUD-P-08 |

Copia del ejemplo: no hay. El ejemplo (`edf-analisis/assets/examples/sisben/yaml-skill-pinning.yaml:76,88,186,354`) usa `nationalId`, `PRI_NOMBRE`, umbral 0.6 e `ISIdentityMatcher`. El `reusePin: false` coincide con el ejemplo (`:372`), pero aquí sale de spec §1 `:28` y §11 `:317`. Lo no verificable está marcado con `[A:PA-008]`, `[A:PA-009]` y `[PM]`. La única excepción son los `[C]` que se dan por evidencia de comentarios o de una ruta que el batch no usa (AUD-P-05, AUD-P-06).

## 2. Hallazgos

| ID | Clasificación | Sev. | Hallazgo | Evidencia | Corrección |
|---|---|---|---|---|---|
| AUD-P-01 | insumo | **P1** | `reusePin` lleva `valor_a_escribir: false` como booleano YAML. La orden «NO se escribe hasta cerrar PA-009» es un comentario y se pierde al parsear: el loader da `('oxygen.stage.pinning.reusePin', False)`. `edf-pinning` lee esa sección (`yaml-skill-pinning.yaml.tpl:3-4`) y su SKILL manda escribir `reusePin = false` en modo Colombia (`SKILL.md:34`) | `yaml-skill-pinning.yaml:189-192`; `edf-pinning/SKILL.md:34` | `valor_a_escribir: "[A:PA-009] no escribir (clave global de la aplicación)"` y un campo aparte `valor_en_spec: false` como contexto |
| AUD-P-02 | insumo · spec | **P1** | El valor `false` se marca `[C]`, pero con la semántica del motor contradice HU §5.4, escenario «Persona existente → el registro recibe ese PIN». Con `reusePin = false` no se lee el identity-pin-map (`SparkPinner.scala:409-427`) y todos los PIN se renumeran como `zipWithUniqueId + pinningVleRange` (`:979-985`): ningún registro recibe el PIN que ya existía. Ese escenario y «Persona nueva» se cumplen solo con pinning estático: el clúster con PIN previo lo conserva (`:888-905`) y el clúster sin PIN previo recibe uno nuevo (`:881-887`) | spec `:28`, `:317`; HU `:93`, `:290-299`; `SparkPinner.scala:409,881-905,979-985` | Marcar el valor `[A:PA-009]`, no `[C]`. Registrar la contradicción como pregunta de spec dirigida al PO (como PA-007) |
| AUD-P-03 | spec (obs.) · insumo | P1 (a confirmar en el fork) | La prioridad 1 de spec §11 y R-24 («tipo + número, coincidencia exacta») no basta por sí sola según la única descripción del matcher. Sin bandera de fuente, la regla 2 exige además nombre, dirección o teléfono, y SISBEN solo publica nombre. Una persona que ya existe en otra fuente con el mismo tipo y número, pero con el nombre escrito distinto (Ñ, segundo nombre), no hace match: recibe un PIN nuevo y falla el escenario 1 de HU §5.4. El insumo lo intuye (`:99`, `:144`), pero no lo dice como conflicto con la HU | `pinning-muric.md:106-116`, `:153-161`; spec `:315`; HU `:293-295` | Añadirlo a PA-008 (b) como un punto propio |
| AUD-P-04 | insumo | P2 | La conclusión sobre el peligro silencioso es correcta en su efecto, pero imprecisa en tres puntos. (i) El valor «por defecto» de la plataforma es `identityMatcher = ""` (`oxygen-config-default.conf:370`), que falla de inmediato en `Class.forName("")`; `ISIdentityMatcher` solo es el valor del conf de ejemplo (`pinning-application.conf:25`). (ii) Para que sea silencioso hace falta además la lista de ejemplo `identifying-attributes.json` (`["firstName",…,"nationalId","cellPhone"]`). Con la lista de Colombia, `ISIdentityMatcher` haría `.get` sobre `None` (`ISIdentityMatcher.scala:78-83`) y fallaría con excepción. (iii) El colapso no viene de la similitud 1.1: con todos los slots en `""`, todos los eventos comparten la misma `Identity` y se agrupan por `identityId` en una sola identidad antes del matching (`SparkPinner.scala:1756-1770`). Con `groupSSR = true` (valor por defecto, `oxygen-config-default.conf:363`), la llave `""` se filtra (`SparkPinner.scala:676`), y un bloque de 700 o más se descarta (`:719-723`). El 0.5+0.3+0.3 = 1.1 ≥ 0.6 (`ISIdentityMatcher.scala:56-61`) es aritméticamente cierto, pero solo actúa con `groupSSR = false` y bloques de menos de 700 | `yaml-skill-pinning.yaml:167-177`, `:218` | Reescribir la advertencia: condición = conf de ejemplo completo (matcher + lista); mecanismo = colapso por `identityId` `[P]` (la construcción de `Identity` está en oxygen-core); efecto = un solo PIN para todos los eventos de todas las fuentes que no traen esos slots |
| AUD-P-05 | insumo (heredado de ART-034 y RG-019) | P2 | «null lanza» se apoya en `EventPinner.scala:64-65`. Esa línea es `addToPinIndex` sobre las claves de `PinIndex` de un `TaggedCuratedEvent`, y `EventPinner` no se instancia en ningún lugar de la copia (grep). El batch usa `TaggedCuratedEventWithMap` (`PinningIOUtils.scala:23`). La evidencia que aplica es `ISIdentityMatcher.scala:46-54` (`.get.toString` sobre un `null`, lo que da NPE), y solo para el matcher de referencia. Para `ColombiaIdentityMatcher` es `[A:PA-008]`. La decisión de omitir los nulos (RG-019) sigue siendo correcta | `yaml-skill-pinning.yaml:63`, `:214`; `EventPinner.scala:57-66`; contrato ART-034 `:21`, `:69` | Cambiar la cita en el insumo y bajar el marcador a `[P]`. No tocar ART-034 (aprobado): anotarlo para su próxima versión |
| AUD-P-06 | insumo | P2 | «Clave ausente = `""` `[C]`» se apoya en un comentario (`IdentityMatcher.scala:46-49`). La construcción real (`TaggedIdentity.createWithTaggedCuratedEventWithMap`, `SparkPinner.scala:1693,1760`) está en oxygen-core. Además, el lector tiene una rama que el insumo omite: si `INPUT_REF_DIR` está definido, lee esa ruta y no `curatedDir/*/*` (`PinningIOUtils.scala:24-25`). Y `TestIdentityMatcher` es el nombre del archivo; la clase se llama `NullIdentityMatcher` (`TestIdentityMatcher.scala:6`) | `yaml-skill-pinning.yaml:62-63`, `:67` | `[C]` comentario del contrato · `[P]` comportamiento. Citar la rama `INPUT_REF_DIR` como `[PM]` del conf de Colombia |
| AUD-P-07 | spec (obs.) · insumo | P2 | La ruta Pinned de spec §14 (`s3://colombia-data/sisben/pinning/pinned/`) es propia de SISBEN, pero el motor tiene un solo `pinnedDir` por aplicación (`oxygen-config-default.conf:344`; steering 11 §6 #1: la salida conocida es la generación EPOC). La ruta sugiere una aplicación propia (opción c de PA-009), y eso choca con «matcher compartido» y con el escenario 1. El insumo la marca `[C]` sin señalar el choque | `yaml-skill-pinning.yaml:204-207`; spec `:345`, `:355`; steering `11:122` | `[C]` ruta de la spec · `[A:PA-009]` relación con el `pinnedDir` de la aplicación que pinea SISBEN |
| AUD-P-08 | insumo | P2 | PA-009 aparece como «PROPUESTA, bloqueante sugerida» y PA-008 como «registrada». En `estado-ingesta.json` las dos están registradas, abiertas y bloqueantes (PA-009, BIT-0108). La opción (b) de PA-009 (escribir `false` en el conf de Colombia) se ofrece como alternativa, cuando es N4: «No se propone como acción. Se documenta como hallazgo y se escala» | `yaml-skill-pinning.yaml:19-21`, `:72-79`; `06:75-77` | Actualizar el estado de PA-009. Presentar (b) como riesgo N4 que se escala, no como opción |
| AUD-P-09 | framework | P2 | Ninguna pieza del framework advierte del peligro del matcher de referencia con eventos de Colombia. La plantilla del bloque HOCON presenta el FQCN como «ARRANQUE heredado del matcher de referencia» (`pinning-application-block.conf.template:32-37`). `decisiones-pendientes.md` solo advierte del defecto `firstName`/`firstLastName` (AUD-P3) | grep `ISIdentityMatcher` en `.kiro/` | Añadir a `edf-pinning` (SKILL «Modo Colombia» y la plantilla) la advertencia de AUD-P-04: nunca dejar el matcher o la lista del conf de ejemplo con eventos `standardizedId*` |
| AUD-P-10 | framework | P2 | `edf-pinning/evals/evals.json` no tiene ningún caso de modo Colombia (0 apariciones de «Colombia») ni de `reusePin`. El único fixture es JURAD sintético | `evals/evals.json`; `evals/fixtures/` | Caso de evaluación: ingesta Colombia sin bandera, con `reusePin` global; esperado: no escribir la clave y reportar el bloqueo |
| AUD-P-11 | framework | P2 | `SKILL.md:28` dice «Referenciarlo en `identityMatcher`» y `:34` dice «sin redefinir las claves compartidas». Para la ingesta nueva no queda claro si se escribe `identityMatcher`. El insumo eligió no redefinirlo (`:185-188`), que es lo prudente | `edf-pinning/SKILL.md:28,34` | Unificar: en modo Colombia no se redefine ninguna clave compartida; solo se verifica |
| AUD-P-12 | framework (proceso) | P2 (informativo) | Aunque se aprobara ART-050, `edf-pinning` no podría correr: su Paso 0 exige `curation_flow` y `curation_stock` aprobadas o habilitadas. Hoy `curation_flow` está `en_progreso` y `curation_stock` `no_iniciada`, las dos con `transicion_habilitada: false` | `edf-pinning/SKILL.md:13`; `estado-ingesta.json` `etapas[]` | Ninguna sobre el insumo; el Orquestador lo sabe |

## 3. Los 5 posibles defectos que reportó el analista

| # | Veredicto | Sev. · clasificación | Evidencia y matiz |
|---|---|---|---|
| 1 | **Real** | **P1 · framework** | `SKILL.md:34` («con `reusePin = false` en ingesta nueva») y la plantilla `pinning-application-block.conf.template:48-51` («false = pinning dinámico (ingesta nueva…)», `reusePin = {{REUSE_PIN}}`) tratan como si fuera de la ingesta una clave que se lee una vez para toda la aplicación (`SparkPinner.scala:223`) y gobierna toda la corrida (`:409`), con todas las fuentes que lee `curatedDir/*/*` (`PinningIOUtils.scala:27`). Si se sigue en Colombia, renumera todos los PIN (`:979-985`): N4 silencioso. El mismo defecto está en `edf-analisis/assets/templates/yaml-skill-pinning.yaml.tpl:74-75` («false en ingesta nueva»), en `logica-pinning.md` §8 (`:207-208`), que no dice que la clave es global, y en la redacción de RG-002. No es P0 porque PA-009 está registrada como bloqueante y el artefacto es una propuesta con aprobación N3 |
| 2 | **Real** | P2 · framework (steering) | `11-contrato-curation-pinning.md:29` y §6 #2 (`:123`) dicen que el lector solo consta en la copia de Validation. Hoy está en `oxygen-pinning`: `SparkPinner.scala:348` → `:1237-1238` → `PinningIOUtils.scala:23-31,43-46` → `OxygenIOReaderUtils.scala:72-75`. Además, §1 está marcado `[C]` y la línea 29 dice `[A]`, y falta la rama `INPUT_REF_DIR`. ART-034 §2 (`:34`) arrastra el mismo dato desactualizado |
| 3 | **Real** (cita corregida) | P2 · framework (herramientas) | La regla está en `edf-analisis/SKILL.md:99-103`, no en `:88-92`. Para el YAML exige «todas las secciones de su plantilla y que ningún valor de `instruccion_generacion*` dependa de un `[A:*]`», pero no da herramienta para comprobarlo. `tools/verificar-claves.py` solo cubre la coherencia de claves; `verificar-artefactos-etapa.py` cubre Synthesis y State Calculation; `edf-pinning/evals/verificar-artefacto.ps1` es PowerShell y revisa los artefactos generados, no el insumo. Tampoco detectaría AUD-P-01 (un valor activo pendiente de un `[A:*]` fuera de `instruccion_generacion`) |
| 4 | **Real** (cita corregida) | P2 · framework | Los números `:77-93` no corresponden a esa parte de la plantilla (ahí están `reusePin` y lo que sigue). Lo pertinente es `yaml-skill-pinning.yaml.tpl:8-21`: `modo_colombia` no tiene campo para el umbral ni para notas de la spec, y `:9` manda dejar `reglas_matching_por_campo` como «no aplica». `:39-45` fija `ISIdentityMatcher` y 0.6. El analista tuvo que inventar `aplica: false` y `nota_spec` (`yaml-skill-pinning.yaml:150,154`) |
| 5 | **Real** (observación) | P2 · spec | spec §11 `:316`: «prioridad 2 `standardizedIdName` (NearNeighborKeys con el umbral vigente)». La única descripción del matcher (nivel 3) dice tres cosas: `nearNeighborKeys` = solo `standardizedIdNumber` (`pinning-muric.md:153-161`), la distancia es binaria, sin umbral (`:230-231`), y el nombre es un componente de la regla 2, no una vía de bloqueo propia (`:114`). El nombre no puede, por tanto, juntar identidades de números distintos. Va unido a AUD-P-03. Solo se confirma con el fork |

## 4. PA-008 y PA-009

### PA-008 — sigue **abierta y bloqueante** (N3)

| Parte | Qué responde el motor (`Motor-Oxigen/`) | Qué responden las fuentes | ¿Bloquea? |
|---|---|---|---|
| (a) Lector | **Resuelto `[C]`.** `SparkPinner.scala:348` → `:1238` → `PinningIOUtils.readCuratedEvents` (`:23-31`: `INPUT_REF_DIR` o `curatedDir/*/*`) → `OxygenIOReaderUtils.taggedCuratedEventsFromWritable` (`:72-75`). Lista de atributos: solo el primer elemento (`:315`), cargado del classpath (`Pinner.scala:137-142`). Matcher: una sola clase por `Class.forName` (`:220`, `:657`). No verificable: `TaggedWritableEvent.toTaggedCuratedEvent` y la creación de `TaggedIdentity` (oxygen-core) | spec §14 da la ruta curada | No, por sí sola. Basta con actualizar el texto de la pregunta |
| (b) `ColombiaIdentityMatcher` | **No existe en `Motor-Oxigen/`.** Solo hay `IdentityMatcher`, `ISIdentityMatcher`, `RIIdentityMatcher`, `NullIdentityMatcher` (`TestIdentityMatcher.scala:6`) e `IdentityImplPerson`/`IdentityImplCompany`, de Brasil (`IdentityImplPerson.scala:6-7,14`: Receita Federal, CPF). Eso también pesa contra «`IdentityImpl`» como matcher de Colombia (steering 11 §6 #7) | spec §11 y R-24 deciden `ColombiaIdentityMatcher`. `pinning-muric.md` (nivel 3) describe sus reglas; `:29` dice que el tipo es texto («CC, NIT, CE») y SISBEN publica entero (RG-020) | **Sí.** Sin la clase no se sabe cómo compara el tipo, si `""` cuenta como nulo en la regla 0, qué contiene `identifying-attributes-new.json`, cuál es el FQCN ni si se cumple el escenario 1 (AUD-P-03) |
| (c) Ñ | Nada en la copia (`ColombiaMatching.matchNames` ausente) | spec §10.2 `:289` conserva la Ñ | Sí, según la mitigación de RG-021 |

Imposible de verificar sin el fork: todo (b) y (c), y el contenido de la lista compartida.

### PA-009 — la premisa del analista es **correcta**; la decisión segura es **(a)**

- **Motor.** `reusePin` se lee una vez del conf de la aplicación (`SparkPinner.scala:223`) y decide para toda la corrida si se lee el identity-pin-map de la generación de referencia (`:409-427`). La aplicación lee todo lo que hay bajo `curatedDir/*/*` (`PinningIOUtils.scala:27`). Escribir `false` en el bloque del Pinning compartido de Colombia renumera **todos** los PIN de todas las fuentes desde `pinningVleRange` (`:979-985`; `PinGenerator` no se usa en esa rama). Los números de PIN de la generación anterior quedan asignados a otras personas, sin error. Es N4: se documenta y se escala; no se propone (`06:75-77`).
- **Fuentes.** spec §1 `:28` y §11 `:317` dicen `reusePIN = false`, pero HU §5.4 pide que la persona existente «reciba ese PIN». Con el motor, eso solo se cumple con `reusePin = true` (`:888-905`), y la persona nueva recibe un PIN nuevo en ambos casos (`:881-887`). Leído junto a «Ingesta nueva: Sí» (`:28`), `reusePIN = false` expresa que SISBEN no tiene PIN previos propios, no un valor de la clave global.
- **Opción segura: (a).** No escribir `reusePin` en el bloque de SISBEN y conservar el valor del conf compartido. Las identidades de SISBEN no están en el identity-pin-map (centinela `-1L`): se unen al clúster existente con su PIN (escenario 1) o reciben PIN nuevo (escenario 2). Hace falta: (1) que el PO cierre PA-009 con esa lectura y registre la aclaración para la próxima versión de la spec (como PA-007); (2) que el Desarrollador con Ingesta 3 confirme en el fork el valor vigente de la clave (`[PM]`).
- **(b) no es segura:** N4. Aparte del riesgo sobre las demás fuentes, rompe el escenario 1.
- **(c) no es segura:** un espacio de PIN propio no cruza con las otras fuentes, así que el escenario 1 se vuelve imposible y SISBEN deja de usar el matcher compartido (spec §11). Es la que sugiere la ruta de spec §14 (AUD-P-07), así que conviene que el cierre de PA-009 diga también qué aplicación pinea SISBEN y con qué `curatedDir`/`pinnedDir`.
- **Imposible sin fork:** el valor actual de `reusePin` en Colombia; si el archivo es un `*-common-application.conf` (tocarlo es N4, `06:59`); `pinnedReferenceDir`; si está activa la integración HF estática, que también lee el mapa de HBase (`SparkPinner.scala:224,410`); el `curatedDir` y el `INPUT_REF_DIR` de la aplicación de Colombia; el nombre real de la clave HOCON tras `REUSE_PIN` (oxygen-global, no está en la copia; indicios: `pinning-application.conf:27`, mensaje de `SparkPinner.scala:1682`).

### Para aprobar ART-050

1. AUD-P-01 y AUD-P-02 (P1): `reusePin` deja de ser un valor activo y pasa a `[A:PA-009]`.
2. AUD-P-03 dentro de PA-008; AUD-P-04 a AUD-P-08 (P2) en la misma versión.
3. Aun corregido, la aprobación queda condicionada a PA-008 (b), (c) y PA-009: la etapa no se habilita sin el fork (N3, con justificación escrita; `06:74`).

## 5. Verificaciones ejecutadas (solo lectura; sin compilar, sin PowerShell, sin git de escritura)

| Verificación | Resultado |
|---|---|
| `python3 tools/verificar-claves.py --dir …/insumos` | «claves coherentes» (34 claves publicadas, 9 citas) |
| Parseo de ART-050 con PyYAML y secciones contra la plantilla | Están las 8 secciones de la plantilla, sin estado ni aprobador. `reusePin` → `False` (AUD-P-01) |
| sha256 de ART-030 y ART-034 contra `estado-ingesta.json` | Coinciden: no cambiaron tras su aprobación |
| `diff -q` de las fuentes contra `expedientes/sisben/fuentes/` | Idénticas |
| grep `ColombiaIdentityMatcher\|ColombiaMatching` y `extends IdentityMatcher` en `Motor-Oxigen/` | Sin `ColombiaIdentityMatcher`. Matchers: IS, RI, Null, IdentityImplPerson/Company (Brasil) |
| grep `EventPinner` en `Motor-Oxigen/` | Solo su propia definición: no se usa en el batch (AUD-P-05) |
| grep `PENDIENTE_VALIDACION_REGIS\|BDIIREGI\|RNEC` en `oxygen-pinning` | Sin resultados (confirma el `[PM]` del insumo) |
| Lectura de `PinningIOUtils.scala:14-46`, `OxygenIOReaderUtils.scala:60-75`, `IdentityMatcher.scala`, `ISIdentityMatcher.scala`, `EventPinner.scala:1-105`, `Pinner.scala:137-142`, `PinGenerator.scala`, `SparkPinner.scala:192-241,300-450,630-760,815-905,960-1000,1225-1241,1674-1780` | Confirmadas las citas del analista salvo lo indicado en AUD-P-04..06 |
| Lectura de `pinning-application.conf`, `oxygen-config-default.conf:340-375`, `identifying-attributes.json` | `ISIdentityMatcher` solo en el conf de ejemplo (`:25`); por defecto `identityMatcher = ""` (`:370`), `groupSSR = true` (`:363`), `nearNeighborBlockSize = 700` (`:372`) |
| Aritmética de `ISIdentityMatcher` con todos los slots en `""` | 0.5 + 0.3 + 0.3 = 1.1 ≥ 0.6: cierto. Llaves `List("")` (`:85`, el prefijo de nombre da `null` y se filtra). No decide el resultado (AUD-P-04) |
| grep `Colombia`/`reusePin` en `edf-pinning/evals/` y `references/` | 0 casos (AUD-P-10) |

## 6. Insumo v2

| Campo | Valor |
|---|---|
| Alcance | ART-050 v2 (`insumos/yaml-skill-pinning.yaml`, versión 2.0.0, sha256 `be720540…` = el reportado en INT-053), framework 1.9.8 (`VERSION`; commit `d9d7488`, HF-57..62), decisiones de INT-052 (`registro-interacciones.md:413-419`) y `edf-orquestador/references/03-gobierno-riesgo-aprobacion.md` §3e (`:90-99`) |
| **Dictamen de ART-050 v2** | **APROBADO.** Los siete ajustes autorizados (AUD-P-01, -02, -04..-08) están aplicados y citados. Ninguna clave compartida lleva un valor listo para escribir, ni booleano ni de otro tipo. Ningún valor que vaya a un artefacto depende de un hecho de RG-025: esos hechos van `[PM] (RG-025)` y ninguno es `[C]` (§3e, condiciones 1 y 2). Los dos verificadores dan rc=0, y el verificador nuevo rechaza la v1 por `reusePin`. Queda un P2 de marcador en el insumo (AUD-P-13) que no bloquea. Los demás hallazgos son del framework. El P1 (AUD-P-14) no afecta al insumo, pero sí a la aprobación de la etapa |

### 6.1 Ajustes autorizados

| Ajuste | Aplicado en v2 | Resultado |
|---|---|---|
| AUD-P-01 | `:206-210`: `valor_en_fuente` como texto; `valor_a_escribir: "no se escribe en modo Colombia (PA-009 resuelta…)"` | Aplicado. Ningún valor de `claves_configuracion_pinning` deja de ser texto (recorrido del YAML parseado: los únicos booleanos son `modo_colombia.aplica`, `reglas_matching_por_campo.aplica` y los indicadores `obligatorio`/`pii` de los atributos) |
| AUD-P-02 | `:84`, `:207-209` (decisión del PO, PA-009); `:86` (PA-010 para la spec) | Aplicado. El valor ya no se marca `[C]` como instrucción |
| AUD-P-04 | `:179-194`, `:238` | Aplicado: condición (conf de ejemplo completo), casos fail-fast (`:370`, `ISIdentityMatcher.scala:78-83`), mecanismo `[P]` (`SparkPinner.scala:1756-1770`), `:676`, `:719-723`, efecto. Las líneas citadas se reproducen |
| AUD-P-05 | `:72`, `:235` | Aplicado. `EventPinner` ya no aparece en el insumo (grep vacío; el verificador no avisa) |
| AUD-P-06 | `:68` (rama `INPUT_REF_DIR` `[PM]`), `:71` (`[C]` comentario · `[P]` comportamiento), `:74` (`NullIdentityMatcher`, `TestIdentityMatcher.scala:6`) | Aplicado |
| AUD-P-07 | `:223-227` (`relacion_con_pinnedDir`, `oxygen-config-default.conf:344`, PA-010) | Aplicado |
| AUD-P-08 | `:66`, `:83` (estados RESUELTA, iguales a `estado-ingesta.json`); `:85` (opción (b) como hallazgo N4 escalado, `06:75-77`) | Aplicado |
| AUD-P-03 → RG-025 | `:79`, `:106`, `:111` | Registrado como riesgo dentro de RG-025, `[PM]`, con su consecuencia (pregunta bloqueante para la certificación si el fork lo confirma). Coincide con RG-025 en `estado-ingesta.json` |
| Defecto 5 → campo HF-60 | `:108-112` `modo_colombia.umbral_y_matcher_determinista` | Usa el campo nuevo de la plantilla (`yaml-skill-pinning.yaml.tpl:26-30`) |

Condición §3e: los valores que llegan a un artefacto son el bloque HOCON sin claves compartidas (solo comentarios `VERIFICAR`) y la lista de claves del evento. Ninguno lleva una clase, un FQCN, un umbral ni un valor de RG-025 como valor activo: `identityMatcher`, `identifyingAttributesFile` y `reusePin` dicen «no se redefine» o «no se escribe» (`:199`, `:203`, `:208`). `valor_resultante` y `reglas` son `[]` (`:177`, `:169`). Las únicas dos apariciones de `[A:` son la leyenda (`:13`) y la nota de `marcado_heredado` (`:89`). Residuo aceptado: ART-034 sigue citando `EventPinner.scala:64-65` (`contrato-curation-pinning.md:9,21,69,77`). INT-052 decidió no revisarlo por §3c; el insumo ya no hereda esa cita.

### 6.2 Hallazgos nuevos

| ID | Clasificación | Sev. | Hallazgo | Evidencia | Corrección |
|---|---|---|---|---|---|
| AUD-P-13 | insumo | P2 | En `escenarios_hu`, el `[C]` corresponde a la letra de la HU. La frase «Se cumple con el pinning estático de la aplicación compartida (`SparkPinner.scala:881-905`), que no se modifica» queda sin marcador, pero depende de hechos que el propio insumo deja `[PM]`: que el `reusePin` vigente sea `true`, que `pinnedReferenceDir` esté definido y que haya un único identity-pin-map (`SparkPinner.scala:409`, `:420`; insumo `:214`; RG-025). Por §3e, regla 2, el cumplimiento va `[PM]`, nunca `[C]` | `yaml-skill-pinning.yaml:229` | En la próxima versión: «… [C] escenario · cumplimiento [PM] (RG-025: valor vigente de reusePin, pinnedReferenceDir)». Por sí solo no justifica una v3 |
| AUD-P-14 | framework (orquestador) | **P1** | HF-57/HF-59 no llegaron al catálogo de artefactos del Orquestador. La semilla del expediente sigue exigiendo como obligatorios un `<Fuente>IdentityMatcher.scala (o matcher compartido) (con verificacion aritmetica del umbral)`, un `identifying-attributes.json` y un «bloque HOCON … con reusePIN = false (ingesta nueva)». El contrato de la skill exige además el «Valor declarado de `reusePIN` (**`false` en ingesta nueva**)», y el prompt de inicio sigue diciendo «ingesta nueva → `reusePIN = false`». En el expediente, ART-051..053 son obligatorios con esos nombres. En modo Colombia, la etapa no se podría aprobar sin artefactos que la skill ya no genera, o empujaría a generarlos | `edf-orquestador/templates/estado-ingesta.seed.json:388-406`; `references/04-contratos-por-skill.md:79-82`; `prompts/00-iniciar-ingesta.md:21`; `estado-ingesta.json` ART-051..053 | Catálogo por modo. En modo Colombia: ART-051 y ART-052 «no aplica (piezas compartidas, SKILL «Modo Colombia»)», con justificación; ART-053 «bloque HOCON sin claves compartidas»; la fila de `reusePIN`, «valor de la fuente como dato; no se escribe». Ajustar el expediente con el Orquestador |
| AUD-P-15 | framework (herramientas) | P2 | `verificar-insumo-pinning.py` tiene dos puntos ciegos, probados con copias mutadas en el scratchpad. (i) Acepta `valor_a_escribir: "false (no se escribe hasta cerrar PA-009)"`, porque basta que la cadena contenga «no se escribe». (ii) Acepta `clave: "reusePIN"` (la grafía de la spec) con `valor_a_escribir: false`, porque compara el nombre de la clave con mayúsculas y minúsculas exactas. Los dos dan «insumo de Pinning OK». Tampoco revisa `pendientes_de_configuracion` | `tools/verificar-insumo-pinning.py:51-53`, `:126-128` | Comparar el nombre de la clave sin distinguir mayúsculas. Rechazar un valor que empiece por `true`/`false` o por un número aunque sea texto. Revisar también `pendientes_de_configuracion`. Añadir los dos casos a `tests/test_verificar_insumo_pinning.py` |
| AUD-P-16 | framework | P2 | La plantilla pide `estado: "[A:<ID>] mientras no se confirme en el código del fork"` para el campo nuevo. Con verificación diferida, §3e regla 2 manda `[PM]`. El insumo siguió §3e y acertó; la plantilla no prevé el caso | `yaml-skill-pinning.yaml.tpl:30`; `references/03…md:95` | «`[A:<ID>]`; con verificación diferida (§3e), `[PM] (RG-nnn)`» |
| AUD-P-17 | estado (Orquestador) | P2 | `etapas[pinning].preguntas_abiertas` sigue en `["PA-008", "PA-009"]` (las dos resueltas) y no incluye PA-010 (abierta, `etapa_afectada: pinning`) | `estado-ingesta.json`, etapa `pinning` | Actualizar la lista con el Orquestador |

### 6.3 Verificaciones (sin compilar, sin PowerShell, git solo de lectura)

| Verificación | Resultado |
|---|---|
| `sha256sum insumos/yaml-skill-pinning.yaml` | `be720540881c…f4793` (= INT-053) |
| `python3 tools/verificar-insumo-pinning.py --archivo EXP/insumos/yaml-skill-pinning.yaml --contrato EXP/insumos/contrato-curation-pinning.md` | «insumo de Pinning OK», sin avisos, rc=0 |
| `python3 tools/verificar-claves.py --dir EXP/insumos` | «claves coherentes» (34 claves, 9 citas), rc=0 |
| v1 (`git show 5f47629:…/yaml-skill-pinning.yaml` al scratchpad; sha256 `c36f449a…` = la v1 auditada) con el verificador nuevo | rc=1: «X ERROR Colombia: oxygen.stage.pinning.reusePin = False está listo para escribir … (HF-57)», más dos avisos (falta `valor_en_fuente`; cita de `EventPinner`). Detecta la v1 |
| Dos copias mutadas de la v2 (scratchpad) | Las dos pasan como OK (AUD-P-15) |
| `pytest tests/test_verificar_insumo_pinning.py tests/test_regresion_e2e_pinning.py` | 23 passed |
| Recorrido del YAML parseado: valores que no son texto y apariciones de `[A:` y `EventPinner` | Sin booleanos ni números en claves compartidas; `[A:` solo en `:13` y `:89`; `EventPinner` ausente |
| Lectura de `edf-pinning/SKILL.md` «Modo Colombia», `pinning-application-block.conf.template:15-49`, steering `11:27-32,125`, `references/03` §3e, INT-052, PA-008/009/010, RG-002 y RG-025 en el expediente | HF-57..62 aplicados en la skill, la plantilla y el steering. Lo que queda está en AUD-P-14 y AUD-P-16 |

## 7. Propuesta

| Campo | Valor |
|---|---|
| Alcance | ART-053 `propuestas/pinning/pinning-application-block.sisben.conf` (76 líneas, sha256 `fabaa4b4…`), generado por `edf-pinning` con el framework 1.9.9 (`VERSION`; commit `43bf3c7`, HF-63/64). Contraste con el insumo ART-050 v3 (sha256 `9c548c3e…` = APR-023; frente a la v2 solo cambian la versión y la línea 229, según `git diff`), `edf-pinning/SKILL.md` «Modo Colombia» (`:21-45`), `assets/pinning-application-block.conf.template:1-49`, `logica-pinning.md` §8.0 (`:210-219`) y §9, `evals/evals.json` EVAL-PIN-12..14, y el motor (`SparkPinner.scala`, `PinningIOUtils.scala`, `pinning-application.conf`, `oxygen-config-default.conf`). Criterio de INT-057 (`registro-interacciones.md:457`): solo comentarios `VERIFICAR` y referencias, sin claves con valor; grep/pyhocon y `hocon_check`. ART-051/052 no aplican (`obligatorio: false`) |
| **Dictamen de ART-053** | **APROBADO**, con observaciones P2 que no bloquean. El bloque cumple el criterio de INT-057 y EVAL-PIN-12..14: pyhocon da `{}`, `hocon_check` da `OK`/`{}`, no hay claves activas y no queda ningún `{{`. Ningún valor depende de RG-025: los hechos del matcher solo aparecen como `[PM] (RG-025)` en comentarios. `ISIdentityMatcher` e `identifying-attributes.json` solo aparecen en la advertencia HF-58. El texto de la plantilla se conserva literal, y cada línea añadida sale de una línea del insumo. Las observaciones se refieren a la exactitud de la lista `VERIFICAR` frente a lo que lee el motor (AUD-P-18, -19) y a defectos de la plantilla y del verificador PowerShell. Conviene recogerlas en los criterios de cierre de RG-025 o en la próxima generación, sin regenerar ahora |

### 7.1 Verificación del bloque

| Comprobación | Resultado |
|---|---|
| Líneas que no son comentario ni están en blanco (`grep -vE '^\s*(#\|//\|$)'`) | 0 |
| Claves prohibidas en cualquier grafía (`grep -iE 'reuse_?pin\|identity_?matcher\|identifying_?attributes_?file'`) | Fuera de comentarios: 0. En todo el archivo: 12, todas en comentarios `VERIFICAR` o en la advertencia HF-58, que el propio criterio exige. Un grep que no excluya comentarios nunca da vacío: el criterio de INT-057 debe decir «fuera de comentarios» |
| `{{` | 0 |
| pyhocon (`gen-pin/pylib`) | `ConfigTree` vacío: `{}` |
| `hocon_check.py` (`edf-curation` y `edf-acceptance`) | `OK`, rc=0; `{}` |
| Valores que dependen de RG-025 | Ninguno activo. FQCN, contenido de la lista y valores vigentes van como `[PM] (RG-025)` (`:36`, `:40`, `:63-64`) |
| Reproducibilidad (la skill no tiene generador; comparación a mano con `diff`) | Las líneas 1-48 de la plantilla aparecen literales, con `{{INGESTA_NOMBRE}}` → `SISBEN` y `{{REUSE_PIN_VALOR_FUENTE}}` → el valor de la fuente. Lo añadido sale del insumo v3: `:5-12` (cabecera: ART-050 v3, APR-023, PA-008/009); `:35-36` ← insumo `:77`, `:98`; `:39-40` ← `:99`, `:105`; `:49-51` ← `:207`; `:52-53` ← `:85`, `:214`; `:54-56` ← `:86`; `:59-62` ← `:212-213`; `:63-64` ← `:214`; `:75-76` ← plantilla `:18-19`. Fiel, pero no reproducible mecánicamente (AUD-P-21) |
| EVAL-PIN-12 | No hay `reusePin = …` activo. El comentario explica `:223`/`:409`, `:979-985` y `:888-905`, registra el valor de la fuente como dato (`:49-51`) y trata cambiar la clave como N4 escalado (`:47-48`, `:52-53`). Pasa |
| EVAL-PIN-13 | `ISIdentityMatcher`, `RIIdentityMatcher` e `identifying-attributes.json` solo aparecen en la advertencia del colapso (`:66-73`). Pasa |
| EVAL-PIN-14 | No hay ninguna clave compartida activa, ni `IdentityMatcher.scala`, ni `identifying-attributes.json` en `propuestas/pinning/` (solo el `.conf`). Pasa en lo que muestran los archivos; la respuesta de texto del generador (INT-059) no se audita |

### 7.2 Lo que el bloque pide verificar frente a lo que el motor lee

| `VERIFICAR` | Lo que lee el motor | Clave HOCON en la copia | Resultado |
|---|---|---|---|
| `identifyingAttributesFile` | `IDENTIFYING_ATTRIBUTES_FILE` (`SparkPinner.scala:222`); solo `.head` (`:315`) | `oxygen.stage.pinning.paths.identifyingAttributesFile` (`pinning-application.conf:21`) | Correcto, pero sin la ruta `paths.` (AUD-P-18) |
| `identityMatcher` | `IDENTITY_MATCHER` (`:220`), `Class.forName` (`:657`) | `oxygen.stage.pinning.identityMatcher` (`pinning-application.conf:25`; por defecto `""`, `oxygen-config-default.conf:370`) | Correcto |
| `reusePin` | `REUSE_PIN` (`:223`), `:409` | `oxygen.stage.pinning.reusePin` (`pinning-application.conf:27`; no está en `oxygen-config-default.conf`) | Correcto |
| `domainDictionaryFile` | `configMap("oxygen.stage.pinning.paths.domainDictionaryFile")` (`:192-198`) | bajo `paths` | Correcto, sin `paths.` (AUD-P-18) |
| `clusterSplitter` | `CLUSTER_SPLITTER` (`:221`) | **ninguna** en la copia (AUD-P1 de la plantilla, sección fuera de modo Colombia) | El aviso «nombre de la clave sin evidencia» no llega a la sección Colombia (AUD-P-18) |
| `nearNeighborBlockSize`, `pinningVleRange`, `groupSSR` | `:238`, `:242`, `:241`; uso en `:719-723`, `:982`, `:665-676` | `oxygen-config-default.conf:372`, `:373`, `:363` (`groupSSR = true`; el ejemplo dice `false`, `pinning-application.conf:30`) | Correcto |
| `pinnedReferenceDir` | `PINNED_REFERENCE_DIR.isDefined` (`:409`) | `oxygen.stage.pinning.paths.pinnedReferenceDir` (`oxygen-config-default.conf:355`, siempre definido por defecto) | Correcto. Matiz: con el default cargado, `:409` depende en la práctica solo de `reusePin` y de que haya un identity-pin-map único (`:413-421`) |
| `INPUT_REF_DIR` | `oxygenConf.oxygen.INPUT_REF_DIR` (`PinningIOUtils.scala:21`, `:24-25`) | **ninguna** en la copia: `INPUT_REF_DIR` es el nombre de la constante, no una clave HOCON | El nombre de la clave es `[PM]` (oxygen-global) (AUD-P-18) |
| (no listado) `curatedDir` | `oxygen.stage.curation.paths.curatedDir` + `/*/*` (`PinningIOUtils.scala:27`) | `oxygen-config-default.conf:193` | **Falta** (AUD-P-19) |
| (no listado) integración HF estática | `ENABLE_HF_BATCH_STATIC_PIN_INTERGRATION` (`:224`), mapa desde HBase (`:410`) | feature de oxygen-global | **Falta** (AUD-P-19) |

### 7.3 Posibles defectos del generador

| # | Veredicto | Sev. · clasificación | Evidencia |
|---|---|---|---|
| 1 | **Real** (AUD-P-23) | P2 · framework (evals) | `verificar-artefacto.ps1:169`: `$hocon -match 'groupSSR\s*=\s*(true\|false)\b'` sobre el texto completo, comentarios incluidos (`-match` no distingue mayúsculas), y `-notmatch 'TODO.*groupSSR\|groupSSR.*TODO'`. El comentario obligatorio de la plantilla `:46` («filtra con groupSSR = true (:676)») aparece en ART-053 `:72` y no lleva `TODO`. Emulado con la misma regex en Python: **FAIL**. En modo Colombia el falso positivo es seguro. Corrección: quitar las líneas de comentario antes de evaluar (y lo mismo en `Test-Contains` del HOCON) |
| 2 | **Real** (AUD-P-24) | P2 · framework (evals) | `verificar-artefacto.ps1:161-180` solo revisa `groupSSR` y `{{`. Los `falla_si` de EVAL-PIN-12..14 son mecánicos y no tienen chequeo: línea activa `reusePin =`, clave compartida activa, `ISIdentityMatcher`/`RIIdentityMatcher`/`identifying-attributes.json` como valor, presencia de `*IdentityMatcher.scala` o `identifying-attributes.json` en modo Colombia. El aviso final (`:196-198`) tampoco los nombra como manuales. El criterio de INT-057 se aplicó a mano y no queda en ninguna herramienta Python. Corrección: un modo `--colombia` (o una suborden `pinning` en `tools/verificar-artefactos-etapa.py`) con pyhocon o `hocon_check`, grep fuera de comentarios y presencia de archivos |
| 3 | **Descartado** como defecto | — (observación P2 en el artefacto) | `steering 11:120` da #7 por resuelto **para SISBEN** por su spec; la tabla de pendientes del framework mantiene #7 `[A:matcher-colombia]` para cualquier otra ingesta (`11:130`). Que `SKILL.md:28` y la plantilla `:27` nombren las dos clases es correcto en una pieza genérica. En ART-053, la `:38` hereda la alternativa y la `:39` la aclara («Insumo: ColombiaIdentityMatcher (spec §11; HU R-24)»). Matiz útil para el framework: los `IdentityImpl*` de la copia son de Brasil (`IdentityImplPerson.scala:6-7,14`) |
| 4 | **Real** (AUD-P-25) | P2 · expediente (ART-030) | `insumos/source-spec-curation.yaml:26` («[P] omitir_si_nulas: steering 11 §3; EventPinner.scala:64-65») mantiene la evidencia mal atribuida (AUD-P-05). RG-019 ya está corregido en `estado-ingesta.json`. Precisión: INT-052 dice «ART-034 no se revisa por §3c»; ART-030 no aparece. Falta la nota que extiende esa decisión a ART-030. Corregir en la próxima versión de ART-030 |

### 7.4 Hallazgos

| ID | Clasificación | Sev. | Hallazgo | Evidencia | Corrección |
|---|---|---|---|---|---|
| AUD-P-18 | artefacto · framework (plantilla) | P2 | Varios nombres de la lista `VERIFICAR` no son la clave HOCON que el motor lee. `identifyingAttributesFile` y `domainDictionaryFile` viven bajo `oxygen.stage.pinning.paths`. `INPUT_REF_DIR` es una constante sin clave HOCON en la copia. De `clusterSplitter` no hay clave en ningún conf: el aviso AUD-P1 solo está en la sección «fuera de modo Colombia». Quien verifique en el fork puede buscar el nombre equivocado y concluir que «no está» | ART-053 `:33`, `:57-58`, `:63-64`; plantilla `:24-38`; §7.2 | Rutas completas en la sección Colombia de la plantilla; `[PM]` para los nombres que la copia no trae (`clusterSplitter`, `INPUT_REF_DIR`). En ART-053, en la próxima generación |
| AUD-P-19 | insumo · artefacto | P2 | La lista `VERIFICAR` (igual que el insumo `:214`) omite tres de los criterios de cierre de RG-025 (`estado-ingesta.json`). Primero, `curatedDir`: si la salida curada de SISBEN (spec §14) no cae bajo el `curatedDir/*/*` de la aplicación compartida, SISBEN no se pinea y no hay error. Segundo, la integración HF estática (`:224`, `:410`). Tercero, si el conf es un `*-common-application.conf` (tocarlo es N4, `06:59`) | ART-053 `:57-64`; insumo `:214`; RG-025 | Añadir los tres a la lista, o remitir de forma explícita a los criterios de RG-025 |
| AUD-P-20 | framework (plantilla) | P2 | La sección Colombia arrastra la cabecera genérica («Es el bloque de claves con efecto directo…», «Cada clave trae su propio comentario», plantilla `:5-13`), que es falsa sin claves. El mecanismo del colapso se afirma sin el `[P]` que sí lleva `logica-pinning.md` §9 (construcción de `Identity` en oxygen-core) | ART-053 `:14-22`, `:69-71`; plantilla `:5-13`, `:43-45` | Cabecera propia del modo Colombia; `[P]` en el mecanismo |
| AUD-P-21 | framework | P2 | `edf-pinning` no tiene generador y la sección Colombia tiene un solo placeholder de valor. Las líneas «Insumo: …» son texto libre: dos generaciones con el mismo insumo pueden diferir. Hoy son fieles, según la comparación a mano de §7.1 | plantilla `:24-38`; `ls .kiro/skills/edf-pinning` (sin `scripts/`) | Un placeholder por cada `VERIFICAR` (valor esperado y marcador, tomados del insumo) o un renderizador mínimo |
| AUD-P-22 | estado (Orquestador) | P2 | La prevalidación de ART-050 en el expediente sigue diciendo «Analista (INT-053): v2…» con el hash de la v3 (`9c548c3e…`) | `estado-ingesta.json` ART-050 | Actualizar `reportada_por` a INT-058 (v3) |
| AUD-P-23 | framework (evals) | P2 | Defecto 1 | §7.3 | §7.3 |
| AUD-P-24 | framework (evals / herramientas) | P2 | Defecto 2. Además, el criterio de grep de INT-057 no dice «fuera de comentarios» | §7.1, §7.3 | §7.3, y redactar el criterio como «sin claves activas fuera de comentarios» |
| AUD-P-25 | expediente (ART-030) | P2 | Defecto 4 | §7.3 | §7.3 |

### 7.5 Verificaciones (sin compilar, sin PowerShell, git solo de lectura)

| Verificación | Resultado |
|---|---|
| `sha256sum` de ART-053 y ART-050 | `fabaa4b43aee…49ec`; `9c548c3e5b76…4330` (= APR-023) |
| `git diff f6075b6 a688ed4 -- insumos/yaml-skill-pinning.yaml` | Solo la cabecera (versión 3), `documento.version` y la línea 229 |
| `diff` de la plantilla (`:1-48`, con `INGESTA_NOMBRE` sustituido) contra ART-053 | Solo inserciones y la sustitución de `{{REUSE_PIN_VALOR_FUENTE}}` (§7.1) |
| pyhocon (`scratchpad/gen-pin/pylib`), `edf-curation/scripts/hocon_check.py`, `edf-acceptance/scripts/hocon_check.py` | `{}` / `OK` / `{}` |
| greps de §7.1 | 0 claves fuera de comentarios, 0 `{{`, 0 `TODO` |
| Emulación en Python de `verificar-artefacto.ps1:169` | FAIL por la `:72` (defecto 1) |
| Lectura del motor: `SparkPinner.scala:192-242,409-421,665-723,979-985`; `PinningIOUtils.scala:21-46`; `pinning-application.conf`; `oxygen-config-default.conf:193,355,363,370-373` | §7.2 |
| Lectura de INT-057 (`:453-459`), APR-023..025, RG-019, RG-025, ART-050..053 en `estado-ingesta.json` | Etapa `pinning` `en_progreso`; ART-051/052 `obligatorio: false`; ART-053 `prevalidado`, alto |

## 8. Re-auditoría por diferencia (1.9.12)

| Campo | Valor |
|---|---|
| Alcance | Revisión por diferencia (`references/03` §3d) de ART-053. La versión aprobada (APR-026, sha256 `cccd06cf…`, `git show c317cc7:…` = `scratchpad/gen-pin/v2-aprobado.conf`) se compara con la regenerada (`propuestas/pinning/pinning-application-block.sisben.conf`, 70 líneas, sha256 `d63da96c…`), producida por `edf-pinning/scripts/render_bloque_colombia.py` (framework 1.9.12, commit `c7174ba`, HF-65..67) desde ART-050 v3 (sha256 `9c548c3e…`, sin cambios) |
| **Dictamen** | **AJUSTES (framework); no aprobar `d63da96c` todavía.** En lo mecánico, la versión regenerada es correcta: `--verificar` da BLOQUE OK, es reproducible byte a byte, no tiene líneas activas y sus 13 rutas `VERIFICAR` coinciden con el motor. Pero pierde los marcadores `[PM] (RG-025)` del FQCN y del contenido de la lista. `references/03` §3e, regla 2, exige esos marcadores también en los artefactos (`03…md:95`). También pierde el disparador de escalamiento «si el valor vigente es false → PO e Ingesta 3 (RG-002)», que es justo la instrucción que necesita quien ejecute la verificación en el fork. No es una pérdida aceptable, y no hay conflicto con la reproducibilidad: esos datos ya están como **datos** (no como comentarios YAML) en el insumo, y un generador determinista puede tomarlos. Es un defecto del generador (AUD-P-26, P1). Hay que corregirlo antes de aprobar, regenerar y volver a auditar por diferencia. El resto de referencias perdidas sí puede vivir en el expediente (AUD-P-28) |

### 8.1 Verificaciones

| Comprobación | Resultado |
|---|---|
| `render_bloque_colombia.py --verificar <bloque> --dir-propuesta propuestas/pinning` | `BLOQUE OK (modo Colombia)`, rc=0 |
| Regeneración en el scratchpad (`--insumo … --contrato … --out scratchpad/aud-pin-r3/regen.conf`) | rc=0; sha256 `d63da96c…`; `cmp` idéntico: reproducible |
| pyhocon (`gen-pin/pylib`) / `edf-curation/scripts/hocon_check.py` | `{}` / `OK` |
| Líneas activas; claves compartidas fuera de comentarios (en cualquier grafía, las 8); `{{` | 0; 0; 0 |
| `--verificar` sobre la versión aprobada `cccd06cf` | rc=1, 5 hallazgos: faltan `VERIFICAR` de `nearNeighborBlockSize`, `pinningVleRange` y `groupSSR` con su ruta completa, `INPUT_REF_DIR` y `*-common-application.conf` |
| `pytest tests/test_regresion_e2e_pinning.py tests/test_verificar_insumo_pinning.py` | 29 passed |
| `verificar-artefacto.ps1 -Modo colombia` | No ejecutado: PowerShell no está permitido. El script declara como equivalente el `--verificar` de Python (`:28-33`) |

Rutas contra el motor (versión regenerada). Todas correctas:

| Línea | Ruta | Evidencia |
|---|---|---|
| `:21-24` | `paths.identifyingAttributesFile` | `SparkPinner.scala:222`, `:315`; `pinning-application.conf:21` |
| `:25-27` | `identityMatcher` | `:220`, `:657`; `oxygen-config-default.conf:370` |
| `:28-36` | `reusePin` | `:223`, `:409`, `:979-985`, `:888-905`; `pinning-application.conf:27` |
| `:37-38` | `paths.domainDictionaryFile` | `:192-198` |
| `:39-42` | `nearNeighborBlockSize`, `pinningVleRange`, `groupSSR` | `:719-723`, `:982`, `:665-676`; `oxygen-config-default.conf:372`, `:373`, `:363` |
| `:43-44` | `paths.pinnedReferenceDir` | `:409`; `oxygen-config-default.conf:355` |
| `:45-47` | `oxygen.stage.curation.paths.curatedDir` | `PinningIOUtils.scala:27`; `oxygen-config-default.conf:193` |
| `:48-50` | HBase | `:224`, `:410` (`Constants.features.ENABLE_HF_BATCH_STATIC_PIN_INTERGRATION`) |
| `:51-56` | `clusterSplitter`, `INPUT_REF_DIR` | `[PM]`; `:221`, `PinningIOUtils.scala:21,24-25` |
| `:57-58` | `*-common-application.conf` | `06:59` |
| `:63` | colapso | `[P]` (`logica-pinning.md` §9) |

AUD-P-18..21 y AUD-P-23..24 quedan cerrados en el framework y en el artefacto.

### 8.2 Referencias propias de la ingesta: aprobada frente a regenerada

| Referencia de la versión aprobada | En la regenerada | ¿Está como dato en el insumo v3? | Juicio |
|---|---|---|---|
| `[PM] (RG-025)` del contenido de `identifying-attributes-new.json` (aprobada `:35-36`) | No aparece (`:21-24`, sin marcador) | Sí: `claves_configuracion_pinning[identifyingAttributesFile].estado` «… contenido de la lista [PM] (RG-025)» (`:200`); `pendientes_del_mapeo[0].id` = `RG-025` (`:154`) | **Necesaria** (§3e, regla 2) |
| `[PM] (RG-025)` del FQCN y del comportamiento del matcher (aprobada `:39-40`) | No aparece. `:26-27` dice «Insumo: ColombiaIdentityMatcher (steering 11 §6 #7)» sin marcador y cita una fila genérica que sigue `[A:matcher-colombia]` (`11:130`) | Sí: `claves_configuracion_pinning[identityMatcher].estado` «[C] decisión (spec §11) · [PM] FQCN en el fork (RG-025)» (`:204`) | **Necesaria** (§3e, regla 2) |
| Escalamiento si el valor vigente es false (RG-002) (aprobada `:52-53`) | Solo la regla general «Cambiar la clave es N4: se documenta y se escala» (`:34-35`). Falta el disparador «si lo que encuentras en el fork es false, escala», que es otro caso: el conf compartido ya renumera en cada corrida | Sí: `precondiciones_bloqueantes[PA-009].hallazgo_N4_escalado` (`:85`); `pendientes_de_configuracion[2].estado` (`:214`) | **Necesaria**: es la instrucción operativa de la propia lista `VERIFICAR` |
| PA-009 (decisión del PO, opción a) | Implícita en «NO SE ESCRIBE (HF-57)» y en el valor de la fuente | `precondiciones_bloqueantes[PA-009]` (`:82-86`); `estado` de reusePin (`:209`) | Conveniente (el ID); el efecto ya está |
| PA-010 (sentido de «reusePIN = false» y ruta Pinned de spec §14) | No aparece | `pendiente_spec` (`:86`) | Aceptable que viva en el expediente |
| «Sin bandera de fuente (regla 2)» | No aparece | `modo_colombia.bandera_fuente_regla_1` | Aceptable: es una regla del matcher, no una clave del conf |
| ~28 M frente a `nearNeighborBlockSize` | Solo «los bloques >= este valor se descartan sin error» | `pendientes_de_configuracion[1]` (`:213`) | Aceptable: los bloques salen por `standardizedIdNumber` (nivel 3), no por volumen de archivo |
| ART-050 / ART-034 / APR-023 | La cabecera nombra el insumo con su versión y su sha256 (`:6-7`) | — | Aceptable: el sha256 une el bloque con ART-050 v3 aprobado |

### 8.3 Hallazgos

| ID | Clasificación | Sev. | Hallazgo | Evidencia | Corrección |
|---|---|---|---|---|---|
| AUD-P-26 | framework (generador y plantilla) | **P1** | `render_bloque_colombia.py` solo rellena cinco huecos (`valores()`, `:108-131`). No lleva al bloque el `estado` de las entradas de `claves_configuracion_pinning`, ni las precondiciones y los riesgos del insumo, aunque son datos. El artefacto pierde los `[PM] (RG-025)` que §3e, regla 2, exige en los artefactos, y el disparador de escalamiento RG-002. `verificar_bloque()` no lo detecta | `render_bloque_colombia.py:108-131`, `:141-162`; plantilla `:20-36`; insumo `:85`, `:154`, `:200`, `:204`, `:209`, `:214`; `03…md:95` | Leer de forma determinista del insumo: (a) el `estado` de `identifyingAttributesFile`, `identityMatcher` y `reusePin`, que se copia en su `VERIFICAR`; (b) una línea «Referencias de la ingesta» con los `id`/`estado` de `precondiciones_bloqueantes` y `pendientes_del_mapeo`; (c) el escalamiento desde `hallazgo_N4_escalado`. Que `--verificar --insumo` exija `[PM]` y el ID del riesgo cuando el insumo los tenga. Añadir una prueba. Después: regenerar y re-auditar por diferencia |
| AUD-P-27 | framework (plantilla) | P2 | La plantilla (`:27`) fija «(steering 11 §6 #7)» como fuente del matcher del insumo. Esa fila sigue `[A:matcher-colombia]` para cualquier ingesta (`11:130`), así que el artefacto cita un pendiente abierto donde el insumo cita spec §11 / HU R-24 | regenerada `:26-27`; insumo `:204-205` | Tomar la `fuente` de la entrada `identityMatcher` del insumo |
| AUD-P-28 | expediente | P2 (aceptado) | PA-010, «sin bandera», el volumen de ~28 M y los IDs ART-034/APR-023 dejan de estar en el bloque. Viven en el insumo (unido por sha256), en `estado-ingesta.json` y en la entrega del generador | §8.2 | Ninguna en el artefacto. La entrega del generador debe listarlos (SKILL.md `:40`: «lo que el script no decide … sigue siendo tu respuesta») |
| AUD-P-29 | estado (Orquestador) | P2 | Con la etapa `pinning` aprobada, ART-053 figura `aprobado` con hash `cccd06cf…`, pero el archivo en `propuestas/` ya es `d63da96c…`. Ninguna de las dos versiones cumple a la vez el verificador de 1.9.12 (la aprobada da 5 hallazgos) y §3e (la regenerada pierde los `[PM]`). Mientras dure la revisión §3d, el artefacto vigente es el de `c317cc7` | `estado-ingesta.json` ART-053; §8.1 | Mantener el registro según §3d hasta regenerar con AUD-P-26 corregido; aprobar entonces el nuevo hash |

## 9. Re-auditoría por diferencia (1.9.14): ART-053 (§3d) y ART-034 (§3d-bis)

| Artefacto | Dictamen |
|---|---|
| ART-053 `propuestas/pinning/pinning-application-block.sisben.conf` (sha256 `ffc146a7…`), regenerado con `render_bloque_colombia.py` de 1.9.14 (commit `9f5db12`, HF-81/82) desde ART-050 v3 (`9c548c3e…`, sin cambios) | **APROBADO.** AUD-P-26 y AUD-P-27 quedan cerrados. Frente a la versión rechazada (`d63da96c…`) solo hay inserciones: el `estado` y la `fuente` del insumo con sus `[PM] (RG-025)` en las tres claves, el disparador de escalamiento RG-002, el hallazgo N4 de PA-009 y las «Referencias de la ingesta». La fuente del matcher es ahora la del insumo, no la fila 7 del §6 de steering 11. Las dos pérdidas que avisa el generador son aceptables (AUD-P-30, AUD-P-31) |
| ART-034 `insumos/contrato-curation-pinning.md` 2.1.0 (sha256 `4124211f…`) frente a 2.0.0 aprobado (`6bec3436…` = `scratchpad/aprobados/…` = `git show c317cc7:…`) | **APROBADO como revisión no sustantiva (§3d-bis).** Cumple (a), (b) y (d). (c) se cumple para los hechos del contrato, con el matiz de AUD-P-32, que la aprobación debe dejar escrito |

### 9.1 ART-053: verificaciones

| Comprobación | Resultado |
|---|---|
| `render_bloque_colombia.py --verificar <bloque> --insumo insumos/yaml-skill-pinning.yaml --dir-propuesta propuestas/pinning` | `BLOQUE OK (modo Colombia)`, rc=0 |
| El mismo `--verificar --insumo` sobre la versión rechazada `d63da96c` y sobre la aprobada `cccd06cf` | 13 y 17 hallazgos: faltan `[PM]`/RG-025, el escalamiento de PA-009 y las referencias. El verificador detecta AUD-P-26 |
| Regeneración en `scratchpad/aud-pin-r4/regen.conf` (con `--contrato`) | rc=0; `cmp` idéntico a `ffc146a7`: reproducible |
| La misma regeneración con el ART-034 **aprobado** (2.0.0) como `--contrato` | Idéntica: el cambio de ART-034 no altera el artefacto desplegable (§3d-bis (a)) |
| pyhocon / `hocon_check.py` | `{}` / `OK` |
| Líneas activas; claves compartidas fuera de comentarios (las 8, en cualquier grafía); `{{` | 0; 0; 0 |
| `steering 11 §6 #7` en el bloque y en la plantilla | Ausente: AUD-P-27 cerrado. La fuente del matcher es la del insumo (`SparkPinner.scala:220, :657; oxygen-config-default.conf:370`, insumo `:205`) |
| Rutas `VERIFICAR` | Las mismas 13 que en §8.1, ya comprobadas contra el motor; no cambian |
| `verificar-insumo-pinning.py` y `verificar-claves.py` con ART-034 2.1.0 | OK; «claves coherentes» |

### 9.2 ART-034: condiciones de §3d-bis (`references/03…md:94-99`)

Diff completo (5 líneas): versión `2.0.0` → `2.1.0` (`:6`); «Fuentes de esta tabla» (`:9`); fila nueva «Cambios respecto de 2.0.0» (`:12`); celda «¿Puede faltar?» de `standardizedIdType` (`:22`); mitigación de RG-019 (`:70`); pie «Motor» (`:78`). En las cinco se cambia la cita `EventPinner.scala:64-65` por `ISIdentityMatcher.scala:46-54` `[P]` (válida solo para el matcher de referencia; para Colombia `[PM]` (RG-025)).

| Condición | Evidencia | Resultado |
|---|---|---|
| (a) Ningún valor desplegable cambia | Los únicos consumidores mecánicos de ART-034 son `verificar-claves.py` (claves) y el `--contrato` del generador de ART-053. Con el contrato viejo y con el nuevo, ART-053 sale idéntico. Fuera de la columna de citas, la tabla §1 (clave, tipo, normalización, PII, slot, estado) es idéntica | Se cumple |
| (b) Solo citas, comentarios o metadatos | Versión, fila de cambios, fuentes, una celda de cita, una mitigación y el pie | Se cumple |
| (c) Ningún marcador de estado de un hecho cambia | Los marcadores del contenido del contrato (columna «Estado» de §1, filas de §2) no cambian (`[C]` 10 → 10, `[A:` 3 → 3). Aumentan `[P]` (5 → 10) y `[PM]` (4 → 9), siempre en la cita que justifica la omisión. Ver AUD-P-32 | Se cumple para los hechos del contrato (matiz AUD-P-32) |
| (d) No gana ni pierde dependencias | Aparece la mención de RG-025, pero ningún valor del contrato depende de ella: la omisión de nulos vale con cualquier comportamiento del matcher, porque «ausente = `""`» y RG-019 ya está aceptado. Esa relación ya estaba aprobada en el expediente: mitigación de RG-019 (INT-052) e insumo ART-050 v3 (APR-023, `:72`, `:235`). PA no cambian (PA-007, PA-008) | Se cumple |
| Cita nueva contra el motor | `Motor-Oxigen/oxygen-pinning/src/main/scala/com/experian/edf/oxygen/pinning/extension/ISIdentityMatcher.scala:46-54`: `.get.toString` sobre los cuatro atributos de cada identidad. Un valor `null` (`Some(null)`) da `NullPointerException`; un atributo ausente del mapa (`None`) da `NoSuchElementException`. La ruta `util/IdentityMatcher.scala:46-49` (comentario «ausente = `""`») también es correcta. `[P]` es el marcador justo: `ISIdentityMatcher` no es el matcher de Colombia, y la construcción de los atributos está en oxygen-core | Correcta |

### 9.3 Hallazgos

| ID | Clasificación | Sev. | Hallazgo | Evidencia | Corrección |
|---|---|---|---|---|---|
| AUD-P-30 | artefacto (pérdida avisada) | P2, aceptado | PA-010 no aparece en ART-053. En el insumo no es una precondición con `id`: es el texto `pendiente_spec` de PA-009 (`yaml-skill-pinning.yaml:86`), y el generador solo copia `id`/`estado`. Es una pregunta de spec, no bloqueante, que no cambia ninguna `VERIFICAR`; vive en `estado-ingesta.json` (PA-010, etapa `pinning`). Aceptable, como AUD-P-28 | ART-053 «Referencias de la ingesta»; insumo `:86` | Ninguna ahora. Opcional para el framework: copiar `pendiente_spec` junto a la precondición |
| AUD-P-31 | insumo (pérdida avisada) | P2, aceptado | Ninguna `VERIFICAR` lleva la etiqueta «criterio de cierre de RG-025 (INT-052)». Para `reusePin` vigente, `pinnedReferenceDir` e `INPUT_REF_DIR` el vínculo sí existe: la pendiente copiada bajo `reusePin` las nombra con «[PM] verificar en el fork (RG-025, INT-052)», y la de `INPUT_REF_DIR` lleva `[PM]`. §3e, regla 2, pide el `[PM]`, no esa etiqueta. Las otras tres condiciones de cierre de RG-025 (`curatedDir`, HBase, `*-common-application.conf`) salen de la plantilla sin RG-025, porque el insumo (`:214`) no las lista. Ese es el origen de AUD-P-19 en el insumo. Los criterios de cierre viven en RG-025 (`estado-ingesta.json`), que las aprobaciones de certificación deben citar | ART-053 `:55-58`, `:66-88`; insumo `:214`; RG-025 | No exige cambiar ahora el insumo ni la plantilla. En la próxima versión de ART-050: añadir `curatedDir`, HBase y `*-common-application.conf` a la pendiente `:214`, y el generador los copiará con RG-025 |
| AUD-P-32 | framework (`references/03` §3d-bis) | P2 | Pregunta del coordinador sobre (c). La cita no pasa de `[P]` a `[PM]`. Antes el hecho «un `null` explícito lanza» no llevaba marcador y se apoyaba en una evidencia inválida (`EventPinner`), así que se leía como confirmado. Ahora lleva `[P]` para el matcher de referencia y `[PM]` (RG-025) para Colombia. Es un cambio del estado de un hecho, aunque sea un hecho de justificación, no de contenido del contrato, y es una **bajada** de confianza ya aprobada en el expediente (RG-019 en INT-052; ART-050 v3). Por eso es no sustantivo. Pero la letra de (c) y (d) no distingue entre los marcadores del contenido y los de las citas que lo justifican, ni prevé alinear un artefacto con un cambio de marcador ya aprobado en otro | `03…md:98-99`; ART-034 `:22`, `:70`, `:78` | La aprobación de ART-034 2.1.0 debe decirlo en su `justificacion` («(c): solo bajan a `[P]`/`[PM]` los marcadores de la cita del motor; los de clave, tipo, regla y slot no cambian; alineado con RG-019 (INT-052) y APR-023»). Para el framework: precisar en (c) y (d) que el contenido manda, y que una bajada de confianza ya aprobada en el expediente no es sustantiva |
| AUD-P-33 | artefacto (metadatos) | P2 | ART-034 2.1.0 sigue citando «`insumos/source-spec-curation.yaml` v2», pero ART-030 ya es v2.1 (§3d-bis, INT-082, cabecera `:48`). Las claves no cambian («claves coherentes»). La fila «Lector del evento en `oxygen-pinning`» (§2, `:34`) sigue `[A:PA-008]`, aunque PA-008 (a) está resuelta. Corregirla sería un cambio de marcador (§3d-bis (c)), así que hace bien en no tocarla aquí | ART-034 `:9`, `:34` | Actualizar la versión citada y la fila del lector en la próxima revisión sustantiva de ART-034 (§3c), no en esta |

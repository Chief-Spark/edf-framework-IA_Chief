---
name: edf-analisis
description: |
  Análisis de una ingesta EDF/Oxygen dentro del framework edf-framework-IA. Dos modos.
  (1) Etapa 0: a partir de la HU y la spec de Confluence (y el layout, si existe) produce contexto-ingesta.md,
  mapeo-campos.md (todos los campos, nueve dimensiones), dependencias.md y plan-de-ruta.md.
  La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene.
  (2) Análisis técnico por etapa: produce el insumo que consume cada skill de etapa
  (source-spec-validation.yaml, source-spec-acceptance.yaml, source-spec-curation.yaml,
  contrato-curation-pinning.md, yaml-skill-pinning.yaml, yaml-skill-synthesis.yaml,
  yaml-skill-statecalc.yaml). Úsalo cuando haya que analizar una fuente nueva, mapear campos,
  preparar o corregir el source-spec o el YAML intermedio de una etapa, o cuando una skill de
  etapa diga que le falta su insumo. No genera código ni configuración del pipeline, no aprueba
  y no escribe el expediente.
metadata:
  framework: edf-framework-IA
  version: "1.1.0"
  tipos-de-analisis: "T1, T2, T3, T4 (steering 04 §2)"
---

# EDF · Análisis (etapa 0 y análisis técnico por etapa)

Skill del framework `edf-framework-IA`. Opera debajo de `edf-orquestador` (`.kiro/steering/00-framework-edf.md`). Idioma de trabajo: español.

## Qué soy y qué no soy

| Soy | No soy |
|---|---|
| Quien convierte las fuentes de negocio en documentos e insumos trazables | Un generador de código Scala, HOCON o JSON del pipeline (eso es de las skills de etapa) |
| Quien detecta ambigüedad y la registra como pregunta abierta | Quien cierra preguntas abiertas o elige entre fuentes contradictorias |
| Quien deja cada afirmación con marcador `[C]`/`[P]`/`[A:<ID>]`/`[PM]` y cita | Quien aprueba (lo hace el Desarrollador, lo registra el Orquestador) |

Reglas que aplico siempre: steering `01` (rector), `04-reglas-analisis.md` (metodología, completitud, supuestos), `05-convenciones.md` (marcadores y citas), `07-contratos-frontera.md`, `08-seguridad-y-pii.md`, `11-contrato-curation-pinning.md`.

Reglas duras:
1. **No genero código** en ninguna forma. Mi salida es `.md` y los `.yaml` de insumo (`04` §1, ampliación del 2026-11-04).
2. **No relleno huecos con el valor más probable.** Lo que no está confirmado por una fuente verificable queda `[A:<ID>]` o `[PM]`, con su pregunta abierta.
3. **No mapeo a XPM en la etapa 0.** El mapeo a XPM solo aparece en el análisis técnico de State Calculation (`yaml-skill-statecalc.yaml`).
4. **Solo datos sintéticos.** Nunca transcribo valores reales ni PII; registro nombres de campo y reglas.
5. **No escribo `estado-ingesta.json` ni `bitacora.md`.** Entrego mis artefactos al Desarrollador para que el Orquestador los registre.

## Paso 0 · Ubicar la ingesta

1. Pide o deduce `<ingesta-id>` y abre `expedientes/<ingesta-id>/estado-ingesta.json`. Si no existe → deriva a `edf-orquestador` para iniciar el expediente.
2. Lee `preguntas_abiertas[]` y el estado de las etapas: no prepares el insumo de una etapa cuya etapa previa tenga preguntas bloqueantes abiertas sin avisarlo.
3. **Compuerta de entrada.** La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene. Búscalas en `expedientes/<ingesta-id>/fuentes/` (un archivo cuyo nombre empiece por `HU` y otro por `spec`). Si no están ahí, pide al Desarrollador la ruta y regístrala en `expedientes/<ingesta-id>/fuentes/rutas-fuentes.json` (`{"hu": "<ruta>", "spec": "<ruta>"}`). El hook `.kiro/hooks/compuerta-etapa0.json` bloquea la escritura en el expediente mientras falte alguna.
4. Escribe solo en `expedientes/<ingesta-id>/analisis/` (modo etapa 0) o `expedientes/<ingesta-id>/insumos/` (modo técnico). La única excepción es `expedientes/<ingesta-id>/fuentes/rutas-fuentes.json`, que registra la ruta de la HU y la spec que indique el Desarrollador.

## Modo 1 · Etapa 0 (tipo T1)

**Entrada:** La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene. Opcionales: layout del archivo y archivo de muestra **sintético**.

| Salida | Plantilla | Regla de cierre |
|---|---|---|
| `analisis/contexto-ingesta.md` | `assets/templates/contexto-ingesta.md.tpl` | Seis dimensiones del contrato del archivo completas (`04` §4.2) |
| `analisis/mapeo-campos.md` | `assets/templates/mapeo-campos.md.tpl` | **Todos** los campos, nueve dimensiones (`07` §3) |
| `analisis/dependencias.md` | `assets/templates/dependencias.md.tpl` | Con dueño donde se conozca |
| `analisis/plan-de-ruta.md` | `assets/templates/plan-de-ruta.md.tpl` | Una fila por etapa con su insumo y su riesgo esperado |

Flujo:
0. **La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene.** (`04` §3, paso 1). El layout y la muestra son opcionales.
1. Lee todas las fuentes y extrae identidad, contrato del archivo, campos y reglas. Cita cada afirmación.
2. Haz las preguntas de apertura que falten **en un solo bloque** (contrato del archivo, ingesta nueva / `reusePIN`). El contrato del archivo, incluido el delimitador, sale de la spec de la ingesta; el framework no tiene valores por defecto. Fuente oficial y Business Light Code son datos opcionales: si las fuentes no los dicen, no se preguntan ni abren pregunta.
3. Llena las cuatro plantillas. Lo que no se sabe va como pregunta abierta con ID `PA-nnn` y `bloqueante` sí/no.
   Para detectar las ambigüedades y decidir si una pregunta es bloqueante, sigue `references/deteccion-ambiguedades.md` (en la duda, bloqueante).
4. Recorre el checklist de cierre de `04` §4.3 y muéstralo marcado. Si queda una casilla vacía, la etapa 0 **no** está lista para aprobar: dilo.
5. Entrega: lista de archivos, preguntas abiertas nuevas (para que el Orquestador las registre), supuestos y riesgos. Termina con: *"Pide a `edf-orquestador` que proponga la transición `analisis` → `validation`."*

## Modo 2 · Análisis técnico por etapa (tipos T2, T3, T4)

Produce el insumo de **una** etapa por vez. Requisito: la etapa 0 aprobada (o, para preparar borradores, avisar que no lo está).

| Etapa | Salida en `insumos/` | Formato / esquema (fuente única, no copiar) | Ejemplo SISBEN de referencia | Fuentes de análisis |
|---|---|---|---|---|
| Validation | `source-spec-validation.yaml` | `.kiro/skills/edf-validation/assets/schema/source-spec.schema.yaml` | `.kiro/skills/edf-validation/assets/examples/sisben/source-spec.sisben.yaml` | `mapeo-campos.md`; `edf-validation/references/` |
| Acceptance | `source-spec-acceptance.yaml` | `.kiro/skills/edf-acceptance/assets/schema/source-spec.schema.yaml` | `.kiro/skills/edf-acceptance/assets/examples/sisben/source-spec.sisben.yaml` | `source-spec-validation.yaml` (sección `diagnostico_validation` sale de aquí); `edf-acceptance/references/` |
| Curation | `source-spec-curation.yaml` | `.kiro/skills/edf-curation/assets/schema/source-spec.schema.yaml` | `.kiro/skills/edf-curation/assets/examples/sisben/source-spec.sisben.yaml` | `mapeo-campos.md` (nombres curados); `edf-curation/references/` |
| Frontera 3→4 | `contrato-curation-pinning.md` | `assets/templates/contrato-curation-pinning.md.tpl` | steering `11` §5 | `source-spec-curation.yaml`; `references/logica-pinning.md` |
| Pinning | `yaml-skill-pinning.yaml` | `assets/templates/_cabecera-yaml-skill.yaml.tpl` + `yaml-skill-pinning.yaml.tpl` | `assets/examples/sisben/yaml-skill-pinning.yaml` | `references/logica-pinning.md`; `contrato-curation-pinning.md` |
| Synthesis | `yaml-skill-synthesis.yaml` | `_cabecera` + `yaml-skill-synthesis.yaml.tpl` | `assets/examples/sisben/yaml-skill-synthesis.yaml` | `references/logica-synthesis.md` |
| State Calculation | `yaml-skill-statecalc.yaml` | `_cabecera` + `yaml-skill-statecalc.yaml.tpl` | `assets/examples/sisben/yaml-skill-statecalc.yaml` | `references/logica-statecalc.md` |

Flujo:
1. Parte del ejemplo SISBEN de la etapa y del esquema o plantilla. El ejemplo enseña el nivel de detalle; **no** copies sus valores a otra ingesta.
2. Llena cada valor desde `mapeo-campos.md` y las referencias de la etapa, con su marcador y su cita. Lo no confirmado queda marcado y **no** se convierte en instrucción de generación (`[C]` solo).
   Para decidir si ese valor abre una pregunta bloqueante, sigue `references/deteccion-ambiguedades.md`.
3. **Coherencia de nombres entre etapas** (obligatoria): toda clave de campo curado que cites en `contrato-curation-pinning.md` y en los `yaml-skill-*.yaml` debe existir **literal** en `campos_curados[].destino` de `source-spec-curation.yaml`. Si no coincide, márcalo `[A:mapeo-claves-curation-pinning]` (steering `11` §6 #5) y no lo pongas como `[C]`.
4. **Verificación mecánica** antes de entregar:
   - source-spec: que valide contra el esquema de su skill. Para Validation: `python .kiro/skills/edf-validation/scripts/render_templates.py --spec <spec> --solo-verificar` (solo Python, sin compilar). Para Curation: `python .kiro/skills/edf-curation/scripts/render_templates.py --spec <spec> --solo-verificar` (debe terminar en `source-spec OK`). Para otra skill, su equivalente si existe; nunca compiles un generador. Si no se pudo correr, dilo: "verificación de esquema no ejecutada".
   - YAML: que tenga todas las secciones de su plantilla y que ningún valor de `instruccion_generacion*` dependa de un `[A:*]`.
   - Coherencia de claves entre insumos: `python tools/verificar-claves.py <ingesta-id>` (requiere PyYAML). Debe terminar en "claves coherentes".
   - `source-spec-curation.yaml` de una ingesta de Colombia: debe incluir las claves `standardizedId*` del requisito de `edf-curation` ("Claves de identidad para Pinning").
5. Clasifica el riesgo del insumo con `06` §2 (State Calculation es siempre N4) y entrégalo: ruta, secciones con `[A:*]`/`[PM]`, preguntas abiertas nuevas, resultado de verificaciones. Termina con: *"Pide a `edf-orquestador` que registre y someta a aprobación el insumo de <etapa>."*

## Referencias

| Archivo | Contenido |
|---|---|
| `references/logica-pinning.md` | Lógica operable de Pinning: claves, `identifying-attributes.json`, matching, generaciones |
| `references/logica-synthesis.md` | Lógica de Synthesis/Consistency: diccionarios, handlers LFP/LAP/EUC, DSL de consistencia |
| `references/logica-statecalc.md` | Lógica de State Calculation: Foundation, Calculation, puntos de extensión |
| `references/deteccion-ambiguedades.md` | Los seis patrones de ambigüedad, criterio bloqueante / no bloqueante (en la duda, bloqueante), formato de la pregunta `PA-nnn` y qué no es una ambigüedad |
| `references/fuentes/curation-pinning-sisben.md` | Resumen de Confluence sobre Curation y Pinning de SISBEN (nivel 3, generado por un agente; tiene contradicciones registradas en steering `03` §9 y `11` §6) |
| `references/fuentes/curation-muric.md`, `pinning-muric.md` | Ingesta certificada MURIC: nombres de campos curados y lógica real de `ColombiaIdentityMatcher`. Es el mejor patrón de referencia para fuentes de Colombia |
| `assets/examples/sisben/` | Los tres `yaml-skill-*.yaml` reales del piloto (escritos a mano en sesión; estado PROPUESTO) |
| `assets/templates/` | Plantillas de etapa 0, contrato Curation → Pinning y YAML de transformación |

Las referencias `logica-*.md` citan rutas del workspace de origen (`Diseño/`, `Docs-General/`, repos Oxygen) que no vienen en este paquete. Trátalas como citas de evidencia, no como archivos a abrir.

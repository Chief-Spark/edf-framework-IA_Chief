# Prompt 00 — Iniciar una ingesta (crear el expediente)

**Reproducible:** mismo prompt + misma fuente → mismo expediente inicial. Componente ② de la certificación.

## Invocación

```
Orquestador: inicia el expediente de la ingesta <NOMBRE>.
Fuente: <ruta de la HU> y <ruta de la spec de Confluence>.
Workspace del expediente: expedientes/<id>/
```

La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene.

## Qué debe hacer el Orquestador

0. **Compuerta de entrada.** La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene. Busca un archivo `HU*` y otro `spec*` en `expedientes/<id>/fuentes/`. Si no están, pide al Desarrollador la ruta y regístrala en `expedientes/<id>/fuentes/rutas-fuentes.json` (`{"hu": "<ruta>", "spec": "<ruta>"}`). El hook `.kiro/hooks/compuerta-etapa0.json` bloquea la creación del expediente mientras falte alguna; la carpeta `fuentes/` está exenta del bloqueo.
1. **Verificar que no exista** ya `expedientes/<id>/estado-ingesta.json`. Si existe, **no sobrescribir**: reportar el estado actual (prompt 01) y preguntar si se quiere continuar ese expediente.
2. **Tomar los datos de apertura de las fuentes** (HU y spec) o del prompt. Preguntar al Desarrollador **solo los que falten**, en un solo bloque. Ningún valor del framework los sustituye: cada ingesta declara los suyos.
   - **Contrato del archivo**: patrón de nombre, delimitador, encoding, header/footer, event type, nº de columnas, formato. **El delimitador es el carácter que declare la spec de la ingesta** (`;`, `|`, `,`, tabulador u otro); el framework no tiene uno por defecto. Si la spec no declara alguna dimensión del contrato, es pregunta abierta bloqueante.
   - **Modo de Pinning** (`references/04`, etapa 4; HF-63). Se toma de la spec y la HU: si dicen que la ingesta usa el matcher compartido de Colombia (p. ej. `ColombiaIdentityMatcher`, la lista `identifying-attributes-new.json` o la aplicación de Pinning del bureau de Colombia), es **modo Colombia**; si la ingesta define su propio matcher y su propia aplicación, es **modo propio**. Si las fuentes no lo dicen, es pregunta abierta bloqueante de `pinning`. Modo Colombia: los artefactos de la semilla quedan como están (ART-051 y ART-052 `obligatorio: false`, «no aplica en modo Colombia»; ART-053 es el bloque sin claves compartidas). Modo propio: ART-051 y ART-052 pasan a `obligatorio: true` y ART-053 se llama «bloque HOCON de pinning-application.conf con reusePin declarado».
   - ¿Es **ingesta nueva**? → `es_ingesta_nueva: true` e `ingesta.reuse_pin` = el valor que declare la fuente (`false` en ingesta nueva). En **modo propio** ese valor se escribe en el bloque de la ingesta. En **modo Colombia** es solo un dato: «ingesta nueva» significa que la ingesta no tiene generación previa propia, `reusePin` es global de la aplicación compartida y `edf-pinning` nunca lo escribe (HF-57).
   - **Rutas del pipeline** (solo lectura): entrada, validated, accepted, rejects, curated, joblogs. Lo que no se conozca queda `null`.
   - **Business Light Code**: ¿por dónde llega el dato (batch / oficial / high frequency)? Si no se conoce, `null`.
   - **Fuente oficial** (`si` / `no` / `mixto` / `indeterminado`) y su posición en la jerarquía: **dato informativo y opcional**. Si las fuentes no lo dicen, se registra `indeterminado` **sin abrir pregunta** y sin preguntar. No bloquea ni eleva el riesgo.
3. **Sembrar `estado-ingesta.json`** a partir de `templates/estado-ingesta.seed.json` (plantilla genérica: se reemplazan **todos** los valores de `ingesta` con los de la HU, la spec y el paso 2), con las 8 entradas de etapa (orden 0–7: etapa 0 + 6 etapas, Curation en Flow y Stock), sus dependencias, `clasificacion_compromiso`, `riesgo_tipico`, `dag_airflow` y los artefactos obligatorios de cada contrato (`references/04-contratos-por-skill.md`). En los nombres de artefacto, `<Fuente>` se reemplaza por el nombre de la fuente. **Todas** las etapas arrancan en `no_iniciada` con `transicion_habilitada: false`.
4. **Registrar las preguntas abiertas que declaren las fuentes de la ingesta** (las que la HU o la spec dejen abiertas) y las que surjan del paso 2. **No se siembran preguntas predefinidas**: si la HU y la spec declaran que no tienen preguntas abiertas, `preguntas_abiertas[]` arranca vacío.
5. **Registrar los riesgos** heredados del Inception (mínimo RG-001: mapeo incompleto por specs ambiguas).
6. **Crear `bitacora.md`** desde `templates/bitacora.template.md` y apendizar `BIT-0001` tipo `inicio_expediente`.
7. **Generar `estado-ingesta.md`** desde el JSON.
8. **Crear** las carpetas vacías `analisis/`, `insumos/` y `propuestas/`.
9. **Validar** el JSON con `python tools/validar-expedientes.py <id>` (esquema + invariantes) antes de darlo por creado.

## Salida esperada

```
✅ Expediente creado: expedientes/<id>/
   - estado-ingesta.json  (schema 1.1.0, validado)
   - estado-ingesta.md
   - bitacora.md          (BIT-0001)

Etapa actual: analisis (no_iniciada)
Transiciones habilitadas: ninguna
⛔ Preguntas abiertas bloqueantes: <n> → <lista de IDs>

Siguiente paso propuesto: pide a `edf-analisis` la etapa 0 ("analiza la ingesta <id>, etapa 0")
para producir contexto-ingesta.md, mapeo-campos.md, dependencias.md y plan-de-ruta.md.
No habilito `validation` hasta que el contexto esté aprobado, las preguntas bloqueantes resueltas
y `insumos/source-spec-validation.yaml` aprobado.
```

## Guardrails aplicables
GR-5 (solo fixtures sintéticos), GR-6 (sin PII en el expediente), GR-8 (no aprobar en nombre del Desarrollador).

# Prompt 08 — Registrar el insumo o los artefactos que entrega una skill

**Reproducible:** mismo expediente + misma entrega → mismo registro. Conecta a `edf-analisis` y a las skills de etapa con el expediente: ellas no escriben `estado-ingesta.json`; lo hace el Orquestador con este prompt.

## Invocación

```
Orquestador: registra el insumo de <etapa> de la ingesta <id>.
Orquestador: registra los artefactos de <etapa> de la ingesta <id>.
```
Variantes: `ya está el source-spec de validation`, `edf-pinning terminó`, `registra lo que generó <skill>`.

## Qué debe hacer el Orquestador

1. **Leer el expediente** y ubicar la etapa. Si la etapa no está habilitada para trabajo (su dependencia no está aprobada), registrar solo como borrador: `estado_artefacto: propuesto` y avisar que no puede ir a aprobación.
2. **Verificar que los archivos existen** en la ruta declarada:
   - insumo → `insumos/<archivo>` (ver `references/04-contratos-por-skill.md`);
   - artefactos → `propuestas/<etapa>/`.
   Si falta un archivo, no lo registra como presente: queda `ausente` y lo dice.
3. **Actualizar `artefactos[]` de la etapa**: `ruta`, `estado_artefacto: propuesto` (o `prevalidado` si la skill reportó verificaciones que pasaron), `riesgo` según `references/03` §1, `contexto_precargado` (qué cambió, por qué, regla aplicable, patrón previo) y, si aplica, `prevalidacion` con lo **reportado** (nunca inventado; si no se corrió, `null`).
4. **Registrar preguntas abiertas nuevas** que entregó la skill (`PA-nnn`, `bloqueante`, `etapa_afectada`, `origen`) y los supuestos `[A:*]` como riesgos si corresponden.
5. **No aprobar.** El artefacto queda `propuesto`/`prevalidado`. La aprobación del insumo se hace con el prompt `03` (`tipo: artefacto`); la de la etapa con `02` + `03` (`tipo: transicion`).
6. **Apendizar** `BIT-nnnn` tipo `propuesta` con la lista de artefactos, su riesgo y la skill que los produjo.
7. **Validar** con `python tools/validar-expedientes.py <id>` y **regenerar** `estado-ingesta.md`.

## Salida esperada

```
📥 Registrado — <etapa> · <insumo | artefactos> de <skill>
| ID | Artefacto | Ruta | Estado | Riesgo | Verificación reportada |
...
Preguntas abiertas nuevas: <PA-nnn …> · Supuestos abiertos: <n>
Registrado: BIT-00nn · Expediente válido: <✅|❌>

**Siguiente paso:** <uno solo — p. ej. "aprueba el insumo con el prompt 03" o "pide a edf-validation que genere">
```

## Guardrails aplicables
GR-6 (sin PII: solo nombres de campo, rutas y conteos), GR-7 (no genero los artefactos), GR-8 (no apruebo).

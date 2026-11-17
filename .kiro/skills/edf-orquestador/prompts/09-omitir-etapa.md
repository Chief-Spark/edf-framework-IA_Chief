# Prompt 09 — Registrar la omisión de una etapa

**Reproducible:** misma decisión + mismo expediente → mismo registro. Regla: `references/03-gobierno-riesgo-aprobacion.md` §4b.

## Invocación

```
Orquestador: registra la omisión de <etapa> para la ingesta <id>.
Decisión: <cita de la decisión de diseño que la respalda>
Actor: PO / <identificador corporativo>
Justificación: <texto>
```

## Qué debe hacer el Orquestador

1. **Verificar el actor.** Solo `PO` o `Aprobador designado`. Si lo pide otro rol → no registrar; explicar que la omisión es decisión del PO.
2. **Verificar que no haya conflicto abierto.** Si la etapa tiene una pregunta abierta sobre si aplica (p. ej. `OQ-TR-4` para Synthesis en SISBEN) y la respuesta del PO no la cierra explícitamente, no registrar.
3. **Exigir** `justificacion` (mín. 10 caracteres) y la cita de la decisión.
4. **Registrar** `APR-nnn` con `tipo: omision`, `decision: aprobada`, riesgo según `references/03` §1, `resultado: modificado`.
5. **Actualizar la etapa:** `estado: omitida`, `nota_omision`, `ref_aprobacion`. Sus artefactos quedan como están (no se aprueban).
6. **Revalidar el checklist** de la etapa siguiente. `omitida` con esta aprobación satisface la condición 1; las demás siguen aplicando.
7. **Apendizar** `BIT-nnnn` tipo `omision`, **validar** con `python tools/validar-expedientes.py <id>` y **regenerar** la vista.

## Salida esperada

```
⏭ Etapa <etapa> → omitida
   APR-00n (BIT-00nn) · actor: PO · `<id>`
   Decisión: <cita>
Checklist para habilitar <N+1>: <✅ habilitada | ⛔ bloqueada por cond. n>

**Siguiente paso:** <uno solo>
```

## Guardrails aplicables
GR-8 (no aprobar en nombre del PO), GR-6 (sin PII).

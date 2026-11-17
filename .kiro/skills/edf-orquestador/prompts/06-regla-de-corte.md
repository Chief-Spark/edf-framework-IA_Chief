# Prompt 06 — Aplicar la regla de corte (G3-ORQ-RF-04)

Permite marcar una etapa como **completada manualmente** cuando no se logró automatizar dentro del plazo, sin bloquear la entrega al negocio y sin perder trazabilidad.

## Restricción dura

Solo aplica a etapas de **clasificación Éxito**: `curation_flow`, `curation_stock`, `pinning`, `synthesis`, `state_calculation`.

**No aplica** a `analisis` (habilitador) ni a `validation`, `acceptance` (clasificación Aceptación — compromiso contractual). Si se pide para una de esas, el Orquestador **lo rechaza** citando la clasificación y la restricción del esquema.

## Invocación

```
Orquestador: aplica la regla de corte a <etapa>.
Pendiente: <qué queda sin automatizar>
Motivo: <por qué no se logró en el plazo>
Actor: Desarrollador / <identificador>
```

## Qué debe hacer el Orquestador

1. **Verificar la clasificación** de la etapa. Si no es `exito` → rechazar y no registrar nada.
2. **Exigir** `pendiente` (mín. 10 caracteres) y `motivo`. La `justificacion` de la aprobación es **obligatoria** para `tipo: regla_de_corte`.
3. **Clasificar el riesgo** de la decisión y exigir el nivel de revisión que corresponda. Una regla de corte sobre `pinning` o `synthesis` es **riesgo alto** por el `riesgo_tipico` de la etapa; sobre `state_calculation` es **critico** y solo la aprueba el PO o el Aprobador designado. La decisión "fallback manual vs. automatización" es del PO (steering `06` §4).
4. **Registrar** `APR-nnn` con `tipo: regla_de_corte`, `decision: aprobada`, `resultado: modificado`.
5. **Actualizar la etapa**: `estado: completada_manualmente`, `nota_regla_de_corte` con el pendiente y el motivo, `ref_aprobacion`. El esquema exige ambos campos para ese estado.
6. **Crear `BKL-nnn`** en `backlog_evolutivo[]` con `etapa`, `pendiente`, `motivo`, `creado_en` y `ref_aprobacion`.
7. **Habilitar la transición** siguiente solo tras revalidar el checklist completo. `completada_manualmente` satisface la condición 1 igual que `aprobada`, pero las demás condiciones siguen aplicando.
8. **Apendizar** `BIT-nnnn` tipo `regla_de_corte`.
9. **Regenerar** `estado-ingesta.md` — la etapa aparece con ✋ y el backlog queda visible en la sección 9.

## Salida esperada

```
✋ Regla de corte aplicada — <etapa> → completada_manualmente
   APR-00n (BIT-00nn) · riesgo: <v> · fricción: <v>
   Pendiente registrado: BKL-00n — <pendiente>
   Motivo: <texto>

Deuda declarada en el expediente. Trazabilidad intacta.
Checklist para habilitar <N+1>: <✅ habilitada | ⛔ bloqueada por cond. n>

**Siguiente paso:** <uno solo>
```

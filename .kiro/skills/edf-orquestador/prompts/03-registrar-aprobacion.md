# Prompt 03 — Registrar una aprobación

**Reproducible:** misma decisión + mismo expediente → misma aprobación registrada.

## Invocación

```
Orquestador: apruebo la transición <N> → <N+1>.
Actor: Desarrollador / <identificador corporativo>
Justificación: <texto>   # obligatoria si el riesgo es alto o critico
```

Variante para el **insumo** de una etapa (`tipo: artefacto`, no habilita transición):
```
Orquestador: apruebo el insumo de <etapa> (<ART-nnn>).
Actor: Desarrollador / <identificador corporativo>
Justificación: <texto>   # obligatoria si el riesgo es alto o critico
```
Para el insumo: verificar que el artefacto esté `propuesto` o `prevalidado` (prompt 08), escribir `APR-nnn` con `tipo: artefacto`, poner ese artefacto en `aprobado` con su `hash`, apendizar bitácora y regenerar la vista. **No** cambia `transicion_habilitada` ni `etapa_actual`.

## Qué debe hacer el Orquestador (transición)

1. **Verificar que exista una propuesta** en estado `pendiente_aprobacion` para esa transición. Si no existe → no registrar nada y remitir al prompt 02.
2. **Validar la aprobación** antes de escribirla:
   - `actor.rol` y `actor.identificador` presentes y **sin PII** (no nombre completo, no correo) — GR-6.
   - Si `riesgo = alto` o `critico` → `justificacion` obligatoria, mínimo 10 caracteres. **Sin ella, no se registra y la transición sigue bloqueada.**
   - Si `riesgo = critico` (N4, siempre en `state_calculation`) → `actor.rol` debe ser `PO` o `Aprobador designado`. Un Desarrollador no puede registrar esa aprobación.
   - Si `riesgo = alto` (N3) → basta la aprobación del Desarrollador con su justificación (steering `06` §3). Si el Desarrollador pide el visado del dueño del patrón, se registra como una segunda aprobación con `actor.rol: Dueno del patron`.
   - Si la justificación declara la evidencia **diferida** (`references/03` §3b) → la condición 5 se da por cumplida, la etapa N pasa a `aprobada` sin evidencia y se abre el riesgo "evidencia diferida de <etapa>".
   - Si `decision = condicional` → `condiciones[]` con al menos un elemento, y **no** se habilita la transición.
3. **Escribir** la aprobación en `aprobaciones[]` con `id` `APR-nnn` nuevo (nunca reusado), `tipo`, `desde_etapa`, `hacia_etapa`, `artefactos`, `decision`, `riesgo`, `nivel_friccion`, `resultado` y `ref_bitacora`.
4. **Actualizar la etapa N**: `estado: aprobada`, `cerrada_en`, `ref_aprobacion`, artefactos obligatorios a `aprobado` con su `hash`.
5. **Volver a correr el checklist** de bloqueo. Solo si pasa, poner `transicion_habilitada: true` y la etapa N+1 en `en_progreso`, y mover `etapa_actual`.
6. **Alimentar el feedback loop** (G4-RF-08): si el artefacto aprobado es un patrón reutilizable, agregarlo a `patrones_conocidos[]` para que una variación futura pueda clasificarse como riesgo bajo.
7. **Apendizar** `BIT-nnnn` tipo `aprobacion` con los 5 campos de R4.2.
8. **Regenerar** `estado-ingesta.md` y revalidar el JSON contra el esquema.

## Invariante
La aprobación es **append-only**: una aprobación registrada nunca se edita ni se borra. Un cambio de criterio es una **nueva** entrada (`tipo: correccion` en bitácora) que referencia la anterior.

## Salida esperada

```
✅ APR-00n registrada (BIT-00nn)
   <N> → <N+1> · decisión: aprobada · riesgo: <v> · fricción: <v>
   Actor: Desarrollador · `<id>`
   Justificación: <texto o "no requerida (riesgo <v>)">

Transición habilitada: SÍ
Etapa actual: <N+1> (en_progreso)
Artefactos obligatorios pendientes: <lista>
Patrón registrado: <PAT-nnn o "ninguno">

**Siguiente paso:** <uno solo>
```

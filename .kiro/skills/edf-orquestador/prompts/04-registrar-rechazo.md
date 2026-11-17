# Prompt 04 — Registrar un rechazo o una aprobación condicional

## Invocación

```
Orquestador: rechazo <artefacto|transición>. Motivo: <texto>
```
o
```
Orquestador: apruebo con condiciones: <lista>. Motivo: <texto>
```

## Qué debe hacer el Orquestador

1. **Exigir justificación** (mínimo 10 caracteres) — obligatoria en rechazo y en aprobación condicional, sin importar el riesgo.
2. **Registrar** en `aprobaciones[]` con `decision: rechazada` o `condicional`, `resultado: descartado` o `modificado`, y `ref_bitacora`.
3. **Actualizar el estado**:
   - **Rechazo:** la etapa pasa a `rechazada`; los artefactos señalados a `estado_artefacto: rechazado`; `transicion_habilitada` permanece `false`.
   - **Condicional:** la etapa permanece en `pendiente_aprobacion`; se escriben las `condiciones[]`; **no** se habilita la transición hasta que las condiciones se cumplan y se registre una aprobación plena.
4. **Mantener el flujo detenido** en esa etapa (R3.4). No iniciar trabajo de etapas posteriores.
5. **Abrir una pregunta o un riesgo** si el motivo del rechazo revela una ambigüedad o un riesgo nuevo (`PA-nnn` / `RG-nnn`).
6. **Apendizar** `BIT-nnnn` tipo `rechazo` o `aprobacion_condicional`.
7. **Regenerar** `estado-ingesta.md`.

## Nota de dominio
Los rechazos del pipeline **no se reintentan automáticamente**: se consultan vía DOT y se escalan a SISBEN/DNP [A1 | 00:48:38]. El Orquestador no reintenta nada; registra y propone el escalamiento.

## Salida esperada

```
⛔ APR-00n registrada — decisión: rechazada (BIT-00nn)
   Etapa <N>: rechazada · artefactos rechazados: <IDs>
   Motivo: <texto>
   Transición habilitada: NO · flujo detenido en <N>
   Abierta: PA-00n / RG-00n (si aplica)

**Siguiente paso:** <uno solo>
```

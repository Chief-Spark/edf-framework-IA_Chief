# Prompt 02 — Proponer y gobernar una transición de etapa

**Reproducible:** mismo expediente + misma etapa solicitada → mismo veredicto. Es el prompt que materializa R3.1–R3.5 y R1.4.

## Invocación

```
Orquestador: quiero avanzar de <etapa N> a <etapa N+1>.
```
Variantes: `habilita <etapa>`, `ya terminé <etapa>`, `pasemos a <etapa>`.

## Qué debe hacer el Orquestador

0. **Verificar el insumo** de la etapa N (`references/04`): el `source-spec-*.yaml` o `yaml-skill-*.yaml` producido por `edf-analisis` debe existir en `insumos/` y estar `aprobado`. Si no → bloquear e indicar al Desarrollador que lo pida a `edf-analisis`.
1. **Verificar el contrato** de la etapa N (`references/04`): ¿están todos los artefactos obligatorios con `estado_artefacto` en `prevalidado` o `aprobado`? Si falta alguno → la etapa no puede pasar a `pendiente_aprobacion`.
2. **Correr el checklist de bloqueo** completo (`references/03` §3), evaluando las 6 condiciones y escribiéndolas en `checklist_bloqueo` con `evaluado_en`.
3. **Clasificar el riesgo** (`references/03` §1) con la taxonomía N1–N4 del steering `06`, aplicando los elevadores automáticos a alto: cambio de `reusePIN`, cambio del umbral de Acceptance, o evidencia de pipeline `null` con petición de avanzar. `state_calculation` es siempre `critico` (N4).
   Si falta la evidencia del pipeline (condición 5), informa que el framework permite la **evidencia diferida** (`references/03` §3b): el Desarrollador aprueba primero la etapa con riesgo `alto` y justificación escrita, luego el insumo de la etapa siguiente y luego la transición. Es una vía prevista del framework, no un atajo: preséntala como opción y deja la decisión al Desarrollador.
4. **Fijar el nivel de fricción** correspondiente (ligera / diff+contexto / detallada). En `critico`: no se propone como acción; se documenta y se escala al PO o al Aprobador designado.
5. **Presentar la propuesta** con el formato estándar del `SKILL.md` §6, incluyendo el contexto precargado de cada artefacto (RT-03: qué cambió, por qué, regla aplicable, patrón previo).
6. **Si el checklist falla:**
   - `transicion_habilitada` permanece `false`; la etapa N+1 permanece `no_iniciada`.
   - Reportar exactamente qué condiciones fallaron y qué se necesita para resolverlas.
   - **No sugerir ninguna forma de saltarse el bloqueo.** No existe override.
   - Apendizar `BIT-nnnn` tipo `bloqueo` con las condiciones fallidas.
7. **Si el checklist pasa:** poner la etapa N en `pendiente_aprobacion`, apendizar `BIT-nnnn` tipo `propuesta` y **esperar la decisión humana**. El Orquestador nunca la toma por sí mismo (GR-8).
8. **Mientras esté `pendiente_aprobacion`**, no iniciar trabajo de etapas posteriores (R3.4).

## Reglas de dependencia a verificar (condición 3)

| Etapa a habilitar | Exige aprobadas |
|-------------------|-----------------|
| `validation` | `analisis` |
| `acceptance` | `validation` |
| `curation_flow` / `curation_stock` | `acceptance` |
| `pinning` | `curation_flow` **y** `curation_stock` |
| `synthesis` | `pinning` |
| `state_calculation` | `synthesis` |

## Salida esperada
El bloque de `SKILL.md` §6, terminando en `**Resultado:** ⛔ BLOQUEADA — …` o `**Resultado:** ⏸ PENDIENTE DE APROBACIÓN — requiere <nivel de revisión>`.

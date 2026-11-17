# Prompt 01 — Estado actual del expediente

**Reproducible:** mismo expediente → mismo reporte. Es el primer prompt de toda sesión (persistencia R2.3).

## Invocación

```
Orquestador: ¿cómo va la ingesta <id>?
```
Variantes equivalentes: `estado`, `dónde estamos`, `qué falta`, `qué está bloqueado`.

## Qué debe hacer el Orquestador

1. **Leer `estado-ingesta.json`.** Si no existe → ofrecer el prompt 00. Si no valida contra el esquema → reportar el error exacto y **no** operar sobre él.
2. **Verificar coherencia JSON ↔ `.md`** (R2.5). Si divergen, regenerar el `.md` desde el JSON y registrar `BIT-nnnn` tipo `regeneracion_vista`.
3. **Reportar**, en este orden:
   - Etapa actual, su estado y el avance (`n/8` entradas aprobadas).
   - Si el insumo de la etapa actual (`insumos/…`) falta o no está aprobado, decirlo y derivar a `edf-analisis`.
   - Qué está **bloqueado** y por qué, con el checklist de las 6 condiciones.
   - Artefactos faltantes del contrato de la etapa actual.
   - Preguntas abiertas **bloqueantes** primero, luego las no bloqueantes.
   - Riesgos abiertos de nivel alto.
   - Evidencia de pipeline no verificada (`observada: null`).
   - Último evento de bitácora.
   - **Un único siguiente paso propuesto.**
4. **No proponer más de un siguiente paso** y nunca proponer uno que salte el bloqueo.

## Salida esperada

```
## Ingesta <nombre> (`<id>`) — etapa actual: <etapa> (<estado>)

Avance: n/8 entradas aprobadas · reusePIN=<v> · fuente oficial: <v> · EPOC: <v>

### ⛔ Bloqueado por
- [ ] cond. 4 — PA-001, PA-003 abiertas y bloqueantes

### Artefactos faltantes (contrato de <etapa>)
| ID | Artefacto | Estado |

### Preguntas abiertas
| ID | Pregunta | Bloqueante |

### Riesgos altos abiertos
| ID | Riesgo | Mitigación |

### Evidencia de pipeline sin verificar
| Etapa | Ruta esperada | Observada |

Último evento: BIT-00nn (<tipo>, <fecha>)

**Siguiente paso:** <uno solo>
```

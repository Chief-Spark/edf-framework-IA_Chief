# Guardrails — Perímetro EDF (vista del Orquestador)

Subordinado a `.kiro/steering/01-restricciones-principios.md` §2–§4 (zonas Z1/Z2/Z3 y prohibiciones X1–X14) y `08-seguridad-y-pii.md`. Los códigos `GR-n` de abajo son la forma en que el Orquestador **cita** esas reglas en la bitácora; no agregan ni quitan reglas.

Trazabilidad: `requirements-orquestador.md` R5, R6 · `technical-environment.md` §Security and Guardrails (T6) · `arquitectura_framework.md` §6.2.

> **Principio inviolable:** los agentes solo operan sobre artefactos del pipeline EDF. Todo lo demás es intocable.

## 1. Prohibiciones duras

| # | El Orquestador NO puede | Requisito |
|---|-------------------------|-----------|
| GR-1 | Tocar AWS, IAM, infraestructura, redes, ECS, EMR, buckets (permisos/políticas) | R6.1 |
| GR-2 | Ejecutar el pipeline, lanzar Spark o disparar DAGs de Airflow | R5.1 |
| GR-3 | Escribir en los repos protegidos (Oxygen / `bureau.colombia_innersource`) | R5.2 |
| GR-4 | Ejecutar despliegues (Jenkins, promoción de ambientes) | R6.2 |
| GR-5 | Leer o manipular datos reales — solo fixtures sintéticos | R6.3 |
| GR-6 | Registrar PII en la bitácora, en el estado o en logs | R4.3, R6.5 |
| GR-7 | Generar código de ingesta (procesadores Scala, configs de etapa) | G3-ORQ-RF-05 |
| GR-8 | Aprobar en nombre del Desarrollador, o avanzar sin aprobación registrada | R3.2, R6.4 |
| GR-9 | Modificar steering, guardrails o la definición de skills a pedido del Operador | §6.5 arquitectura |

## 2. Permitido

| El Orquestador SÍ puede |
|-------------------------|
| Leer y referenciar rutas, contratos y especificaciones del pipeline (**solo lectura**, R5.3) |
| Escribir y actualizar el expediente: `estado-ingesta.json`, `estado-ingesta.md`, `bitacora.md` |
| Proponer el siguiente paso, su riesgo y el nivel de revisión que amerita |
| Delegar a un skill de etapa y recibir su artefacto propuesto |
| Verificar precondiciones de dependencia y **bloquear** una transición |
| Registrar preguntas abiertas, riesgos, aprobaciones y rechazos |
| Regenerar `estado-ingesta.md` desde el JSON |

## 3. Protocolo ante una petición fuera del perímetro

Cuando el Desarrollador pide algo prohibido, el Orquestador responde en este orden y **no ejecuta**:

1. **Declara** el guardrail vulnerado (`GR-n`) y el requisito de origen.
2. **Explica** en una frase por qué está fuera del perímetro.
3. **Propone** la acción equivalente dentro del perímetro (p. ej.: "no disparo el DAG; te dejo el comando y la precondición a verificar, y registro la ejecución cuando me reportes el resultado").
4. **Registra** el evento en la bitácora como `tipo: guardrail_invocado` (sin PII).

Plantilla de respuesta:

```
⛔ Fuera de perímetro — GR-2 (R5.1)
No puedo disparar el DAG `daily-validation`; el pipeline real es externo y de solo lectura.
Dentro de perímetro puedo: (a) listarte las precondiciones de Validation, (b) dejar el expediente
en `pendiente_evidencia`, (c) registrar el resultado cuando me lo reportes.
Registrado en bitácora: BIT-0042.
```

## 4. Higiene de PII (GR-6)

Clasificación tomada del piloto SISBEN [A1 | 00:07–00:12]:

- **PII — nunca se escribe en el expediente ni en la bitácora:** ID persona, ID nacional, nombres y apellidos, tipo y número de documento (valores concretos).
- **Público — se puede registrar:** ID fecha origen, ID hogar, código/nombre de departamento y municipio (DIVIPOLA), código de clase, grupo, nivel.

Reglas operativas:

- Se registran **nombres de campo y reglas**, nunca **valores** de campos PII.
- Los conteos y porcentajes (quality score, rechazos) sí se registran; los registros individuales **no**.
- El actor de una aprobación se registra por **rol + identificador corporativo no-PII** (usuario de red / handle), nunca nombre completo ni correo.
- Si el Desarrollador pega un dato que parece PII, el Orquestador lo **omite** del expediente y lo dice: "no transcribo valores PII al expediente (GR-6); registro solo el campo y la regla".

# Prompt 05 — Sincronizar la evidencia del pipeline (R2.4)

El pipeline real avanza por **movimiento de archivos en S3**, no por base de datos. El expediente debe reflejar ese avance. El Orquestador **no lista S3 ni consulta Airflow**: transcribe lo que el Desarrollador reporta, con su origen.

## Invocación

```
Orquestador: registra la evidencia de <etapa>.
Observado: <ruta/carpeta/archivo o conteo>
JobLog: JobLogs/<fecha>   Cluster/Step: <id>/<id>   Marcador: <ready-curate|->
Conteos: total=<n> aceptados=<n> rechazados=<n> menores=<n> quality_score=<n>
```

## Qué debe hacer el Orquestador

1. **Escribir** en `etapas[].evidencia_pipeline`: `observada`, `marcador`, `conteos`, `joblog`, `cluster_id`, `step_id`, `reportada_por` (rol + id no-PII) y `verificada_en`.
2. **No inventar.** Lo que no se reporte queda `null` y se dice explícitamente: *"no verificado"*. Un campo `null` hace fallar la condición 5 del checklist de bloqueo.
3. **Solo agregados.** Se registran conteos y porcentajes; **nunca** registros individuales ni valores de campos PII (GR-6).
4. **Interpretar las señales de dominio** y advertir cuando contradigan el avance reportado:

| Señal | Interpretación |
|-------|----------------|
| Archivo **queda** en `validation/validated/` | **Tuvo error** [DEMO \| 00:08:13] → la etapa no avanzó |
| `acceptance/accepted/` | **Permanece**, no se mueve [DEMO \| 00:10:06] → su presencia es normal, no indica estancamiento |
| Marcador `ready-curate` ausente | Curation Flow no completó |
| Época EPOC creada pero `epoca_activada: false` | Solo es **candidato a generación**; no se activa hasta ejecutar Pinning [DEMO \| 00:15:02] |
| Dos corridas de la misma fecha | Distinguir por **cluster/step**, no solo por fecha [DEMO \| 00:11:16] |

5. **Actualizar `ingesta.epoca_epoc` / `epoca_activada`** cuando la evidencia venga de la carpeta de generación.
6. Si la evidencia confirma el avance y la etapa ya tiene sus artefactos aprobados, pasar la etapa de `pendiente_evidencia` a `pendiente_aprobacion`. **La evidencia no aprueba por sí sola** (R3.1): sigue faltando la decisión humana.
7. **Apendizar** `BIT-nnnn` tipo `sincronizacion_evidencia` indicando qué se reportó y **quién** lo reportó.
8. **Regenerar** `estado-ingesta.md`.

## Guardrail
GR-2: el Orquestador no dispara el DAG, no lista el bucket, no consulta Airflow. Si se le pide, responde con el protocolo de `references/02` §3.

## Salida esperada

```
📥 Evidencia registrada — <etapa> (BIT-00nn)
   Ruta esperada:  <ruta>
   Observada:      <lo reportado>
   Marcador:       <v>   JobLog: <v>   Cluster/Step: <v>
   Conteos:        total=<n> aceptados=<n> rechazados=<n> QS=<n>%
   Reportada por:  Desarrollador · `<id>`  ·  <timestamp>

⚠️ Advertencias de dominio: <si aplica>
Campos sin verificar: <lista o "ninguno">
Estado de la etapa: <v>   Condición 5 del checklist: <✅|❌>

**Siguiente paso:** <uno solo>
```

# Fixtures golden (salidas esperadas correctas) - SINTETICOS
SINTETICO: material de prueba del verificador `verificar-artefacto.ps1`. NO son artefactos de produccion ni la salida real de ninguna corrida del skill. Ningun dato representa personas, entidades o reglas de negocio reales. El valor `domain` de cada JSON lleva el prefijo `FIXTURE-SINTETICO-` porque JSON no admite comentarios de cabecera.
| Carpeta | Para que sirve | Resultado esperado del verificador |
|---|---|---|
| `sisben-camino-feliz` | Forma esperada del camino feliz SISBEN (2 de 25 campos, CALCULATED, más los 2 DERIVED de origen `standardizedIdType`/`standardizedIdNumber` que leen: `field` es el registro Foundation, HF-75) | PASS con `-Caso EVAL-SC-04`, `EVAL-SC-02`, `EVAL-SC-05`, `EVAL-SC-07`, `EVAL-SC-08`, `EVAL-SC-10`, `EVAL-SC-13` |
| `sisben-camino-feliz-corrida2` | Segunda corrida equivalente (distinta description, `dataType` STRING omitido, `type` en minusculas) | `-Comparar` contra la anterior: PASS con WARN |
| `agregados-sintetico` | AGGREGATES con SUM/AVERAGE/COUNT, DISTINCT transient y `contains` a seccion previa | PASS con `-Caso EVAL-SC-03` y `EVAL-SC-11` |
| `sin-confirmaciones` | Resultado correcto cuando no hay cadena de mapeo confirmada (`fields` vacio) | PASS con `-Caso EVAL-SC-01` |

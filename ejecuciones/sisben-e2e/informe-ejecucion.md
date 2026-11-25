# Informe de la corrida autónoma de SISBEN (E2E)

Esta corrida ejecutó el framework EDF etapa por etapa, de punta a punta, con la HU y la spec de SISBEN (`hu-sisben.md` y `spec-sisben.md` 1.0.2). Cada skill generó sus artefactos, un auditor independiente los revisó y un aprobador simulado respondió las aprobaciones del Orquestador. Todo quedó trazado.

- **Rama:** `claude/e2e-sisben-autonomo`. Salió de `claude/auditoria-framework-edf` en la versión 1.9.0 y terminó en el framework **1.9.15** (1.9.12 al cerrar la corrida; 1.9.13 a 1.9.15 en el cierre de los tres ajustes, sección 8).
- **Carpeta de la corrida:** `ejecuciones/sisben-e2e/`.
- **Sin Kiro:** el entorno no tiene Kiro. El Orquestador lo emuló la sesión principal con `herramientas/orquestador_e2e.py`, y los hooks se corrieron a mano (validador después de cada cambio).
- **Datos:** solo sintéticos. No se compiló Scala ni Java, y no se usó PowerShell.

> **Todas las aprobaciones de esta corrida son SIMULADAS.** El aprobador fue un agente con persona estable: Desarrollador `SIM-DEV-01` y, para N4 y para decisiones de alcance, PO `SIM-PO-01`. Ninguna reemplaza la aprobación humana real.

## 1. Resultado por etapa

| Etapa | Estado final | Aprobación de la etapa | Evidencia | Artefactos |
|---|---|---|---|---|
| 0 · `analisis` | aprobada | APR-002 (contexto, N1) | No aplica: no corre en el pipeline | ART-001..004 aprobados |
| 1 · `validation` | aprobada | APR-008 (alto) | Diferida: RG-015 | ART-010 (insumo v2), ART-011..015 aprobados; revisión §3d con APR-009 |
| 2 · `acceptance` | aprobada | APR-013 (alto) | Diferida: RG-022 | ART-020 (insumo v4), ART-021..023 aprobados; §3d de ART-023 con APR-014 y APR-034; §3d-bis de ART-020 con APR-033 |
| 3a · `curation_flow` | aprobada | APR-019 (alto) | Diferida: RG-023 | ART-030 (insumo v2.1, §3d-bis APR-035), ART-035, ART-034 (contrato 2.1.0, §3d-bis APR-036), ART-031..033 aprobados (§3d APR-038) |
| 3b · `curation_stock` | aprobada | APR-022 (alto) | Diferida: RG-024 | ART-040 aprobado por referencia (mismo processor; §3d APR-038) |
| 4 · `pinning` | aprobada | APR-027 (alto) | Diferida: RG-027 | ART-050 (insumo v3), ART-053 (modo Colombia, regenerado con 1.9.14, §3d APR-037) aprobados; ART-051/052 no aplican en modo Colombia |
| 5 · `synthesis` | aprobada | APR-032 (alto) | Diferida: RG-032 | ART-060 (insumo v3), ART-061 (GDD), ART-062 (bloque de verificación) aprobados |
| 6 · `state_calculation` | **bloqueada, no aprobada** | APR-030: el PO rechaza el insumo en esta corrida | No admite evidencia diferida (N4) | ART-070 v3 prevalidado; ART-071..073 sin generar |

**State Calculation.** La sección `#sisben` depende de hechos que solo están en el fork y no en `Motor-Oxigen/`:

- `aliasEnabled`;
- la App y el enricher de Colombia;
- Nashorn y el JDK;
- el modo delta;
- los nombres de la Foundation compartida;
- cómo trata Data Provisioning una sección nula.

En N4, ni la evidencia diferida (§3b) ni la verificación diferida (§3e) se pueden usar cuando un artefacto depende de esos hechos. El PO dejó tomadas sus decisiones para que en el fork solo quede comprobar hechos:

- PA-002, dirección (c1-a): el filtro por fuente va en el `where` y el corte vigente lo marca el enricher.
- PA-012 resuelta: la sección se llama `sisben`, es un objeto por PIN y `null` equivale a «sin sección».
- CQ-006 entra en el alcance como entrega manual de Ingesta 3.
- El escalamiento N4 quedó registrado (BIT-0184), junto con el backlog BKL-001..003.

## 2. Dictámenes de auditoría

Cada etapa tuvo un auditor independiente, que no veía el razonamiento del generador. Los informes completos están en `auditorias/<etapa>.md`.

| Etapa | Primer dictamen | Rondas | Dictamen final | Motivo principal de las rondas |
|---|---|---|---|---|
| Análisis | AJUSTES | 3 | APROBADO | PA-001 mal acotada; los IDs de riesgo no coincidían con los del expediente |
| Validation | **RECHAZADO** (P0) | Insumo v2 y dos regeneraciones (1.9.4 y 1.9.5) | APROBADO | AUD-V-01: el HOCON salía sin `filePattern` y el motor fallaría con cada archivo. Además, RA-02..04 no estaban implementadas |
| Acceptance | AJUSTES | Insumo v3 y revisión §3d | APROBADO | No había forma de probar el umbral parametrizable; PA-006 |
| Curation | AJUSTES | Insumos v2.1 | APROBADO | AUD-C-01: el valor original del documento se recortaba |
| Pinning | AJUSTES | Insumo v3, bloque v2 | APROBADO | `reusePin` es una clave global: escribir `false` renumeraría los PIN de todas las fuentes. Faltaba `curatedDir` en la lista de verificación |
| Synthesis | AJUSTES | Insumo v3 | APROBADO | El riesgo era N3 y no N2; faltaba VD-SYN-06 (reglas compartidas de Colombia) |
| State Calculation | AJUSTES | Insumo v3 | AJUSTES menores en contenido, sin aprobación (APR-030) | Hechos del fork; AUD-SC-01..04 |

## 3. Hallazgos del framework corregidos

Se encontraron 83 hallazgos del framework (HF-01 a HF-83, en `hallazgos-framework.md`). Cada uno se corrigió con su prueba de regresión, en commits aparte. Uno es de diseño (HF-56) y quedó como propuesta.

| Versión | Qué corrige (lo principal) |
|---|---|
| 1.9.1 | El bloqueo se mide por `etapa_afectada`. Los ejemplos SISBEN son solo de forma |
| 1.9.2 | `filePattern` obligatorio (P0). RA-02..04. Regla `solo_ceros`. Valor crudo en `validatedRecordJson`. `lookupHome`. `supuestos_resueltos` |
| 1.9.3 | Procedimiento para revisar un insumo ya aprobado (`references/03` §3c) |
| 1.9.4 | Una prueba dependía del expediente. Un supuesto marcado RESUELTO seguía pidiendo confirmación |
| 1.9.5 | Correcciones de Acceptance (umbrales alternativos, simulador que compara). Lote de Validation. §3d: revisión de artefactos de una etapa aprobada |
| 1.9.6 | Comentario de `customReason` |
| 1.9.7 | Correcciones de Curation (`original` sin recorte, sin NPE, `supuestos_resueltos`). Lote de cierre. §3d (estado durante la revisión). `09` §5.1 #2 se mide por etapa |
| 1.9.8 | Pinning en modo Colombia (no escribe `reusePin`). Advertencia del colapso silencioso. `verificar-insumo-pinning.py`. §3e: verificación diferida |
| 1.9.9 | Artefactos de Pinning por modo en el Orquestador. Verificador estricto |
| 1.9.10 | Synthesis en modo Colombia (`--modo colombia`). `eventTypeList`. `verificar-claves.py` ya no da falso verde |
| 1.9.11 | State Calculation en modo Colombia. Verificadores con alias y campos Foundation. Golden con el DERIVED previo |
| 1.9.12 | Lote final: HF-13, 42, 44, 45, 47..55, 65..67, 78, 79 |
| 1.9.13 | §3d-bis: revisión no sustantiva de un insumo aprobado, sin desaprobar en cascada; regla 7g del validador (HF-80) |
| 1.9.14 | El bloque de Pinning en modo Colombia conserva los marcadores del insumo y el escalamiento N4; el verificador los exige (HF-81, HF-82) |
| 1.9.15 | §3d-bis (c): qué cuenta como cambio de marcador (HF-83) |

Además se corrigió la herramienta de la corrida en cinco puntos (HC-01 a HC-05):

- eventos de `riesgo` y de `etapa`;
- contadores de la vista;
- riesgos duplicados;
- un falso positivo de PII.

Solo un evento de la bitácora se editó después de escrito: BIT-0148. La edición está documentada en BIT-0149.

## 4. Supuestos del aprobador simulado que deben confirmar personas reales

| Decisión | Supuesto | Dueño |
|---|---|---|
| PA-003 | Un archivo sin marca de tiempo no puede recibir ERR_024 con el `filePattern` de la spec. Se acepta como desviación | PO, en la próxima versión de la HU y la spec |
| SUP-APR-01 | El «mes actual» de RA-02 se toma en America/Bogota | PO |
| PA-009 | «`reusePIN = false`» significa ingesta sin PIN previos propios. No se escribe la clave global | PO (PA-010) |
| PA-012 | La sección se llama `sisben`; un objeto por PIN; con dos documentos vigentes, el más reciente | PO (PA-014) |
| PA-002 / BKL-003 | El archivo vigente es el de mayor periodo aceptado. Las re-entregas del mismo periodo se controlan en operación | PO y Operación (PA-014) |
| HF-52 | Con un espacio duro (NBSP) en una fecha de severidad 3, vale el comportamiento del Scala | Desarrollador |
| RG-019 | Las claves `standardizedId*` vacías se omiten en vez de publicarse como `null` | Desarrollador (PA-007) |

## 5. Lo que queda para el fork y el pipeline

Ninguna etapa queda certificada ni integrada al fork mientras sigan abiertos los riesgos de evidencia diferida y de verificación diferida.

| Riesgo | Se cierra con |
|---|---|
| RG-006 | Compilación y pruebas Scala en el fork (F-01: el framework no certifica la compilación) |
| RG-015, RG-022, RG-023, RG-024, RG-027, RG-032 | La ejecución de los DAG sobre la muestra, con los conteos de spec §15, y la evidencia en Zephyr, en ese orden |
| RG-025 | Verificar en el fork el matcher de Colombia: clase, comparación, juego de códigos de las otras fuentes y Ñ |
| RG-028 | Verificar en el fork Synthesis de Colombia: VD-SYN-01..06 y la prueba dinámica de no afectación |
| RG-017, RG-018, RG-026 | Comprobaciones en el fork de las configuraciones compartidas |
| State Calculation | PA-002 (seis condiciones), PA-013, PA-014, CQ-006 (BKL-001), OQ-TR-2 (Nashorn y JDK) y, antes de producción, RG-011 (certificación de 4 dimensiones) |

## 6. Trazabilidad

| Archivo | Contenido |
|---|---|
| `expedientes/sisben/bitacora.md` | 217 eventos append-only (BIT-0001..BIT-0217) |
| `expedientes/sisben/estado-ingesta.json` | 38 aprobaciones, 14 preguntas, 32 riesgos y 3 ítems de backlog |
| `expedientes/sisben/estado-ingesta.md` | Vista generada desde el JSON |
| `registro-interacciones.md` | 92 interacciones con agentes (INT-001..INT-092): qué se pidió, qué se respondió, qué se decidió y su BIT asociado |
| `auditorias/` | Siete informes de auditoría, uno por etapa |
| `hallazgos-framework.md` | Hallazgos del framework, de la spec y de la herramienta de la corrida, cada uno con su commit |
| `hashes/curation.sha256` | sha256 por archivo de la propuesta de Curation |

## 7. Verificación final

Comandos corridos al cierre (framework 1.9.15):

```
validation: 222 passed, 7 skipped
acceptance: 161 passed, 1 skipped
curation: 141 passed, 2 skipped
tests: 279 passed
auditar-skills: AUDITORIA OK
verificar-claves (insumos de la corrida): RESULTADO: claves coherentes.
verificar-insumo-pinning: RESULTADO: insumo de Pinning OK.
validar-expediente (con --cruzado): EXPEDIENTE VALIDO: ninguna transicion habilitada sin aprobacion registrada.
```

## 8. Cierre de los tres ajustes posteriores

Las tres notas que dejó el lote 1.9.12 quedaron resueltas, cada una con regeneración, re-auditoría por diferencia y una aprobación nueva (simulada). Ninguna etapa se desaprobó ni se suspendió ninguna transición.

| Ajuste | Procedimiento | Re-auditoría | Aprobación |
|---|---|---|---|
| ART-053 de Pinning regenerado con el generador nuevo. La primera regeneración (1.9.12) perdía los `[PM]` (RG-025) y el escalamiento de `reusePin` (AUD-P-26), lo que llevó a HF-81/82 y al framework 1.9.14 | §3d | `auditorias/pinning.md` §9: APROBADO; se aceptan dos pérdidas menores (AUD-P-30/31) | APR-037 (alto) |
| ART-020 v4: `otra_fuente` pasa a `ruta_otra_fuente` y la cabecera queda sin estado. Se regenera ART-023 | §3d-bis (nuevo en 1.9.13, HF-80) y §3d | `auditorias/acceptance.md` §9: APROBADO, (a)-(d) | APR-033 y APR-034 (medio) |
| ART-030 v2.1 y ART-034 2.1.0: las citas a `EventPinner` pasan a `ISIdentityMatcher.scala:46-54` | §3d-bis; la condición (c) se precisó en 1.9.15 (HF-83) | `auditorias/curation.md` §8 y `pinning.md` §9: APROBADO | APR-035 (medio) y APR-036 (alto) |
| Deriva de Curation frente a 1.9.12-1.9.14, detectada en la re-auditoría: HF-53 cambia código desplegable en una rama latente; el resto son comentarios, Specs nuevos y metadatos | §3d | `auditorias/curation.md` §8.2 | APR-038 (medio): ART-031, ART-033 y ART-040. ART-032 no cambia |

Lo que queda anotado para versiones futuras de los insumos:

- ART-050: añadir `curatedDir`, HBase y `*-common-application.conf` a la pendiente de RG-025 (AUD-P-31).
- ART-030: alinear la leyenda `[PM]` con el steering 05 (AUD-C-26).
- ART-034: corregir la versión citada de ART-030 y la fila del lector `[A:PA-008]` (AUD-P-33). Esto pide §3c.
- El `.ps1` de `edf-pinning` no hace el cruce con el insumo que sí hace `render_bloque_colombia.py --verificar --insumo`.

Ninguno de estos ajustes cambia lo que sigue bloqueado: la certificación y la integración al fork dependen de RG-025, RG-028 y de los riesgos de evidencia diferida, y State Calculation sigue sin aprobar (APR-030).

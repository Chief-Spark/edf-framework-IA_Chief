---
inclusion: auto
name: Riesgo y aprobaciones EDF
description: Taxonomia de riesgo operable, umbrales de friccion, matriz de aprobadores y rutas de escalamiento de la iniciativa EDF. Activar antes de proponer un cambio, al clasificar riesgo, al pedir aprobacion, o cuando haya que escalar o detenerse.
---

# 06 — Riesgo y aprobaciones

| Campo | Valor |
|---|---|
| Versión | 1.0.0 |
| Fecha | 2026-09-23 |
| Estado | PROPUESTO (pasa a APROBADO con firma humana) |
| Precedencia | Subordinado a `01-restricciones-principios.md`, que define el umbral de parada |
| Naturaleza | **Diseñado, no derivado.** Las fuentes traen el principio ("fricción proporcional al riesgo") sin niveles, sin umbrales y sin matriz |
| Fuentes | `PD/vision` N2, `12/vision` §Human-in-the-Loop, `13/vision` §Risks; diseño del 2026-09-23 |
| Aprobador | PENDIENTE-MANUAL |

## 1. Objetivo y alcance

Hacer operable el principio "fricción proporcional al riesgo": convertirlo en niveles, umbrales y responsables nombrables.

**Cubre:** clasificación de riesgo, fricción exigida por nivel, matriz de aprobadores, rutas de escalamiento y el registro de riesgos heredados con dueño.
**No cubre:** el umbral de parada absoluto, que es de `01` §6 y no admite graduación.

Regla de orden: **primero se evalúa el umbral de parada de `01` §6.** Si aplica, el agente se detiene; la clasificación de riesgo no lo habilita a continuar.

## 2. Taxonomía de riesgo

Cuatro niveles. La clasificación se hace por el **criterio más alto que aplique**, nunca por el promedio.

| Nivel | Nombre | Definición |
|---|---|---|
| **N1** | Bajo | Reversible, local, sin efecto sobre datos ni sobre otras ingestas |
| **N2** | Medio | Reversible pero con alcance más allá del artefacto; afecta configuración compartida o el contrato de una costura |
| **N3** | Alto | Difícil de revertir, o con efecto sobre datos, identidad o una ingesta ya certificada |
| **N4** | Crítico | Fuera del perímetro, irreversible, o con exposición de PII o de secretos |

### 2.1 Criterios de clasificación

| Criterio | N1 | N2 | N3 | N4 |
|---|---|---|---|---|
| **Zona tocada** (`01` §2) | Z1, artefacto nuevo | Z1, configuración compartida | Z1, clase compartida `Colombia*` | Z2 o Z3 |
| **Reversibilidad** | Trivial | Requiere re-generar | Requiere re-procesar datos | Irreversible |
| **Alcance** | Un campo o una regla | Una etapa | Varias etapas o varias ingestas | El pipeline o el XPM |
| **Datos** | Solo fixtures sintéticos | Solo fixtures sintéticos | Afecta datos procesados | PII expuesta o dato real |
| **Etapa afectada** | 0 (análisis) | 1, 2, 3 | 4, 5 | **6 — State Calculation** |
| **Dependencia de pregunta abierta** | Ninguna | `[PM]` no bloqueante | `[A:<ID>]` que afecta el artefacto | `[A:<ID>]` con consecuencia irreversible |
| **Aditividad** | Puramente aditivo | Aditivo sobre archivo compartido | Toca lógica existente | Renombra o elimina |

### 2.2 Clasificaciones fijas `[C]`

Estas no se re-evalúan caso por caso:

| Situación | Nivel | Razón |
|---|---|---|
| Cualquier generación para **State Calculation** | **N4** | Riesgo declarado MUY ALTO en su HU de origen: el XPM es visible al cliente |
| Cualquier cambio a `ColombiaIdentityMatcher`, `ColombiaClusterSplitter`, `IdentityImpl`, `ColombiaFoundationStateEnricher` | **N3** | Clases compartidas entre ingestas |
| Cualquier toque a un `*-common-application.conf` | **N4** | Archivo base inmutable (`01` P10) |
| Cualquier acción sobre Z2 o Z3 | **N4** | Fuera del perímetro; se detiene, no se aprueba |
| Codificar la regla "no cross subscriber validation special" | **N4** | Retirada por su autor; prohibida en `07` §5.5 |
| Usar un dato real o PII | **N4** | Prohibición X8/X9 |
| Análisis de la etapa 0 sin generación | **N1** | Solo `.md`, reversible |
| Agregar un campo al mapeo con las nueve dimensiones completas | **N1** | Aditivo, local |
| Agregar un catálogo propio `<NÚMERO>_<NOMBRE>_NH.csv` | **N2** | Aditivo pero consumido por varias reglas |
| Modificar el umbral de calidad | **N3** | Afecta la aceptación de archivos completos |

## 3. Fricción exigida por nivel

| Nivel | Qué debe presentar el agente | Quién aprueba | Registro |
|---|---|---|---|
| **N1** | El artefacto y su justificación en una línea | Desarrollador | Bitácora, entrada simple |
| **N2** | Artefacto + alternativas consideradas + qué se rompe si está mal + prueba asociada | Desarrollador | Bitácora, con las alternativas |
| **N3** | Todo lo de N2 + análisis de impacto en otras ingestas + plan de reversión + prueba de no afectación | Desarrollador, con **justificación escrita**. El dueño del patrón (Ingesta 3) se consulta solo si el Desarrollador lo pide | Bitácora + expediente, con la justificación |
| **N4** | **No se propone como acción.** Se documenta como hallazgo y se escala | Aprobador designado / PO | Bitácora + expediente + escalamiento explícito |

Regla de N4: un riesgo crítico **no se resuelve bajando la fricción ni pidiendo una confirmación rápida.** Se detiene y se escala. Si el agente se encuentra redactando una justificación para hacer algo N4, eso mismo es la señal de parada.

## 4. Matriz de aprobadores

| Decisión | Aprobador | Estado |
|---|---|---|
| Análisis de la etapa 0 (contexto e ingesta) | Desarrollador; PO para el cierre formal | `[C]` |
| Artefacto de etapa N1/N2 | Desarrollador (Operador) | `[C]` |
| Artefacto de etapa N3 y transición N3 (incluida la de evidencia diferida) | Desarrollador, con justificación escrita. Consulta opcional a los dueños del patrón (Ingesta 3) | `[C]` (decisión del Desarrollador, 2026-11-18) |
| **Patrón generado, línea base de garantía** | **Aprobador designado — sin nombrar** | `[A:OQ-06]` |
| Alcance: qué entra y qué sale del MVP | PO | `[C]` |
| Fallback manual vs. automatización | PO | `[C]` |
| Cierre de una pregunta abierta | Quien la abrió + dueño del dato | `[C]` |
| Cambio a un steering `APROBADO` | Aprobador del pack | `[A:OQ-06]` |
| Despliegue, infraestructura, IAM, redes | Experian / Cloud Engineering | `[C]` |
| Uso de datos reales en el piloto | Experian, decisión escrita | `[A:OQ-07(a)]` / `[A:PT-02]` |
| Uso de IA externa sobre código de Experian | Experian, Seguridad y Cumplimiento | `[A:OQ-07(b)]` |

**Brecha de gobierno declarada:** sin aprobador designado antes del **2026-11-19**, el criterio de garantía no tiene línea base. Es el riesgo R12/PN-04 del registro global y sigue abierto. `[A:OQ-06]`

## 5. Rutas de escalamiento

| Situación | Ruta | Plazo |
|---|---|---|
| Ambigüedad de regla de negocio | Agente → Desarrollador → dueño del dominio (Ingesta 3) | Antes de generar el artefacto afectado |
| Contradicción entre fuentes vinculantes | Agente → Desarrollador → PO, aplicando `01` §0.1 | Antes de cerrar el análisis |
| **Conflicto entre este pack y un pack global de la casa** | Agente **se detiene** → Desarrollador → aprobador del pack. El agente **no resuelve por su cuenta** (`01` §0.3) | Inmediato |
| Acción que tocaría Z2 o Z3 | Agente se detiene → Desarrollador ejecuta si corresponde | Inmediato |
| Hallazgo de seguridad, secreto o PII fuera de lugar | Agente se detiene, **no reproduce el valor** → Desarrollador → DevSecOps de Experian (`08` §6) | Inmediato |
| Dependencia de Experian sin dueño ni fecha | Desarrollador → PO → Experian (PN-05) | Antes del congelamiento |
| El mismo enfoque falló dos veces | Agente diagnostica la causa raíz, cambia de enfoque y lo declara; si el nuevo enfoque desvía de lo pedido, pide confirmación | Inmediato |
| Riesgo N4 detectado | Agente documenta y escala; **no propone acción** | Inmediato |

Contenido obligatorio de todo escalamiento: qué se intentó, qué lo bloquea, cuál es el ID de la pregunta abierta, qué alternativas existen y qué consecuencia tiene cada una. **Sin elegir por el humano.**

## 6. Riesgos heredados con dueño

Extracto operable del registro de riesgos del proyecto. La lista completa vive en las fuentes; aquí solo lo que cambia el comportamiento de un agente.

| # | Riesgo | Nivel | Dueño | Mitigación vigente |
|---|---|---|---|---|
| 1 | State Calculation afecta el XPM visible al cliente | N4 | Ingesta 3 / PO | Certificación obligatoria de 4 dimensiones antes de producción |
| 2 | Credencial en texto plano en `settings-artifactory.xml` | N4 | DevSecOps Experian | Prohibición absoluta (`01` §4) + rotación pendiente `[PM]` |
| 3 | Nashorn eliminado en JDK 15; versión de Java del repo sin confirmar | N3 | Experian | No generar configuración dependiente de Nashorn `[A:OQ-TR-2]` |
| 4 | Regla "no cross subscriber validation special" no confiable | N4 | Julio Morales | Prohibido codificarla `[A:OQ-TR-1]` |
| 5 | Tipo documental "Salvoconducto refugiado" (código 7) no existe en el catálogo | N3 | Experian | Crear antes del despliegue `[A:OQ-TR-2b]` |
| 6 | Contrato Curation → Pinning documentado parcialmente (`11`) | N3 | Ingesta 3 | No certificar la costura 3→4 hasta cerrar los pendientes de `11` §6 `[A:OQ-IL-3]` |
| 7 | Colisión posible entre el manejo de EP en Curation y `inferDelete` en Pinning | N3 | Ingesta 3 | Coordinación operativa `[A:PT-12]` |
| 8 | Semántica de los Field Handlers sin confirmar | N3 | Julio Morales / Andrés Pérez | No asignar handler sin confirmación `[A:OQ-TR-3]` |
| 9 | Configuración SISBEN heredada podría colisionar en Pinning | N2-N3 | Ingesta 3 | Validar antes de certificar `[A:OQ-TR-3b]` |
| 10 | Delimitador que Preparation entrega realmente, sin verificar | N2 | Responsable de Preparation | Verificar antes de certificar `[PM]` |
| 11 | Pruebas de no afectación sin datos ni dueño | N3 | Experian | Mitad estática internalizada (aditividad, `reusePIN`); mitad dinámica abierta `[A:PT-06]` |
| 12 | Aprobador del patrón sin designar | N3 | PO | PN-04, antes del 2026-11-19 `[A:OQ-06]` |
| 13 | Meta de reducción ≥50% sin línea base instrumentada | N2 | PO | No publicar cifras fuera del equipo `[A:OQ-01]` |
| 14 | Cinco dependencias de Experian sin dueño ni fecha | N3 | PO | PN-05 `[A:FINDING-11]` |
| 15 | Reportes, DOT, Collibra y certificación XPM en tensión con el alcance excluido | N2 | PO | Decisión antes del congelamiento `[A:FINDING-08]` |
| 16 | Decisiones H-3, H-4 y H-5 no comunicadas al Product Definition global | N2 | Equipo del framework | Comunicación formal pendiente `[PM]` |

## 7. Calendario de gobierno `[C]`

| Fecha | Hito | Consecuencia si se incumple |
|---|---|---|
| **2026-11-19** | Congelamiento de especificaciones | Sin aprobador designado, el criterio de garantía no tiene línea base |
| **2026-12-03** | Entrega | — |
| **2027-01-02** | Fin de la ventana de garantía (ventana de **reporte**, no de corrección) | — |

Secuencia crítica hacia el congelamiento, donde **solo la última fecha es fija**: catálogos (DR-03) → especificaciones ejecutables F8a → aprobación del patrón. Las fechas intermedias siguen sin fijar. `[A:riesgo-11-global]`

## Pendientes de este documento

| # | Pendiente | Estado |
|---|---|---|
| 1 | Aprobador designado, nominal y con fecha | `[A:OQ-06]` |
| 2 | Fechas intermedias de la secuencia catálogos → F8a → aprobación | `[PM]` |
| 3 | Dueños y fechas de las cinco dependencias de Experian | `[A:FINDING-11]` |
| 4 | Validación de la taxonomía de §2 con el equipo (es diseño, no evidencia) | `[P]` |
| 5 | Responsable nominal de escalamiento por etapa | `[PM]` |

## Pie de trazabilidad

- Principio de fricción proporcional al riesgo: `PD/vision` N2; `12/vision` §Human-in-the-Loop.
- Niveles, criterios, fricción y matriz: diseñados el 2026-09-23. No tienen respaldo en las fuentes porque las fuentes no los traen; requieren aprobación.
- Clasificaciones fijas: `13/vision` §Non-Functional (riesgo MUY ALTO de State Calculation); `13/tech` §Project Structure (clases compartidas); `01` §2, §4.
- Riesgos heredados: `PD/vision` §Known Risks; `PD/open-questions` §Resumen; `11/vision` §Known Risks; `13/vision` §Known Risks.
- Calendario: `PD/vision` §Business Drivers, §Business Constraints.
- Última verificación de referencias: 2026-09-23.

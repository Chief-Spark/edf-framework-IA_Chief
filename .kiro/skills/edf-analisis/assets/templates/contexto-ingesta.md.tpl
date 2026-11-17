# Contexto de la ingesta — {{Ingesta}}

<!-- Compuerta de entrada. La HU y la spec de Confluence son obligatorias. Si falta alguna, el análisis no arranca: se dice cuál falta y se detiene. La fila "Fuentes" registra las dos con su ruta; si falta una, no se llena esta plantilla. -->

| Campo | Valor |
|---|---|
| Ingesta (`id` del expediente) | `{{ingesta-id}}` |
| Versión | 1.0.0 |
| Fecha | {{AAAA-MM-DD}} |
| Estado | PROPUESTO (pasa a APROBADO con aprobación registrada por `edf-orquestador`) |
| Fuentes | {{HU y spec de Confluence (obligatorias), con ruta; layout y archivo de muestra sintético (opcionales)}} |
| Skill | `edf-analisis` (modo etapa 0, tipo T1) |

## 1. Identidad de la fuente

| Atributo | Valor | Estado | Fuente |
|---|---|---|---|
| Entidad que entrega | | | |
| ¿Fuente oficial? (`si` / `no` / `mixto` / `indeterminado`) | | | |
| Posición en la jerarquía de best data para PIN | | | |
| Business Light Code (batch / oficial / high frequency) | | | |
| ¿Ingesta nueva? → `reusePIN` | | | |
| Periodicidad y volumen | | | |

## 2. Contrato del archivo (las seis dimensiones de `04` §4.2)

| Dimensión | Valor | Estado | Fuente |
|---|---|---|---|
| Patrón de nombre | | | |
| Delimitador | | | |
| Encoding | | | |
| Encabezado / pie | | | |
| Tipo de evento | | | |
| Número de columnas | | | |

## 3. Reglas de negocio extraídas

| # | Regla | Etapa donde aplica | Estado | Fuente |
|---|---|---|---|---|

## 4. Contradicciones entre fuentes (no se resuelven aquí)

| # | Fuente A dice | Fuente B dice | Pregunta abierta |
|---|---|---|---|

## 5. Supuestos

| ID | Supuesto | Dueño | Consecuencia si es falso |
|---|---|---|---|

## 6. Preguntas abiertas

| ID | Pregunta | Etapa afectada | Bloqueante | Origen |
|---|---|---|:-:|---|

## 7. Riesgos

| ID | Riesgo | Impacto | Mitigación |
|---|---|---|---|

## Checklist de cierre (`04` §4.3)

- [ ] Todos los campos mapeados con las nueve dimensiones (`mapeo-campos.md`).
- [ ] Contrato del archivo completo (seis dimensiones).
- [ ] Reglas de negocio extraídas y trazadas.
- [ ] Dependencias listadas con dueño (`dependencias.md`).
- [ ] Toda afirmación con marcador de estado y cita.
- [ ] Ambigüedades registradas como preguntas abiertas con ID.
- [ ] Supuestos con dueño y consecuencia.
- [ ] Contradicciones explicitadas, no resueltas.
- [ ] Clasificación público/PII campo a campo completa.
- [ ] Solo fixtures sintéticos.
- [ ] Plan de ruta propuesto (`plan-de-ruta.md`).
- [ ] Riesgos con impacto y mitigación.

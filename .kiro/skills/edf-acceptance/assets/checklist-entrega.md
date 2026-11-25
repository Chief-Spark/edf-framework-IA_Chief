# Checklist de entrega — Acceptance

Marque cada punto antes de presentar la propuesta. Un punto en rojo bloquea la entrega.

## A. Automático (`python scripts/render_templates.py --audit` y `pytest` en verde; nada se compila)

- [ ] La spec valida contra `source-spec.schema.yaml`.
- [ ] Todo hueco de las plantillas está definido en el schema y lleno en la spec (ramas activas).
- [ ] Ningún `{{` / `}}` en los archivos generados.
- [ ] `filePattern` empieza con `.*` y coincide con la ruta sin extensión **y** con `lineage.fileName` (ACC-R03).
- [ ] Umbral con una sola escala en `accept` y en las condiciones, y `calidad.umbral` en [0, 1] (regla "umbral en [0, 1]", AC-04).
- [ ] `reject` en sintaxis nueva y solo cita condiciones definidas (ACC-P09).
- [ ] Códigos y niveles de `recordAcceptance` existen en IN-05; niveles de un carácter; ningún código subcadena de otro (ACC-R01).
- [ ] Ningún nombre de quality score dentro de `recordAcceptance` (ACC-R02).
- [ ] Cada quality score declarado vale 0 o 1 en cada evento del fixture (regla "QS por registro binario", AC-ACC-05).
- [ ] HOCON generado parsea con el subconjunto local (`hocon_check.py`) y, si está instalado, con pyhocon. (El parse con
      Typesafe Config 1.3.4 era de las pruebas del generador Java deprecado: histórico.)
- [ ] El simulador reproduce `expected-output.yaml` / las decisiones esperadas del fixture.
- [ ] `--audit` lista una línea `supuesto  S-ACC-xx  impacto=…` por cada supuesto del bloque `supuestos` de la spec, y la entrega
      declara los supuestos que imprime el generador para esa spec, con su ID e impacto. Cada ID está en el catálogo de
      `decisiones-y-abiertos.md`; el texto puede concretar los datos de la spec y una spec puede no declarar todos los del
      catálogo (AC-27, HF-29; `test_supuestos_de_la_spec_iguales_a_los_del_documento`, `test_hf29_*`).

## B. Manual (ingeniero)

- [ ] `name` y `reasonCode` de cada condición no existen ya en `fileConditions` del inner compartido.
- [ ] Ningún otro `filePattern` de `configs[]` coincide con los archivos de la fuente.
- [ ] Integración **aditiva**: solo se agregaron elementos en el `*-inner.conf`; el base no se tocó.
- [ ] `contextualOverride` ausente, o presente con referencia a la decisión humana (D2) y caso de prueba de vacío silencioso.
- [ ] `lookupMapping` ausente, o completo con su CSV de severidades enteras.
- [ ] El ambiente define `emitStockMetrics` y `lambda = batch`.
- [ ] Todos los `ASSUMPTION` citan su ID (`ASSUMPTION S-ACC-xx`) y ese supuesto está en el bloque `supuestos` de la spec, con su impacto, y en el reporte.

## C. Seguridad y datos

- [ ] Sin credenciales, tokens ni `settings-artifactory.xml` en el entregable ni en los logs.
- [ ] Fixture 100 % sintético: `eventId` `SINT-*`; documentos sintéticos, declarados como tales por la spec de la ingesta
      o con prefijo `SINT`, nunca acompañados de nombres reales (HF-31); ningún registro real.
- [ ] Sin PII real en ejemplos, comentarios o mensajes.
- [ ] Sin rutas de máquina locales ni de un workspace concreto.

## D. HITL

- [ ] Propuesta presentada (artefactos + simulación + supuestos + abiertos).
- [ ] Aprobación explícita registrada (quién, cuándo, qué versión de la spec).
- [ ] Compilación declarada **no certificada** (F-01) en el reporte.

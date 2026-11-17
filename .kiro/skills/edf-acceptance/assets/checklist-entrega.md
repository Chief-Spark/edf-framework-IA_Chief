# Checklist de entrega — Acceptance

Marque cada punto antes de presentar la propuesta. Un punto en rojo bloquea la entrega.

## A. Automático (`render-templates.jar --audit`, `mvn -f scripts/java/pom.xml verify` y `pytest` en verde)

- [ ] La spec valida contra `source-spec.schema.yaml`.
- [ ] Todo hueco de las plantillas está definido en el schema y lleno en la spec (ramas activas).
- [ ] Ningún `{{` / `}}` en los archivos generados.
- [ ] `filePattern` empieza con `.*` y coincide con la ruta sin extensión **y** con `lineage.fileName` (ACC-R03).
- [ ] Umbral con una sola escala en `accept` y en las condiciones, y `calidad.umbral` en [0, 1] (regla "umbral en [0, 1]", AC-04).
- [ ] `reject` en sintaxis nueva y solo cita condiciones definidas (ACC-P09).
- [ ] Códigos y niveles de `recordAcceptance` existen en IN-05; niveles de un carácter; ningún código subcadena de otro (ACC-R01).
- [ ] Ningún nombre de quality score dentro de `recordAcceptance` (ACC-R02).
- [ ] Cada quality score declarado vale 0 o 1 en cada evento del fixture (regla "QS por registro binario", AC-ACC-05).
- [ ] HOCON generado parsea con Typesafe Config 1.3.4 (pruebas Java) y con el subconjunto local (`hocon_check.py`).
- [ ] El simulador reproduce `expected-output.yaml` / las decisiones esperadas del fixture.
- [ ] `--audit` lista una línea `supuesto  S-ACC-xx  impacto=…` por cada supuesto del bloque `supuestos` de la spec, y son los mismos de `decisiones-y-abiertos.md` (AC-27; `supuestosDeLaSpecIgualesALosDelDocumento`).

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
- [ ] Fixture 100 % sintético (`eventId` `SINT-*`, documentos `SINT*`); ningún registro real.
- [ ] Sin PII real en ejemplos, comentarios o mensajes.
- [ ] Sin rutas de máquina locales ni de un workspace concreto.

## D. HITL

- [ ] Propuesta presentada (artefactos + simulación + supuestos + abiertos).
- [ ] Aprobación explícita registrada (quién, cuándo, qué versión de la spec).
- [ ] Compilación declarada **no certificada** (F-01) en el reporte.

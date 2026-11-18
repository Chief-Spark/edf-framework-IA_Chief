# Checklist de entrega · Curation

Marcar cada punto antes de presentar la propuesta. Un punto sin marcar se reporta como pendiente,
no se oculta.

## Generación

- [ ] `python scripts/render_templates.py` terminó con `OK` (source-spec válido contra el esquema). No se ejecutó `mvn`, `javac` ni `java -jar`.
- [ ] Ningún `{{hueco}}` sin resolver en la salida (lo verifica el generador y sus pruebas).
- [ ] `python -m pytest scripts/tests -q` en verde.
- [ ] Parse-check HOCON de `fragmentos/*.conf` en verde (`hocon_check.py`; Typesafe Config queda para el fork y se reporta como no verificado).
- [ ] Los 12 artefactos CUR-TPL-01..12 están en `MANIFEST.json` (`archivos`).
- [ ] Todo archivo generado salvo `MANIFEST.json` está en exactamente una de `archivos`, `marcas_sintetico`
      u `otros_archivos`, y los `sha256` de `otros_archivos` coinciden (AC-25, ADR-G3).
- [ ] `MANIFEST.json` muestra `zona_horaria_fechas: {valor, origen}`; si hay `advertencias` (zona distinta de
      la de `asOfDate`, C-CUR-04), se presentan al humano (AC-CUR-10, ADR-G4).

## Contrato del processor (CUR-RC01..06)

- [ ] Constructor exacto `(CurationProcessorContext)`; clase `Serializable`.
- [ ] Logger y lookups como `@transient lazy val`; homologación cargada una vez.
- [ ] `curateEvent` no lanza; `curar` envuelto en `NonFatal`.
- [ ] `outputEventType = 30` (`ASSUMPTION:`) y `enrichEventWithPayload = event.payload`.
- [ ] `subscriberRelation` no sobrescrito, salvo que el source-spec declare `extension_subscriber_relation` (D14):
      en ese caso hay `override def subscriberRelation`, el núcleo `relacion` y su prueba, y la firma del gancho
      se declara **sin verificar** (F-01).
- [ ] Sin `println`, sin datos de negocio en logs ni en advertencias.

## Esquema de salida (CUR-RC05)

- [ ] Claves de `write` = entradas del domain dictionary = `ClavesPublicadas`.
- [ ] Ningún nombre publicado existente se renombra ni elimina.
- [ ] Campos abiertos (D11-a, P2, CUR-PT06) **no** generados.
- [ ] Homologación: 1→10, 2→7, 3→1, 4→4, 5→8, 6→5, 7→pendiente, 8→9, PPT→11; el original en
      `tipoDocumentoSisben`.

## Configuración (regla aditiva)

- [ ] El bloque solo tiene `filePattern` + `processor`; FQCN = clase generada.
- [ ] `filePattern` cubre los ejemplos y no los contraejemplos; no choca con otra ingesta del inner.
- [ ] Ningún `excludes` de la etapa cubre la fuente.
- [ ] El ambiente tiene `emitStockMetrics` y `consistency.paths.domainDictionaryFile`;
      `enable.defaultCurationProcessor` apagado.
- [ ] Lookups: solo se agregan tablas propias; CSV real cargado por el equipo (no el sintético).
- [ ] Conf base sin cambios.

## Seguridad y datos

- [ ] Ningún `settings-artifactory.xml`, token, contraseña o clave en la entrega.
- [ ] Ninguna ruta personal ni del equipo de origen.
- [ ] Fixtures y lookups marcados como sintéticos; ningún dato personal real.

## Trazabilidad

- [ ] Cada `ASSUMPTION:` del código cita un ID que aparece en `decisiones-y-abiertos.md` (AC-35;
      prueba `todaMarcaAssumptionTieneIdEnDecisiones`).
- [ ] La salida esperada (`expected/`) coincide con la generada.
- [ ] Compilación declarada **no certificada** (F-01) en la propuesta.

## Hand-off

- [ ] Contrato **desde Acceptance** revisado (ver SKILL.md, "Contrato de entrada").
- [ ] Entradas del domain dictionary entregadas a Synthesis.
- [ ] Propuesta presentada y **aprobación explícita** registrada antes de integrar.

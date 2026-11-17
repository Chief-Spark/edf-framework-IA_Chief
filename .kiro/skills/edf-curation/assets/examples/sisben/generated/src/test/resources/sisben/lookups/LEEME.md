# Lookups sintéticos (F-12, F-13)

**SINTÉTICO.** Archivos de muestra para pruebas locales. No son los catálogos oficiales y no contienen
datos personales.

- Columnas, delimitador y nombres de archivo: SUPUESTO (F-12). Ajustar al formato real cuando se tenga.
- Códigos DIVIPOLA: muestra pequeña de códigos públicos, no verificada contra DANE (F-13).
- `202_SISBEN_PROCESS_ID_TYPE_HO.csv` no se versiona en el fixture del skill: el generador Java
  (`scripts/java`) lo escribe desde `homologacion_tipo_documento` del source-spec, para que tabla embebida
  y lookup no diverjan.
- Curation solo registra la tabla 202 en `oxygen.lookups[]`; las otras seis las consulta Validation.
- Este archivo es la marca de sintético de la carpeta (AC-30). El generador lo copia junto a los CSV en
  `src/test/resources/<fuente>/lookups/` y lo registra en `MANIFEST.json` (`marcas_sintetico`). Los CSV no
  llevan comentarios porque `OxygenLookup` los carga con cabecera.

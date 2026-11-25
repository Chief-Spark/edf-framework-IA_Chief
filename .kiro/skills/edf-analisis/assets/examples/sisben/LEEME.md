# Ejemplos SISBEN: qué tomar y qué no

Los tres `yaml-skill-*.yaml` son los originales, escritos a mano en sesión el 2026-10-01. Sirven para ver **el nivel de detalle, las citas y los marcadores** que se esperan. Tienen desviaciones conocidas respecto a decisiones posteriores; **no copies estos valores** a un insumo nuevo:

| Archivo | Desviación | Lo vigente |
|---|---|---|
| `yaml-skill-pinning.yaml` | `campo_curado_sisben` usa nombres del archivo crudo (`PRI_NOMBRE`, `SEG_NOMBRE`…) | Claves publicadas por Curation (`standardizedId*`), steering `11` §6 #5 |
| `yaml-skill-pinning.yaml` | `identityMatcher = ISIdentityMatcher` (matcher de referencia del repo InnerSource) | Matcher y lista de atributos compartidos de Colombia, sin generar matcher (DA-12, sección `modo_colombia` de la plantilla) |
| `yaml-skill-pinning.yaml` | Aritmética del umbral 0.6 y pesos 0.5/0.3 | No aplica al matcher de Colombia (match determinista por reglas 0–4, steering `11` §2.1) |
| `yaml-skill-synthesis.yaml` | Agrega `sisben` a `eventTypeList`; `nombre_en_gdd: "[PM]"` y EUC propuestos; GDD vacío | SISBEN sí pasa por Synthesis con sus campos LFP (spec §12, HU R-25; RG-010). `eventTypeList` guarda tipos de archivo y no filtra eventos; en modo Colombia no se escribe (HF-69, `edf-synthesis` SKILL «Modo Colombia») |
| Los tres | `skill_destino: "Skill-ConfigGen-*"` | `edf-pinning`, `edf-synthesis`, `edf-statecalc` |
| Los tres | La cabecera lleva `documento.estado: "PROPUESTO"` y `aprobador` (y el cierre, `Estado global` o `resumen_de_cierre.estado`) | Un insumo nuevo no lleva estado ni aprobador en la cabecera: el estado vive solo en `estado-ingesta.json` y cambiarlo en el archivo altera su hash (HF-32, SKILL.md Modo 2). La cabecera lleva versión, fecha, citas y desviaciones |

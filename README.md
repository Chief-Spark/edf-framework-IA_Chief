# edf-framework-IA — Framework de IA para ingestas EDF sobre Kiro

Framework de uso exclusivo en **Kiro IDE** para configurar una ingesta nueva en EDF/Oxygen: análisis de la fuente (etapa 0) y las 6 etapas del pipeline (Validation → Acceptance → Curation → Pinning → Synthesis → State Calculation), con gobierno humano en cada paso.

> **El framework propone, guía y coordina. El ingeniero de datos aprueba y ejecuta.**
> Nada se copia al fork, se commitea ni se ejecuta en un clúster sin aprobación registrada.

Versión: ver `VERSION` · Cambios: `CHANGELOG.md` · Arquitectura: `docs/arquitectura.md`

## 1. Qué trae

```
edf-framework-IA/
├── README.md · CHANGELOG.md · VERSION
├── instalar.ps1                  Windows (PowerShell)
├── instalar.sh                   macOS / Linux
├── docs/                         para personas (Kiro no lo carga)
│   ├── arquitectura.md
│   └── contratos/README.md
├── kiro/                         se instala como .kiro/ en el workspace
│   ├── steering/                 00 mapa · 01 rector · 02 léxico · 03 pipeline · 04 análisis · 05 convenciones
│   │                             06 riesgo · 07 contratos · 08 seguridad/PII · 09 estado · 10 piloto SISBEN · 11 Curation→Pinning
│   ├── skills/
│   │   ├── edf-orquestador/      puerta de entrada: expediente, bitácora, riesgo, habilita/bloquea
│   │   ├── edf-analisis/         etapa 0 + insumo de cada etapa
│   │   ├── edf-validation/  edf-acceptance/  edf-curation/
│   │   └── edf-pinning/  edf-synthesis/  edf-statecalc/
│   ├── hooks/validar-expediente.json
│   └── specs/orquestador/        diseño, certificación, tareas, trazabilidad
└── tools/
    ├── validar-expediente.py     valida un expediente (esquema + invariantes de gobierno)
    ├── validar-expedientes.py    valida todos (lo usa el hook)
    └── verificar-claves.py       cruza los nombres de campo curado entre los insumos de una ingesta
```

Estado frente a las pruebas E2E: `docs/estado-e2e.md`.

## 2. Instalación

Kiro solo lee la configuración desde `.kiro/` en la **raíz** del workspace que tienes abierto. Por eso el framework se descarga una vez y se **instala** en cada workspace donde trabajes.

1. Clona o descarga este repo (una sola vez, en cualquier carpeta).
2. Crea o elige la carpeta de trabajo (el workspace que abrirás en Kiro).
3. Ejecuta el instalador:

**Windows (PowerShell):**
```powershell
cd <ruta>\edf-framework-IA
.\instalar.ps1 -Destino "C:\ruta\de\tu\workspace"
```
Si PowerShell bloquea el script: `powershell -ExecutionPolicy Bypass -File .\instalar.ps1 -Destino "C:\ruta\..."`.

**macOS / Linux:**
```bash
cd <ruta>/edf-framework-IA
./instalar.sh /ruta/de/tu/workspace
```

El instalador copia `kiro/` → `.kiro/`, `tools/` → `tools/`, `docs/` → `docs/edf-framework/` y crea `expedientes/`. Si el workspace ya tiene `.kiro/`, avisa y pide confirmación; solo sobrescribe archivos del framework con el mismo nombre.

4. Abre el workspace en Kiro. Los steering `00`, `01` y `05` se cargan siempre; el resto según su tipo de inclusión. Las skills se activan solas según lo que pidas.

**Actualizar:** `git pull` en el repo y vuelve a ejecutar el instalador sobre el mismo workspace. Tus `expedientes/` no se tocan. La versión instalada queda en `.kiro/edf-framework.version`.

### Requisitos

| Para qué | Qué |
|---|---|
| Validar expedientes (hook) | Python 3 + `pip install jsonschema` |
| Verificar claves entre insumos | Python 3 + `pip install pyyaml` |
| Generadores de Validation, Acceptance y Curation | Java 17 + Maven 3.9 (`mvn -q -f .kiro/skills/edf-<etapa>/scripts/java/pom.xml verify`) |
| Verificadores de Pinning, Synthesis y State Calculation | PowerShell (`evals/verificar-artefacto.ps1`) |

No se necesita Scala ni el classpath de Oxygen: lo generado se compila en el fork.

## 3. Primer uso (orden de trabajo)

| # | Qué pides | A quién | Resultado |
|---|---|---|---|
| 1 | "Orquestador: inicia el expediente de la ingesta SISBEN" | `edf-orquestador` | `expedientes/sisben/` con estado, vista y bitácora |
| 2 | "Analiza la ingesta sisben, etapa 0" | `edf-analisis` | `analisis/` con contexto, mapeo de campos, dependencias, plan de ruta |
| 3 | "Orquestador: quiero avanzar de analisis a validation" → apruebas | `edf-orquestador` | Etapa 0 aprobada, `validation` habilitada |
| 4 | "Prepara el insumo de Validation para sisben" | `edf-analisis` | `insumos/source-spec-validation.yaml` |
| 5 | "Orquestador: registra el insumo de validation" → apruebas | `edf-orquestador` | Insumo aprobado |
| 6 | "Genera la etapa Validation de sisben" | `edf-validation` | `propuestas/validation/` |
| 7 | "Orquestador: registra los artefactos de validation y propón la transición" → apruebas | `edf-orquestador` | `acceptance` habilitada |
| … | Repite 4–7 para cada etapa | | |

**Primer mensaje de toda sesión nueva:** "Orquestador: ¿cómo va la ingesta <id>?". El expediente persiste entre sesiones.

## 4. Reglas que no cambian

- Solo datos sintéticos. Nunca registros reales ni PII en expedientes, bitácora o reportes.
- Nada fuera del workspace: sin AWS, IAM, Airflow, despliegues ni escritura en repos Oxygen (steering `01` §2).
- Ninguna etapa avanza sin aprobación registrada. State Calculation (riesgo N4) la aprueba el PO o el Aprobador designado.

## 5. Mantenimiento

| Rol | Puede |
|---|---|
| Ingeniero de datos | Usar el framework, aprobar/rechazar, ejecutar en el fork |
| Mantenedor (KIDAR) | Cambiar steering, skills, plantillas, hooks; publicar versión nueva en este repo |

Cambios al framework: en este repo, con entrada en `CHANGELOG.md` y subida de `VERSION`. No editar `.kiro/` instalado a mano: se sobrescribe en la próxima instalación.

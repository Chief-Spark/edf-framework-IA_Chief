# Pinning

## 1. Descripción General

La etapa de \*\*Pinning\*\* es el proceso mediante el cual se determina si dos identidades (registros de personas) corresponden al mismo individuo. Este proceso se basa en la comparación de \*\*atributos identificadores\*\* (Identifying Attributes) y en un conjunto de \*\*reglas de matching\*\* implementadas en el componente \`ColombiaIdentityMatcher\`.

El objetivo principal es vincular (o "pinear") registros provenientes de distintas fuentes de datos a una única identidad consolidada, evitando duplicados y garantizando la integridad del bureau de crédito.

---

## 2. Componentes Principales

| Componente | Ubicación | Función |
|---|---|---|
| \`identifying-attributes-new.json\` | \`env/application-conf/pinning/\` | Define la lista de atributos identificadores utilizados en el proceso de pinning. |
| \`ColombiaIdentityMatcher.scala\` | \`modules/pinning/src/main/scala/.../custommatch/\` | Implementa la lógica de comparación y matching entre dos identidades. |

---

## 3. Atributos Identificadores (Identifying Attributes)

El archivo \`identifying-attributes-new.json\` contiene la lista de atributos que se extraen de cada evento curado y se utilizan como insumo para la comparación de identidades. Cada atributo representa un campo estandarizado o una bandera de tipo de fuente.

### 3.1. Lista de Atributos

| Atributo | Tipo | Descripción |
|---|---|---|
| \`isOfficialSource\` | Bandera (true/false) | Indica si el registro proviene de una fuente oficial. |
| \`standardizedIdType\` | Texto | Tipo de documento de identidad estandarizado (e.g., CC, NIT, CE). |
| \`standardizedIdNumber\` | Texto | Número de documento de identidad estandarizado. |
| \`standardizedIdName\` | Texto | Nombre completo estandarizado del individuo. |
| \`standardizedHomePhone\` | Texto | Teléfono del hogar estandarizado. |
| \`standardizedOfficePhone\` | Texto | Teléfono de oficina estandarizado. |
| \`standardizedMobilePhone\` | Texto | Teléfono móvil estandarizado. |
| \`standardizedHomeAddress\` | Texto | Dirección del hogar estandarizada. |
| \`standardizedOfficeAddress\` | Texto | Dirección de oficina estandarizada. |
| \`standardizedMailingAddress\` | Texto | Dirección de correspondencia estandarizada. |
| \`isSuperIntendencia\` | Bandera (true/false) | Indica si el registro proviene de la Superintendencia. |
| \`isINFP\` | Bandera (true/false) | Indica si el registro es una Huella de Consulta (Inquiry FootPrint). |
| \`isTable1QP\` | Bandera (true/false) | Indica si el registro proviene de Table1 QP. |
| \`isMigratedAmnesty\` | Bandera (true/false) | Indica si el registro corresponde a una amnistía migrada. |
| \`isPinningSpecialEventAlpha\` | Bandera (true/false) | Indica si el registro corresponde a un evento especial Alpha de pinning. |
| \`pinningKeySSR\` | Texto | Llave de pinning SSR utilizada como criterio adicional de matching. |
| \`isMuric\` | Bandera (true/false) | Indica si el registro proviene de MURIC. Esta es la \*\*llave de Pinning para MURIC\*\*. |

### 3.2. Consideraciones

- Si un evento curado no contiene un atributo identificador definido en esta lista, el valor se establece como cadena vacía (\`""\`).
- Los atributos de tipo bandera utilizan \`"false"\` como valor por defecto cuando no están presentes.
- Los atributos \`standardizedIdType\` y \`standardizedIdNumber\` son \*\*obligatorios\*\* para que se evalúe cualquier regla de matching. Si alguno de ellos es nulo, el proceso retorna inmediatamente un resultado de \*\*no match\*\*.

---

## 4. Lógica de Matching (ColombiaIdentityMatcher)

La clase \`ColombiaIdentityMatcher\` extiende \`IdentityMatcher\` e implementa tres métodos fundamentales: \`doesMatch\`, \`nearNeighborKeys\` y \`matchingKeysForCelebrity\`.

### 4.1. Método \`doesMatch\`

Este método recibe dos objetos \`TaggedIdentity\` y determina si corresponden al mismo individuo. Retorna una tupla \`(Boolean, Double)\` donde el booleano indica si hay match y el double representa la distancia (0.0 = match exacto, 1.0 = no match).

#### 4.1.1. Flujo de Evaluación

\`\`\`text
┌─────────────────────────────────────────┐
│  1. Ejecutar matching por defecto       │
│     (subscriber / subscriberRelation)   │
│     via super.doesMatch()               │
└──────────────┬──────────────────────────┘
               │
        ┌──────▼──────┐
        │ ¿Match?     │──── SÍ ──► Retorna (true, 0.0)
        └──────┬──────┘
               │ NO
               ▼
┌─────────────────────────────────────────┐
│  2. Extraer todos los atributos         │
│     identificadores de ambas            │
│     identidades                         │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│  3. Calcular componentes de nombre      │
│     para ambas identidades              │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│  4. Evaluar match/conflicto/nulo para:  │
│     - idNumber                          │
│     - idType                            │
│     - name (completo y parcial)         │
│     - address                           │
│     - phone                             │
│     - banderas de fuente                │
│     - SSR Key                           │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│  5. Aplicar reglas de decisión          │
└─────────────────────────────────────────┘
\`\`\`

#### 4.1.2. Reglas de Decisión

Las reglas se evalúan en orden secuencial. La primera regla que se cumple determina el resultado:

| Prioridad | Regla | Resultado |
|---|---|---|
| 0 | \`idType\` es nulo \*\*O\*\* \`idNumber\` es nulo | \*\*(false, 1.0)\*\* — No match. La evaluación se detiene inmediatamente. |
| 1 | \`idType\` match \*\*Y\*\* \`idNumber\` match \*\*Y\*\* al menos una bandera de fuente es verdadera (\`isOfficialSource\`, \`isSuperIntendencia\`, \`isINFP\`, \`isTable1QP\`, \`isMigratedAmnesty\`, \`isPinningSpecialEventAlpha\`, \*\*\`isMuric\`\*\*) | \*\*(true, 0.0)\*\* — Match. |
| 2 | \`idType\` match \*\*Y\*\* \`idNumber\` match \*\*Y\*\* (\`name\` match o \`name\` parcial match \*\*O\*\* \`address\` match \*\*O\*\* \`phone\` match) | \*\*(true, 0.0)\*\* — Match. |
| 3 | \`idType\` match \*\*Y\*\* \`idNumber\` match \*\*Y\*\* \`pinningKeySSR\` match | \*\*(true, 0.0)\*\* — Match. |
| 4 | Ninguna regla anterior se cumple | \*\*(false, 1.0)\*\* — No match. |

#### 4.1.3. Detalle de las Banderas de Fuente en Regla 1

Cuando al menos una de las dos identidades comparadas proviene de una fuente especial, el matching se simplifica y solo requiere coincidencia en tipo y número de documento. Las fuentes especiales son:

- \*\*Fuente Oficial\*\* (\`isOfficialSource\`)
- \*\*Superintendencia\*\* (\`isSuperIntendencia\`)
- \*\*Huella de Consulta\*\* (\`isINFP\`)
- \*\*Table1 QP\*\* (\`isTable1QP\`)
- \*\*Amnistía Migrada\*\* (\`isMigratedAmnesty\`)
- \*\*Evento Especial Alpha\*\* (\`isPinningSpecialEventAlpha\`)
- \*\*MURIC\*\* (\`isMuric\`)

Esto significa que para registros provenientes de estas fuentes, \*\*no se requiere coincidencia adicional\*\* en nombre, dirección o teléfono; basta con que coincidan \`standardizedIdType\` y \`standardizedIdNumber\`.

#### 4.1.4. Comparaciones de Atributos

Cada grupo de atributos se evalúa mediante métodos utilitarios de la clase \`ColombiaMatching\`:

| Comparación | Método | Resultados Posibles |
|---|---|---|
| Número de documento | \`ColombiaMatching.matchIdNumbers\` | match, conflicto, nulo |
| Tipo de documento | \`ColombiaMatching.matchIdTypes\` | match, conflicto, nulo |
| Nombre | \`ColombiaMatching.matchNames\` | match, match parcial, conflicto, nulo |
| Dirección | \`ColombiaMatching.matchAddresses\` | match, conflicto, nulo |
| Teléfono | \`ColombiaMatching.matchPhones\` | match, conflicto, nulo |
| Banderas de fuente | \`ColombiaMatching.isEvent\` | true/false |
| Fuente oficial | \`ColombiaMatching.isOfficialSource\` | true/false |
| Llave SSR | \`ColombiaMatching.isEquals\` | true/false |

Para \*\*direcciones\*\*, se comparan cruzadamente los tres tipos (hogar, oficina, correspondencia) de ambas identidades. Lo mismo aplica para \*\*teléfonos\*\* (hogar, móvil, oficina).

Para \*\*nombres\*\*, se descomponen en componentes mediante \`ColombiaMatching.getNameComponents\` y se evalúa tanto coincidencia completa como parcial.

---

### 4.2. Método \`nearNeighborKeys\`

Define las llaves utilizadas para localizar identidades potencialmente coincidentes (candidatas a matching) de forma eficiente, sin necesidad de comparar contra todas las identidades existentes.

| Parámetro | Llave Generada |
|---|---|
| \`standardizedIdNumber\` | El número de documento estandarizado se utiliza como llave de búsqueda. |

Si el \`standardizedIdNumber\` es nulo, se retorna una lista vacía, lo que significa que la identidad no participará en el proceso de búsqueda de vecinos cercanos.

---

### 4.3. Método \`matchingKeysForCelebrity\`

Gestiona el matching para identidades de tipo "celebridad" (identidades que contienen un número inusualmente alto de cuentas o registros asociados). Para estas identidades, el matching se realiza con una llave compuesta:

| Posición | Atributo |
|---|---|
| 1 | \`standardizedIdType\` |
| 2 | \`standardizedIdNumber\` |

La combinación de tipo y número de documento debe coincidir exactamente para que se vincule un nuevo registro a una identidad celebridad.

---

## 5. Llave de Pinning para MURIC

La llave de pinning \`isMuric\` identifica registros provenientes de la fuente \*\*MURIC\*\*. Su comportamiento dentro del flujo de pinning es el siguiente:

- Se define como atributo identificador en \`identifying-attributes-new.json\`.
- Se representa en el código mediante la constante \`IdentityAttributesKey.isMuric\` con valor \`"isMuric"\`.
- Cuando no está presente en un evento, se asume el valor por defecto \`"false"\`.
- Se evalúa mediante \`ColombiaMatching.isEvent(thisMuric, thatMuric)\`, que retorna \`true\` si al menos una de las dos identidades tiene esta bandera activa.
- Cuando \`isMuric\` es \`true\`, el matching se resuelve \*\*únicamente con la coincidencia de \`standardizedIdType\` y \`standardizedIdNumber\`\*\* (Regla 1), sin requerir validación adicional de nombre, dirección o teléfono.

---

## 6. Resumen del Flujo Completo

\`\`\`text
Evento Curado
     │
     ▼
Extracción de Identifying Attributes
(según identifying-attributes-new.json)
     │
     ▼
Generación de Near Neighbor Keys
(standardizedIdNumber)
     │
     ▼
Búsqueda de identidades candidatas
     │
     ▼
Evaluación doesMatch() por cada candidata
     │
     ├── super.doesMatch() ──► Match por subscriber/subscriberRelation
     │
     ├── Regla 0: idType o idNumber nulo ──► No Match
     │
     ├── Regla 1: idType + idNumber + Fuente Especial ──► Match
     │   (isOfficialSource | isSuperIntendencia | isINFP |
     │    isTable1QP | isMigratedAmnesty |
     │    isPinningSpecialEventAlpha | isMuric)
     │
     ├── Regla 2: idType + idNumber + (nombre | dirección | teléfono) ──► Match
     │
     ├── Regla 3: idType + idNumber + pinningKeySSR ──► Match
     │
     └── Default ──► No Match
     │
     ▼
Vinculación o creación de nueva identidad
\`\`\`

---

## 7. Consideraciones Técnicas

- La operación de matching es \*\*reflexiva\*\*: si A coincide con B, entonces B coincide con A.
- El valor de distancia retornado es binario en la implementación actual: \`0.0\` para match y \`1.0\` para no match.
- Las identidades tipo celebridad se manejan con llaves más restrictivas (\`standardizedIdType\` + \`standardizedIdNumber\`) para evitar vinculaciones incorrectas en volúmenes altos.
- Cualquier nuevo atributo identificador debe agregarse tanto en \`identifying-attributes-new.json\` como en el objeto \`IdentityAttributesKey\` y en la lógica de extracción dentro de \`doesMatch\`.

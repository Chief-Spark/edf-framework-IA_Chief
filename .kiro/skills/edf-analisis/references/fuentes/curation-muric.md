# Curación

## **1. Descripción General **

  

La etapa de **Curación MURIC** hace parte del pipeline de procesamiento de datos y tiene como objetivo transformar información validada en un formato estandarizado y listo para su uso.

En esta etapa:

- Se recibe información en formato **JSON**.
- Se mapea al modelo interno **MuricEvent**.
- Se aplican **reglas de negocio** para transformar los datos.
- Se **normalizan valores** usando catálogos de referencia.
- Se genera una de las estructuras de salida definidas según el tipo de evento.

Como resultado, los datos quedan **consistentes, estructurados y preparados** para su almacenamiento y consumo en etapas posteriores.

---

### **1.1 Estructuras Generadas**

| Código | Nombre | Descripción |
| --- | --- | --- |
| 001 | MuricCreditStructure | Créditos |
| 002 | MuricAttributeStructure | Atributos |
| 003 | MuricTransactionStructure | Transacciones |

---

### **1.2 Las operaciones que ejecuta la curación son:**

  
-Determinación de la estructura (001 / 002 / 003)  
-Limpieza de campos con prefijo \_  
-Resolución de businessLineCode  
-Homologación de campos (tipo\_garantia, modalidad)  
-Conversión de fechas a epoch  
-Conversión de moneda (COP → 1)  
-Resolución de estado de suscriptor  
-Serialización a JSON

---

### **1.2 Flujo de Procesamiento**

  


---

## **2. Archivos de Configuración Involucrados**

curation-common-application.conf   
curation-common-application-inner.conf

---

## **3. Patrón de Archivo MURIC**

### **3.1 Expresión Regular del Patrón**

.\*/T(\[1-9\]\\d?)\_C(\[1-9\]\\d{0,2})\_D\\d{8}\_00\[1-3\]\\.\\d{14}-\\d{4}\\.json.\*

---

## **4. Estructuras de Datos**

###  4.1 Estructura 001 - Créditos

Contiene información básica del crédito al momento del desembolso.

| Campo | Tipo | Descripción | Origen |
| --- | --- | --- | --- |
| `standardizedIdType` | `Option[Int]` | Tipo de identificación estandarizado | `event.standardizedIdType` |
| `standardizedIdNumber` | `Option[Int]` | Número de identificación estandarizado | `event.standardizedIdNumber` |
| `typeOfData` | `Option[Int]` | Código de fuente de información (MURIC) | Lookup: INFORMATION\_SOURCES |
| `businessLineCode` | `Option[String]` | Código de línea de negocio | `giBusinessLineCode()` |
| `cutoffDate` | `Option[Long]` | Fecha de corte (epoch) | `event.cutoffDate` |
| `entityType` | `Option[String]` | Tipo de entidad | `event.tipo_entidad` |
| `entityCode` | `Option[String]` | Código de entidad | `event.codigo_entidad` |
| `entityName` | `Option[String]` | Nombre de la entidad | `event.nombre_entidad` |
| `businessIdentification` | `Option[String]` | Identificación del negocio | `event.identificacion_negocio` |
| `reportingDate` | `Option[String]` | Fecha de reporte | `event.fecha_corte` |
| `typeOfDebt` | `Option[String]` | Modalidad del crédito | `event.modalidad`  |
| `productCode` | `Option[String]` | Código del producto | `event.codigo_producto`  |
| `debtorQuality` | `Option[String]` | Calidad del deudor | `event.calidad_deudor`  |
| `disbursementDate` | `Option[Long]` | Fecha de desembolso (epoch) | `event.fecha_desembolso` |
| `maturityDate` | `Option[Long]` | Fecha de vencimiento (epoch) | `event.fecha_vencimiento` |
| `disbursedAmount` | `Option[Double]` | Valor desembolsado | `event.valor_desembolsado` |
| `capitalPaymentFrequency` | `Option[String]` | Frecuencia pago capital | `event.frecuencia_pago_capital`  |
| `interestPaymentFrequency` | `Option[String]` | Frecuencia pago intereses | `event.frecuencia_pago_intereses`  |
| `rateType` | `Option[String]` | Tipo de tasa | `event.tipo_tasa` |
| `currencyType` | `Option[Int]` | Tipo de moneda (1=COP, 2=Otra) | `getCurrencyType()` |
| `guaranteeType` | `Option[String]` | Tipo de garantía | `event.tipo_garantia`  |
| `legalFramework` | `Option[String]` | Marco legal | `event.marco_legal` |
| `accountNumber` | `Option[String]` | Número de cuenta | `event.identificacion_credito_entidad` |
| `eventDateLastUpdated` | `Option[Long]` | Última actualización (epoch) | `event.eventLastUpdated` |
| `subscriber` | `Option[String]` | Identificador del suscriptor | `event.subscriber` |
| `fileExtension` | `Option[String]` | Extensión del archivo | `event.extension` |
| `structure` | `Option[String]` | Estructura del registro | `event.estructura` |
| `isMuric` | `Option[Boolean]` | Indicador MURIC | `true` (constante) |
| `subscriberBlockStatus` | `Option[Int]` | Estado de bloqueo del suscriptor | `getSubscriberStatus()` |
| `blockReason` | `Option[Int]` | Razón del bloqueo | `getBlockReason()` |

---

### 4.2 Estructura 002 - Atributos 

Contiene atributos adicionales del crédito.

| Campo | Tipo | Descripción | Origen |
| --- | --- | --- | --- |
| `standardizedIdType` | `Option[Int]` | Tipo de identificación estandarizado | `event.standardizedIdType` |
| `standardizedIdNumber` | `Option[Int]` | Número de identificación estandarizado | `event.standardizedIdNumber` |
| `typeOfData` | `Option[Int]` | Código de fuente de información | Lookup: INFORMATION\_SOURCES |
| `businessLineCode` | `Option[String]` | Código de línea de negocio | `giBusinessLineCode()` |
| `cutoffDate` | `Option[Long]` | Fecha de corte (epoch) | `event.cutoffDate` |
| `entityType` | `Option[String]` | Tipo de entidad | `event.tipo_entidad` |
| `entityCode` | `Option[String]` | Código de entidad | `event.codigo_entidad` |
| `entityName` | `Option[String]` | Nombre de la entidad | `event.nombre_entidad` |
| `businessIdentification` | `Option[String]` | Identificación del negocio | `event.identificacion_negocio` |
| `reportingDate` | `Option[String]` | Fecha de reporte | `event.fecha_corte` |
| `attributeKey` | `Option[String]` | Clave del atributo | `event.clave_atributo` |
| `attributeValue` | `Option[String]` | Valor del atributo | `event.valor_atributo` |
| `accountNumber` | `Option[String]` | Número de cuenta | `event.identificacion_credito_entidad` |
| `eventDateLastUpdated` | `Option[Long]` | Última actualización (epoch) | `event.eventLastUpdated` |
| `subscriber` | `Option[String]` | Identificador del suscriptor | `event.subscriber` |
| `fileExtension` | `Option[String]` | Extensión del archivo | `event.extension` |
| `structure` | `Option[String]` | Estructura del registro | `event.estructura` |
| `isMuric` | `Option[Boolean]` | Indicador MURIC | `true` (constante) |
| `subscriberBlockStatus` | `Option[Int]` | Estado de bloqueo del suscriptor | `getSubscriberStatus()` |
| `blockReason` | `Option[Int]` | Razón del bloqueo | `getBlockReason()` |

---

###  4.3 Estructura 003 - Transacciones

Contiene información mensual de las transacciones del crédito.

| Campo | Tipo | Descripción | Origen |
| --- | --- | --- | --- |
| `standardizedIdType` | `Option[Int]` | Tipo de identificación estandarizado | `event.standardizedIdType` |
| `standardizedIdNumber` | `Option[Int]` | Número de identificación estandarizado | `event.standardizedIdNumber` |
| `typeOfData` | `Option[Int]` | Código de fuente de información | Lookup: INFORMATION\_SOURCES |
| `businessLineCode` | `Option[String]` | Código de línea de negocio | `giBusinessLineCode()` |
| `cutoffDate` | `Option[Long]` | Fecha de corte (epoch) | `event.cutoffDate` |
| `entityType` | `Option[String]` | Tipo de entidad | `event.tipo_entidad` |
| `entityCode` | `Option[String]` | Código de entidad | `event.codigo_entidad` |
| `entityName` | `Option[String]` | Nombre de la entidad | `event.nombre_entidad` |
| `businessIdentification` | `Option[String]` | Identificación del negocio | `event.identificacion_negocio` |
| `reportingDate` | `Option[String]` | Fecha de reporte | `event.fecha_corte` |
| `monthlyQualification` | `Option[String]` | Calificación mensual del crédito | `event.calificacion_credito` |
| `typeOfDebt` | `Option[String]` | Modalidad del crédito | `event.modalidad`  |
| `productCode` | `Option[String]` | Código del producto | `event.codigo_producto`  |
| `debtorQuality` | `Option[String]` | Calidad del deudor | `event.calidad_deudor`  |
| `disbursementDate` | `Option[Long]` | Fecha de desembolso (epoch) | `event.fecha_desembolso` |
| `maturityDate` | `Option[Long]` | Fecha de vencimiento (epoch) | `event.fecha_vencimiento` |
| `disbursedAmount` | `Option[Double]` | Valor desembolsado | `event.valor_desembolsado` |
| `capitalPaymentFrequency` | `Option[String]` | Frecuencia pago capital | `event.frecuencia_pago_capital`  |
| `interestPaymentFrequency` | `Option[String]` | Frecuencia pago intereses | `event.frecuencia_pago_intereses`  |
| `rateType` | `Option[String]` | Tipo de tasa | `event.tipo_tasa` |
| `currencyType` | `Option[Int]` | Tipo de moneda (1=COP, 2=Otra) | `getCurrencyType()` |
| `guaranteeType` | `Option[String]` | Tipo de garantía | `event.tipo_garantia`  |
| `legalFramework` | `Option[String]` | Marco legal | `event.marco_legal` |
| `accountStatus` | `Option[String]` | Estado de la cuenta | `event.estado`  |
| `gracePeriod` | `Option[String]` | Período de gracia | `event.periodo_gracia`  |
| `daysInArrears` | `Option[String]` | Días en mora | `event.dias_mora` |
| `interestRate` | `Option[String]` | Tasa de interés | `event.tasa_interes` |
| `interestRateSpread` | `Option[String]` | Spread tasa de interés | `event.spread_tasa_interes` |
| `capitalValue` | `Option[Double]` | Saldo de capital | `event.saldo_capital` |
| `interestValue` | `Option[Double]` | Saldo de intereses | `event.saldo_intereses` |
| `otherConceptValue` | `Option[Double]` | Saldo otros conceptos | `event.saldo_otros` |
| `provisionModel` | `Option[String]` | Modelo de provisiones | `event.modelo_provisiones`  |
| `individualProvisionsValue` | `Option[Double]` | Provisión procíclica | `event.provision_prociclica` |
| `countercyclicalProvision` | `Option[Double]` | Provisión contracíclica | `event.provision_contraciclica` |
| `additionalEntityPolicyProvision` | `Option[Double]` | Provisión adicional | `event.provision_adicional_politica_entidad` |
| `otherProvisions` | `Option[Double]` | Otras provisiones | `event.provision_otros` |
| `expectedCapitalInstallment` | `Option[Double]` | Cuota esperada capital | `event.cuota_esperada_capital` |
| `expectedInterestInstallment` | `Option[Double]` | Cuota esperada intereses | `event.cuota_esperada_intereses` |
| `receivedCapitalInstallment` | `Option[Double]` | Cuota recibida capital | `event.cuota_recibida_capital` |
| `receivedInterestInstallment` | `Option[Double]` | Cuota recibida intereses | `event.cuota_recibida_intereses` |
| `guaranteeValue` | `Option[Double]` | Valor de garantía | `event.valor_garantia` |
| `productCutoffDate` | `Option[Long]` | Fecha corte producto (epoch) | `event.fecha_corte_producto` |
| `guaranteeDetailDate` | `Option[Long]` | Fecha de garantía (epoch) | `event.fecha_garantia` |
| `defaultProbability` | `Option[String]` | Probabilidad de incumplimiento | `event.probabilidad_incumplimiento_credito` |
| `lossGivenDefault` | `Option[String]` | Pérdida dado el incumplimiento | `event.perdida_dado_incumplimiento` |
| `accountNumber` | `Option[String]` | Número de cuenta | `event.identificacion_credito_entidad` |
| `eventDateLastUpdated` | `Option[Long]` | Última actualización (epoch) | `event.eventLastUpdated` |
| `subscriber` | `Option[String]` | Identificador del suscriptor | `event.subscriber` |
| `fileExtension` | `Option[String]` | Extensión del archivo | `event.extension` |
| `structure` | `Option[String]` | Estructura del registro | `event.estructura` |
| `isMuric` | `Option[Boolean]` | Indicador MURIC | `true` (constante) |
| `subscriberBlockStatus` | `Option[Int]` | Estado de bloqueo del suscriptor | `getSubscriberStatus()` |
| `blockReason` | `Option[Int]` | Razón del bloqueo | `getBlockReason()` |

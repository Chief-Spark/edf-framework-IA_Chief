package com.experian.edf.oxygen.statecalculation.extension

object Constants {

  val EVENT_TYPE = EventType
  val QUOTE = "quote"
  val COMPRESSION = "compression"
  val ARRAY = "array"
  val VALUE = "_VALUE"
  val STRUCT_TYPE = "struct"
  val OPEN_PARENTHESIS = "("
  val ZERO = "0"
  val NOT_AVAILABLE = "Not_Available"
  val THREE_DIGIT_MAX_VALUE = 999
  val FOUR_DIGIT_MAX_VALUE = 9999
  val ALPHANUMERIC_REGEX = "[a-zA-Z0-9]+"
  val FILENAME_SPLITTER_DOT = '.'
  val DATE_FORMAT = "ddMMyyyy"
  val IS_PARTIAL_PAYMENT = "isPartialPayment"
  val EMPTY = ""
  val DATA_RECORD = "dataRecord"
  val BRAZIL_TIMEZONE = "America/Sao_Paulo"
  val FILE_NAME = "fileName"
  val LEFT_PAD = "L"
  val RIGHT_PAD = "R"

  val HEADER_MARKER = "00"
  val FIRST_HEADER_MARKER = "HHH"
  val LAST_FOOTER_MARKER = "FFF"
  val FOOTER_MARKER = "99"

  val LEADING_ZEROS = "^0+"
  val LEFT_PADDING = "leftPad"
  val INGESTED_ITEMS = "ingesteditems"
  val FIRST_INDEX = 0
  val UNDERSCORE = "_"
  val VALUE_COLUMN = "value"
  val HEADER = "Header"
  val SUCCESS = "Success"
  val ZERO_LONG = 0L
  val ONE_INT = 1
  val ONE_STRING = "1.0"
  val EIGHTY_FIVE = "0.85"
  val FIFTY = "0.50"
  val EIGHT_INT = 8
  val FOURTEEN = 14
  val ERROR_CODE = "errorCode"
  val ERROR_CODE_VALUE = "errorCodeValue"
  val MESSAGE_KEY = "messageKey"
  val ERROR_LEVEL = "errorLevel"
  val ERROR_CODE_NAME = "errorCodeName"
  val FIXEDWIDTH = "fixedWidth"
  val DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSSSS"
  val DEFAULT_DATE_VALUE = "0000-00-00T00:00:00.000000000000"
  val EMPTY_STRING = ""
  val RECORD_TYPE_03_15 = "R03R15"
  val BOARD_ALLOCATION = "BoardAllocation"
  val MANUAL_ALLOCATION = "ManualAllocation"
  val NO_ALLOCATION = "NoAllocation"
  val COMPANY_CURRENCY_ALLOCATION_AFTER_19940630 = "CompanyCurrencyAllocationAfter19940630"
  val COMPANY_CURRENCY_ALLOCATION_BEFORE_19940630 = "CompanyCurrencyAllocationBefore19940630"
  val EIGHT = "8"
  val SIX = "6"

  val TRUE = "true"
  val SCENARIO = "scenario"
  val MESSAGE_VALUE = "msgValue"
  val MESSAGE_CODE = "msgCode"
  val MESSAGE_LEVEL = "msgLevel"
  val ERROR_KEY = "msgKey"
  val PIN = "pin"
  val FIXED_WIDTH_DATA = "FixedWidthData"
  val LEFT_OUTER = "left_outer"
  val FIELD_NAME = "fieldName"
  val LENGTH = "length"
  val FILLER = "filler"
  val PADDING = "padding"
  val START_POSITION = "startPosition"
  val OXYGEN_APPLICATION_PATHS_APPNAME = "oxygen.application.paths.appName"
  val OXYGEN_APPLICATION_PATHS_USERHOME = "oxygen.application.paths.userHome"
  val OXYGEN_APPLICATION_MASTER = "spark.master"
  val ENV_BRAZIL_CONF = "env-brazil.conf"
  val SPARK_DEV_CONF = "spark-dev.conf"
  val FINAL_OUTPUT = "finalOutput"
  val BRANCH = "Branch"

  val COMPANY_DETAIL = "CompanyDetail"

  val PARTNER_PERSON = "PartnerPerson"

  val PARTNER_PERSON_DETAIL = "PartnerPersonDetail"

  val PARTNER_COMPANY = "PartnerCompany"

  val PARTNER_COMPANY_DETAIL = "PartnerCompanyDetails"

  val ACTIVITY = "Activity"

  val COMPANY_COMPLIMENTARY_DATA = "CompanyComplimentaryData"

  val PARTNER_COMPLIMENTARY_DATA = "PartnerComplimentaryData"


  val ONE = "1"
  val TWO = "2.00"
  val THREE = "3"
  val FOUR = "4"
  val REAL = "R$"
  val NINE = "9"
  val RECORD_04 = "04"
  val RECORD_15 = "15"
  val RECORD_02 = "02"
  val RECORD_03 = "03"

  val DATASET_ID = "datasetId"
  val API_DATASETS = "api/datasets/"

  val ROOT_COLUMNS = "rootColumns"
  val UNIQUE_KEY = "uniqueKey"
  val RAW_DATA = "RawData"
  val PREVIOUS = "previous"
  val CURRENT = "current"
  val MATCH_RESULT = "matchResult"
  val INCREMENTAL_XPM = "incrementalXPM"
  val CURRENT_RAW_DATA = "CurrentRawData"
  val PREVIOUS_RAW_DATA = "PreviousRawData"

  val PARTNER = "Partner"
  val LEGAL_NATURE = "LegalNature"
  val TYPE_OF_CURRENCY = "TypeOfCurrency"
  val CO_UF = "co_uf"
  val CO_NAT_JUR = "co_nat_jur"
  val CO_TERC_DIG_NIRE = "co_terc_dig_nire"
  val JUNTA_CODE_UF = "JuntaCodeUF"
  val CO_JUNTA_COMER = "co_junta_comer"
  val DE_NAT_JUR = "de_nat_jur"
  val CO_TIPO_MOEDA = "co_tipo_moeda"
  val CO_TIPO_ARQUIV = "co_tipo_arquiv"
  val CO_UF_JUNTA = "co_uf_junta"
  val DT_REMESSA = "dt_remessa"
  val NU_DOCUMENTO = "nu_documento"
  val DT_CONSTITUICAO = "dt_constituicao"
  val DT_DESLIGAMENT = "dt_desligament"
  val NU_NIRE_SOCIOPJ = "nu_nire_sociopj"
  val CO_SEQ_EMPRESA = "co_seq_empresa"
  val NU_TELEFONE = "nu_telefone"
  val DE_ENDERECO = "de_endereco"
  val NU_ENDERECO = "nu_endereco"
  val CO_CEP = "co_cep"
  val DE_ENDER_COMP = "de_ender_comp"
  val ADDRESS = "Address"
  val NO_BAIRRO = "no_bairro"
  val DE_ENDERECO_PLUS_NU_ENDERECO_PLUS_DE_ENDER_COMP = "de_endereco + nu_endereco + de_ender_comp"
  val CO_CIDADE = "co_cidade"
  val NO_CIDADE = "no_cidade"
  val NO_PESSOA = "no_pessoa"
  val CO_SEQ_SOCIO = "co_seq_socio"
  val DT_INI_ATIV = "dt_ini_ativ"
  val DT_ULT_ALT_CONTR = "dt_ult_alt_contr"
  val NU_ULTIMO_ARQ = "nu_ultimo_arq"
  val NU_NIRE_FILIAL = "nu_nire_filial"
  val CO_IND_EMP_MEI = "co_ind_emp_mei"
  val DE_EMAIL = "de_email"
  val NO_EMPRESA = "no_empresa"
  val NU_CNPJ = "nu_cnpj"
  val PARENT_TAGS = "ParentTags"
  val CLIENT = "Client"
  val XML_ROW_TAG = "rowTag"
  val DATABRICKS_XML_FORMAT = "com.databricks.spark.xml"
  val DATABRICKS_AVRO_FORMAT = "com.databricks.spark.avro"
  val IGNORE_SURROUNDING_SPACES = "ignoreSurroundingSpaces"
  val COMPANYS_SIZE_MOCK = "CompanysSizeMock"
  val CO_PORTE = "co_porte"
  val DE_PORTE = "de_porte"
  val ACTS_MOCK = "ActsMock"
  val CO_ATOS = "co_atos"
  val DE_ATOS = "de_atos"
  // Brazil Metrics Name
  val DELIMITER = "delimiter"
  val JUNTA_CODE_SIT_EMPRESA = "JuntaCodeSitEmpresa"
  val CO_SITUACAO_EMP = "co_situacaoemp"
  val DE_SITUACAO_EMP = "de_situacaoemp"
  val FL_DATA_FIM_ATV = "fl_data_fim_ativ"
  val YES = "S"
  val NO = "N"
  val SPFileType = "SP"
  val MGFileType = "MG"
  val DNRCFileType = "DNRC"
  val DE_CONDICAO = "de_condicao"
  val JUNTA_CONDICAO = "JuntaCondicao"
  val CO_CONDICAO = "co_condicao"
  val FL_REV_QUALIDADOS = "fl_rev_qualidados"
  val FL_MANUAL = "fl_manual"
  val NO_MOEDA = "no_moeda"
  val DE_RAMO_ATIV = "de_ramo_ativ"
  val ACTIVITIES_MOCK = "ActivitiesMock"
  val CO_RAMO_ATIV = "co_ramo_ativ"
  val DATA_TYPE_MOCK = "DataTypeMock"
  val DE_TIPO_DADO = "de_tipo_dado"

  val FL_ADMINISTRADOR = "fl_administrador"
  val FL_CONSELHEIRO = "fl_conselheiro"
  val FL_FISCAL = "fl_fiscal"

  // Brazil Xml Attribute names & Tag names
  object ACP005_Fields {
    val ALLOWED = true
    val NOT_ALLOWED = false
    val SUBSCRIBER_RELATION = "subscriberRelation"
    val RECORD_EXIST = "true"
    val RECORD_DOES_NOT_EXIST = "false"

    val NON_FILED = "NON_FILED"
  }

}

object DateFormat {
  val DDMMYYYY = "ddMMyyyy"
  val YYYY_MM_DD = "yyyy-MM-dd"
  val DDMMYYYYHHmmss_WITH_DOT = "dd.MM.yyyy.HH.mm.ss"
  val DDMMYYYYHHmmssZ_WITH_DOT = "dd.MM.yyyy.HH.mm.ssZ"
  val YYYYMMDD = "yyyyMMdd"
  val YYYYMMDDHHmmssS = "yyyy-MM-dd-HH.mm.ss.SSSSSS"
  val YYYYMMDDHHmmssSZ = "yyyy-MM-dd-HH.mm.ss.SSSSSSZ"
  val Z = "+0000"
  val YYYYMMDDHHmmssZ = "yyyyMMddHHmmssZ"
  val dateFormatLayoutMap = Map("SP" -> "ddMMyyyy", "DNRC" -> "yyyyMMdd", "MG" -> "yyyyMMdd")

}

object EventType {
  val CREDIT_FILE = 5
}

/* Common Delimiters */
object Delimiters {
  val HASH = "#"
  val COLON = ":"
  val COMMA = ","
  val PIPE = "|"
  val DOT = "."
  val DOT_ESCAPED = "[.]"
  val UNDERSCORE = "_"
  val SPACE = " "
  val HYPHEN = "-"
  val PIPE_CHAR = PIPE.charAt(0)
  val ZERO_INT = 0
  val TWO_INT = 2
  val FOUR_INT = 4
  val SIX_INT = 6
  val STAR = "*"
  val ONE_STRING = "1"
  val TWO_STRING = "2"
}

/* Common CSV options */
object CSVOptions {
  val CHARSET = "charset"
  val UTF8 = "UTF-8"
}

object FileType {
  val ACPO005 = "ACPO005"
}


object StateCalconstants {

  val VL_CAPITAL_SOC = "vl_capital_soc"
  val DT_VINCULACAO = "dt_vinculacao"
  val NU_NIRE_MATRIZ = "nu_nire_matriz"
  val TEN = "10"
  val TWENTY_ONE = "21"
  val R04_NUMERO_DO_CPF_CALCULATED = "R04_NumeroDoCpf_Calculated"
  val R04_NOME_DA_PESSOA_CALCULATED = "R04_NomeDaPessoa_Calculated"

}

object Curationconstant {

  // These attributes.json are used only for pinning
  val CODIGO_DO_ARQUIVO = "CodigoDoArquivo"
  val CODIGO_DA_SEQUENCIA_DA_EMPRESA = "CodigoDaSequenciaDaEmpresa"
  val NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL = "NumeroDaInscricaoNaJuntaComercial"
  val NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL_PARTNER = "NumeroDaInscricaoNaJuntaComercialPartner"
  val NUMERO_DO_CNPJ = "NumeroDoCNPJ"
  val NUMERO_DO_CNPJ_SHORT = "NumeroDoCNPJShort"
  val DATA_DA_CONSTITUICAO = "DataDaConstituicao"
  val CODIGO_DA_SEQUENCIA_DO_SOCIO = "CodigoDaSequenciaDoSocio"
  val NUMERO_DO_CNPJ_DO_SOCIO = "NumeroDoCNPJDoSocio"
  val NUMERO_DO_CPF = "NumeroDoCPF"
  val NUMERO_DO_CPF_SHORT = "NumeroDoCPFShort"
  val NU_DOCUMENTO_CAD_CURATED = "nu_documento_cad_curated"

  val R01_LEGAL_NATURE_DESCRIPTION = "R01_LegalNatureDescription_Curated"
  val NOME_DA_EMPRESA = "NomeDaEmpresa"
  val LAYOUT = "layout"
  val DATA_DA_ULTIMA_ALTERACAO_CONTRATUAL_CURATED = "DataDaUltimaAlteracaoContratual_Curated"
  val DATA_DA_CONSTITUICAO_CURATED = "DataDaConstituicao_Curated"
  val DATA_DE_TERMINO_DAS_ATIVIDADES_CURATED = "DataDeTerminoDasAtividades_Curated"
  val R05_LEGAL_NATURE_DESCRIPTION_CURATED = "R05_LegalNatureDescription_Curated"
  val R01_DATA_DA_ULTIMA_ALTERACAO_CONTRATUAL_CURATED = "R01DataDaUltimaAlteracaoContratual_Curated"
  val R05_DATA_ULTIMO_ARQUIVAMENTO_CURATED = "R05DataUltimoArquivamento_Curated"
  val RECORD_TYPE_CURATED = "recordType_Curated"
  val RECEIVED_DATE_CURATED = "receivedDate_Curated"
  val IS_VALID_NIRE_CURATED = "isValidNire_Curated"
  val IS_VALID_SOCIO_NIRE_CURATED = "isValidSocioNire_Curated"
  val IS_VALID_CNPJ_CURATED = "isValidCNPJ_Curated"
  val R15_LEGAL_NATURE_DESCRIPTION_CURATED = "R15_LegalNatureDescription_Curated"
  val R01_DATA_DA_CONSTITUCAO_CURATED = "R01_DataDaConstitucao_Curated"
  val R02_DATA_DA_VINCULACAO_CURATED = "R02_DataDaVinculacao_Curated"
  val R02_DATA_DO_DESLIGAMENTO_CURATED = "R02_DataDoDesligamento_Curated"
  val R03_DATA_DA_VINCULACAO_CURATED = "R03_DataDaVinculacao_Curated"
  val R03_DATA_DO_DESLIGAMENTO_CURATED = "R03_DataDoDesligamento_Curated"
  val R01_VALOR_DO_CAPITAL_SOCIAL_CURATED = "R01_ValordoCapitalSocial_Curated"
  val R01_CODIGO_DO_TIPO_DE_MOEDA_CURATED = "R01_CodigoDoTipoDeMoeda_Curated"
  val R05_VALOR_DO_CAPITAL_SOCIAL_CURATED = "R05_ValordoCapitalSocial_Curated"
  val R05_CODIGO_DO_TIPO_DE_MOEDA_CURATED = "R05_CodigoDoTipoDeMoeda_Curated"
  val NUMERO_DA_INSCRICAO_DA_FILIAL_NA_JUNTA_COMERCIAL_CURATED = "NumeroDaInscricaoDaFilialNaJuntaComercial_Curated"
  val R05_NOME_DA_EMPRESA_CURATED = "R05_NomeDaEmpresa_Curated"
  val R15_NUMERO_DO_CNPJ_CURATED = "R15_NumeroDoCNPJ_Curated"
  val R05_NUMERO_DO_CNPJ_CURATED = "R05_NumeroDoCNPJ_Curated"
  val R01_NOME_DA_EMPRESA_CURATED = "R01_NomeDaEmpresa_Curated"
  val R04_NOME_DA_PESSOA_CURATED = "R04_NomeDaPessoa_Curated"
  val R04_NUMERO_DO_CPF_CURATED = "R04_NumeroDoCPF_Curated"
  val R03_CODIGO_DA_CONDICAO_DESCRIPTION_CURATED = "R03_CodigoDaCondicaoDescription_Curated"
  val R03_CODIGO_DA_CONDICAO_REVIEW_FLAG_CURATED = "R03_CodigoDaCondicaoReviewFlag_Curated"
  val R03_CODIGO_DA_CONDICAO_MANUAL_FLAG_CURATED = "R03_CodigoDaCondicaoManualFlag_Curated"
  val R03_CODIGO_DO_TIPODE_MOEDA_DESCRIPTION_CURATED = "R03_CodigoDoTipoDeMoedaDescription_Curated"
  val R03_CODIGO_DO_TIPODE_MOEDA_REVIEW_FLAG_CURATED = "R03_CodigoDoTipoDeMoedaReviewFlag_Curated"
  val R03_CODIGO_DO_TIPODE_MOEDA_MANUAL_FLAG_CURATED = "R03_CodigoDoTipoDeMoedaManualFlag_Curated"
  val R06_CODIGO_DO_RAMO_DE_ATIVIDADE_DESCIPTION_CURATED = "R06_CodigoDoRamoDeAtividadeDescription_Curated"
  val R06_CODIGO_DO_RAMO_DE_ATIVIDADE_REVIEW_FLAG_CURATED = "R06_CodigoDoRamoDeAtividadeReviewFlag_Curated"
  val R06_CODIGO_DO_RAMO_DE_ATIVIDADE_MANUAL_FLAG_CURATED = "R06_CodigoDoRamoDeAtividadeManualFlag_Curated"
  val R01_CODIGO_DA_SITUACAO_DA_EMPRESA_DESCRIPTION_CURATED = "R01_CodigoDaSituacaoDaEmpresaDescription_Curated"
  val R01_CODIGO_DA_SITUACAO_DA_EMPRESA_REVIEW_FLAG_CURATED = "R01_CodigoDaSituacaoDaEmpresaReviewFlag_Curated"
  val R01_CODIGO_DA_SITUACAO_DA_EMPRESA_MANUAL_FLAG_CURATED = "R01_CodigoDaSituacaoDaEmpresaManualFlag_Curated"
  val R01_CODIGO_DO_TIPO_DE_MOEDA_DESCRIPTION_CURATED = "R01_CodigoDoTipoDeMoedaDescription_Curated"
  val R01_CODIGO_DO_TIPO_DE_MOEDA_REVIEW_FLAG_CURATED = "R01_CodigoDoTipoDeMoedaReviewFlag_Curated"
  val R01_CODIGO_DO_TIPO_DE_MOEDA_MANUAL_FLAG_CURATED = "R01_CodigoDoTipoDeMoedaManualFlag_Curated"
  val R01_CODIGO_DA_NATUREZA_JURIDICA_DESCRIPTION_CURATED = "R01_CodigoDaNaturezaJuridicaDescription_Curated"
  val R01_CODIGO_DA_NATUREZA_JURIDICA_REVIEW_FLAG_CURATED = "R01_CodigoDaNaturezaJuridicaReviewFlag_Curated"
  val R01_CODIGO_DA_NATUREZA_JURIDICA_MANUAL_FLAG_CURATED = "R01_CodigoDaNaturezaJuridicaManualFlag_Curated"
  val R01_CODIGO_DO_PORTE_DESCRIPTION_CURATED = "R01_CodigoDoPorteDescription_Curated"
  val R01_CODIGO_DO_PORTE_REVIEW_FLAG_CURATED = "R01_CodigoDoPorteReviewFlag_Curated"
  val R01_CODIGO_DO_PORTE_MANUAL_FLAG_CURATED = "R01_CodigoDoPorteManualFlag_Curated"
  val R01_ATOS_DESCRIPTION_CURATED = "R01_AtosDescription_Curated"
  val R01_ATOS_REVIEW_FLAG_CURATED = "R01_AtosReviewFlag_Curated"
  val R01_ATOS_MANUAL_FLAG_CURATED = "R01_AtosManualFlag_Curated"
  val IS_VALID_NIRE_SOCIO_CURATED = "isValidNireSocio_Curated"

  val R02_CODIGO_DA_CONDICAO_DESCRIPTION_CURATED = "R02_CodigoDaCondicaoDescription_Curated"
  val R02_CODIGO_DA_CONDICAO_REVIEW_FLAG_CURATED = "R02_CodigoDaCondicaoReviewFlag_Curated"
  val R02_CODIGO_DA_CONDICAO_MANUAL_FLAG_CURATED = "R02_CodigoDaCondicaoManualFlag_Curated"
  val R02_CODIGO_DO_TIPODE_MOEDA_DESCRIPTION_CURATED = "R02_CodigoDoTipoDeMoedaDescription_Curated"
  val R02_CODIGO_DO_TIPODE_MOEDA_REVIEW_FLAG_CURATED = "R02_CodigoDoTipoDeMoedaReviewFlag_Curated"
  val R02_CODIGO_DO_TIPODE_MOEDA_MANUAL_FLAG_CURATED = "R02_CodigoDoTipoDeMoedaManualFlag_Curated"


  val R05_CODIGO_DA_NATUREZA_JURIDICA_REVIEW_FLAG_CURATED = "R05_CodigoDaNaturezaJuridicaReviewFlag_Curated"
  val R05_CODIGO_DA_NATUREZA_JURIDICA_MANUAL_FLAG_CURATED = "R05_CodigoDaNaturezaJuridicaManualFlag_Curated"
  val R05_CODIGO_DO_TIPODE_MOEDA_DESCRIPTION_CURATED = "R05_CodigoDoTipoDeMoedaDescription_Curated"
  val R05_CODIGO_DO_TIPODE_MOEDA_REVIEW_FLAG_CURATED = "R05_CodigoDoTipoDeMoedaReviewFlag_Curated"
  val R05_CODIGO_DO_TIPODE_MOEDA_MANUAL_FLAG_CURATED = "R05_CodigoDoTipoDeMoedaManualFlag_Curated"
  val R05_CODIGO_DA_SITUACAO_DA_EMPRESA_DESCRIPTION_CURATED = "R05_CodigoDaSituacaoDaEmpresaDescription_Curated"
  val R05_CODIGO_DA_SITUACAO_DA_EMPRESA_REVIEW_FLAG_CURATED = "R05_CodigoDaSituacaoDaEmpresaReviewFlag_Curated"
  val R05_CODIGO_DA_SITUACAO_DA_EMPRESA_MANUAL_FLAG_CURATED = "R05_CodigoDaSituacaoDaEmpresaManualFlag_Curated"

  val R15_CODIGO_DA_NATUREZA_JURIDICA_REVIEW_FLAG_CURATED = "R15_CodigoDaNaturezaJuridicaReviewFlag_Curated"
  val R15_CODIGO_DA_NATUREZA_JURIDICA_MANUAL_FLAG_CURATED = "R15_CodigoDaNaturezaJuridicaManualFlag_Curated"
  val R15_CODIGO_DO_TIPODE_MOEDA_DESCRIPTION_CURATED = "R15_CodigoDoTipoDeMoedaDescription_Curated"
  val R15_CODIGO_DO_TIPODE_MOEDA_REVIEW_FLAG_CURATED = "R15_CodigoDoTipoDeMoedaReviewFlag_Curated"
  val R15_CODIGO_DO_TIPODE_MOEDA_MANUAL_FLAG_CURATED = "R15_CodigoDoTipoDeMoedaManualFlag_Curated"
  val R15_CODIGO_DA_SITUACAO_DA_EMPRESA_DESCRIPTION_CURATED = "R15_CodigoDaSituacaoDaEmpresaDescription_Curated"
  val R15_CODIGO_DA_SITUACAO_DA_EMPRESA_REVIEW_FLAG_CURATED = "R15_CodigoDaSituacaoDaEmpresaReviewFlag_Curated"
  val R15_CODIGO_DA_SITUACAO_DA_EMPRESA_MANUAL_FLAG_CURATED = "R15_CodigoDaSituacaoDaEmpresaManualFlag_Curated"

  val R02_CODIGO_DA_CONDICAO_FL_ADMINISTRADOR_CURATED = "R02_CodigoDaCondicaofl_administrador_Curated"
  val R02_CODIGO_DA_CONDICAO_FL_CONSELHEIRO_CURATED = "R02_CodigoDaCondicaofl_conselheiro_Curated"
  val R02_CODIGO_DA_CONDICAO_FL_FISCAL_CURATED = "R02_CodigoDaCondicaofl_fiscal_Curated"

  val R03_CODIGO_DA_CONDICAO_FL_ADMINISTRADOR_CURATED = "R03_CodigoDaCondicaofl_administrador_Curated"
  val R03_CODIGO_DA_CONDICAO_FL_CONSELHEIRO_CURATED = "R03_CodigoDaCondicaofl_conselheiro_Curated"
  val R03_CODIGO_DA_CONDICAO_FL_FISCAL_CURATED = "R03_CodigoDaCondicaofl_fiscal_Curated"


  val RECORD_TYPE_01 = "01"
  val RECORD_TYPE_02 = "02"
  val RECORD_TYPE_03 = "03"
  val RECORD_TYPE_04 = "04"
  val RECORD_TYPE_05 = "05"
  val RECORD_TYPE_06 = "06"
  val RECORD_TYPE_15 = "15"
  val RECORD_TYPE_17 = "17"
  val RECORD_TYPE_18 = "18"
  val TRUE = "true"
  val FALSE = "false"
  val R01_NUMERO_DA_CNPJ_CURATED = "R01_NumeroDaCNPJ_Curated"
  val APPEND_ZERO = "000000"
  val BRA = "BRA"
}

object ReceitaFederalCompany {

  val TS_INCLUSAO_HIS = "ts_inclusao_his"
  val AUD_APPLY_TIMESTAMP = "aud_apply_timestamp"
  val MATCH_100_PERC = "1.0"
  val NU_DOCUMENTO_CAD = "nu_documento_cad"
}

object ReceitaFederalIndividual {
  val NU_DOCUMENTO_CAD = "nu_documento_cad"
  val NO_COMPLETO_CAD = "no_completo_cad"
  val DT_ABERTURA_CAD = "dt_abertura_cad"
  val AUD_APPLY_TIMESTAMP = "aud_apply_timestamp"
  val MATCH_100_PERC = "1.0"
}

object JuntaCommercialFields {

  /**
    * For Record Type 00, followings are the naming convention
    */
  val R00_CODIGO_DE_REGISTRO = "R00_CodigoDeRegistro"
  val R00_TIPO_DE_REMESSA = "R00_TipoDeRemessa"
  val R00_CODIGO_DA_JUNTA_COMERCIAL = "R00_CodigoDaJuntaComercial"
  val R00_SIGLA_DA_UNIDADE_FEDERATIVA_DA_JUNTA = "R00_SiglaDaUnidadeFederativaDaJunta"
  val R00_DATA_DO_PERIODO_DE_REMESSA_DE = "R00_DataDoPeriodoDeRemessaDe"
  val R00_DATA_DO_PERIODO_DE_REMESSA_ATE = "R00_DataDoPeriodoDeRemessaAte"
  val R00_PERIODICIDADE = "R00_Periodicidade"
  val R00_ULTIMA_VERSAO_DO_SISTEMA = "R00_UltimaVersaoDoSistema"
  val R00_DATA_DE_ENVIO_DA_REMESSA = "R00_DataDeEnvioDaRemessa"
  val R00_BRANCOS = "R00_Brancos"


  /**
    * For Record Type 01, followings are the naming convention
    */
  val R01_CODIGO_DO_ARQUIVO = "R01_CodigoDoArquivo"
  val R01_CODIGO_DA_SEQUENCIA_DA_EMPRESA = "R01_CodigoDaSequenciaDaEmpresa"
  val R01_CODIGO_DA_JUNTA_COMERCIAL = "R01_CodigoDaJuntaComercial"
  val R01_CODIGO_SEQUENCIAL = "R01_CodigoSequencial"
  val R01_NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL = "R01_NumeroDaInscricaoNaJuntaComercial"
  val R01_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R01_NumeroDaInscricaoDaSedeNaJuntaComercial"
  val R01_NOME_DA_EMPRESA = "R01_NomeDaEmpresa"
  val R01_DESCRICAO_DO_LOGRADOURO = "R01_DescricaoDoLogradouro"
  val R01_NUMERO_DO_LOGRADOURO = "R01_NumeroDoLogradouro"
  val R01_DESCRICAO_COMPLEMENTO_DO_LOGRADOURO = "R01_DescricaoComplementoDoLogradouro"
  val R01_NOME_DO_BAIRRO = "R01_NomedoBairro"
  val R01_CODIGO_DO_CEP = "R01_CodigoDoCep"
  val R01_CODIGO_DA_CIDADE = "R01_CodigoDaCidade"
  val R01_CODIGO_DO_PAIS = "R01_CodigoDoPais"
  val R01_DESCRICAO_DO_EMAIL = "R01_DescricaoDoEMail"
  val R01_NUMERO_DO_FAX = "R01_NumeroDoFAX"
  val R01_NUMERO_DOS_TELEFONES = "R01_NumeroDosTelefones"
  val R01_CODIGO_DA_SITUACAO_DA_EMPRESA = "R01_CodigoDaSituacaoDaEmpresa"
  val R01_CODIGO_IND_AUTORI_GOV = "R01_CodigoIndAutoriGov"
  val R01_DATA_DE_INICIO_DAS_ATIVIDADES = "R01_DataDeInicioDasAtividades"
  val R01_DATA_DE_TERMINO_DAS_ATIVIDADES = "R01_DataDeTerminoDasAtividades"
  val R01_VALOR_DO_CAPITAL_SOCIAL = "R01_ValorDoCapitalSocial"
  val R01_CODIGO_DO_TIPO_DE_MOEDA = "R01_CodigoDoTipoDeMoeda"
  val R01_DATA_DA_CONSTITUICAO = "R01_DataDaConstituicao"
  val R01_CODIGO_DA_NATUREZA_JURIDICA = "R01_CodigoDaNaturezaJuridica"
  val R01_NUMERO_DO_CNPJ = "R01_NumeroDoCNPJ"
  val R01_NUMERO_DA_INSCRICAO_ESTADUAL = "R01_NumeroDaInscricaoEstadual"
  val R01_CODIGO_DO_PORTE = "R01_CodigoDoPorte"
  val R01_DATA_DA_ULTIMA_ALTERACAO_CONTRATUAL = "R01_DataDaUltimaAlteracaoContratual"
  val R01_NOME_DA_EMPRESA_ANTECESSORA = "R01_NomeDaEmpresaAntecessora"
  val R01_CODIGO_DA_UF = "R01_CodigoDaUF"
  val R01_NOME_DA_CIDADE = "R01_NomeDaCidade"
  val R01_ATOS = "R01_Atos"
  val R01_TIPO_DE_DADO = "R01_TipoDeDado"
  val R01_NUMERO_ULTIMO_ARQUIVAMENTO = "R01_NumeroUltimoArquivamento"
  val R01_INDICADOR_DE_EMPRESA_MEI = "R01_IndicadorDeEmpresaMEI"
  val R01_EMPRESSA_MEI_S_N = "R01_EmpresaMEI_S_N"
  val R01_BRANCOS = "R01_Brancos"


  /**
    * For Record Type 02, followings are the naming convention
    */
  val R02_CODIGO_DO_ARQUIVO = "R02_CodigoDoArquivo"
  val R02_CODIGO_DA_JUNTA_COMERCIAL = "R02_CodigoDaJuntaComercial"
  val R02_CODIGO_SEQUENCIAL = "R02_CodigoSequencial"
  val R02_CODIGO_DA_SEQUENCIA_DO_SOCIO = "R02_CodigoDaSequenciaDoSocio"
  val R02_CODIGO_DA_SEQUENCIA_DA_EMPRESA = "R02_CodigoDaSequenciaDaEmpresa"
  val R02_CODIGO_DA_CONDICAO = "R02_CodigoDaCondicao"
  val R02_DATA_DA_VINCULACAO = "R02_DataDaVinculacao"
  val R02_DATA_DO_DESLIGAMENTO = "R02_DataDoDesligamento"
  val R02_VALOR_DO_CAPITAL_SOCIAL = "R02_ValorDoCapitalSocial"
  val R02_CODIGO_DO_TIPO_DE_MOEDA = "R02_CodigoDoTipoDeMoeda"
  val R02_NUMERO_DO_CPF = "R02_NumeroDoCPF"
  val R02_CODIGO_DO_DEPENDENTE = "R02_CodigoDoDependente"
  val R02_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R02_NumeroDaInscricaoDaSedeNaJuntaComercial"
  val R02_DATA_DE_TERMINO_DO_MANDATO = "R02_DataDeTerminoDoMandato"
  val R02_BRANCOS = "R02_Brancos"
  val R02_AS_OF_DATE = "R02_AsOfDate"

  /**
    * For Record Type 02 and 04, followings are the naming convention
    */
  val R02R04_CODIGO_DO_ARQUIVO = "R02R04_CodigoDoArquivo"
  val R02R04_CODIGO_DA_SEQUENCIA_DO_SOCIO = "R02R04_CodigoDaSequenciaDoSocio"
  val R02R04_CODIGO_DA_JUNTA_COMERCIAL = "R02R04_CodigoDaJuntaComercial"
  val R02R04_CODIGO_SEQUENCIAL = "R02R04_CodigoSequencial"
  val R02R04_NOME_DA_PESSOA = "R02R04_NomeDaPessoa"
  val R02R04_DESCRICAO_DO_LOGRADOURO = "R02R04_DescricaoDoLogradouro"
  val R02R04_NUMERO_DO_LOGRADOURO = "R02R04_NumeroDoLogradouro"
  val R02R04_DESCRICAO_COMPLEMENTO_DO_LOGRADOURO = "R02R04_DescricaoComplementoDoLogradouro"
  val R02R04_CODIGO_DO_CEP = "R02R04_CodigoDoCep"
  val R02R04_CODIGO_DA_CIDADE = "R02R04_CodigoDaCidade"
  val R02R04_CODIGO_DO_PAIS = "R02R04_CodigoDoPais"
  val R02R04_DESCRICAO_DO_EMAIL = "R02R04_DescricaoDoEMail"
  val R02R04_NUMERO_DO_FAX = "R02R04_NumeroDoFAX"
  val R02R04_NUMERO_DOS_TELEFONES = "R02R04_NumeroDosTelefones"
  val R02R04_NUMERO_DO_CPF = "R02R04_NumeroDoCPF"
  val R02R04_CODIGO_DO_DEPENDENTE = "R02R04_CodigoDoDependente"
  val R02R04_DESCRICAO_DA_NACIONALIDADE = "R02R04_DescricaoDaNacionalidade"
  val R02R04_CODIGO_DA_UF = "R02R04_CodigoDaUF"
  val R02R04_NOME_DO_BAIRRO = "R02R04_NomeDoBairro"
  val R02R04_NOME_DA_CIDADE = "R02R04_NomeDaCidade"
  val R02R04_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R02R04_NumeroDaInscricaoDaSedeNaJuntaComercial"
  val R02R04_BRANCOS = "R02R04_Brancos"
  val R02R04_AS_OF_DATE = "R02R04_AsOfDate"

  /**
    * For Record Type 02 and 01, followings are the naming convention
    */
  val R02R01_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R02R01_NumeroDaInscricaoDaSedeNaJuntaComercial"


  /**
    * For Record Type 03, followings are the naming convention
    */

  val R03_CODIGO_DO_ARQUIVO = "R03_CodigoDoArquivo"
  val R03_CODIGO_DA_JUNTA_COMERCIAL = "R03_CodigoDaJuntaComercial"
  val R03_CODIGO_SEQUENCIAL = "R03_CodigoSequencial"
  val R03_CODIGO_DA_SEQUENCIA_DO_SOCIO = "R03_CodigoDaSequenciaDoSocio"
  val R03_CODIGO_DA_SEQUENCIA_DA_EMPRESA = "R03_CodigoDaSequenciaDaEmpresa"
  val R03_CODIGO_DA_CONDICAO = "R03_CodigoDaCondicao"
  val R03_DATA_DA_VINCULACAO = "R03_DataDaVinculacao"
  val R03_DATA_DO_DESLIGAMENTO = "R03_DataDoDesligamento"
  val R03_VALOR_DO_CAPITAL_SOCIAL = "R03_ValorDoCapitalSocial"
  val R03_CODIGO_DO_TIPO_DE_MOEDA = "R03_CodigoDoTipoDeMoeda"
  val R03_NUMERO_DO_CNPJ_DO_SOCIO = "R03_NumeroDoCNPJDoSocio"
  val R03_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R03_NumeroDaInscricaoDaSedeNaJuntaComercial"
  val R03_NUMERO_DA_INSCRICAO_DO_SOCIO_NA_JUNTA_COMERCIAL = "R03_NumeroDaInscricaoDoSocioNaJuntaComercial"
  val R03_AS_OF_DATE = "R03_AsOfDate"

  /**
    * For Record Type 03 and 15, followings are the naming convention
    */

  val R03R15_CODIGO_DO_ARQUIVO = "R03R15_CodigoDoArquivo"
  val R03R15_CODIGO_DA_SEQUENCIA_DO_SOCIO = "R03R15_CodigoDaSequenciaDoSocio"
  val R03R15_CODIGO_DA_JUNTA_COMERCIAL = "R03R15_CodigoDaJuntaComercial"
  val R03R15_CODIGO_SEQUENCIAL = "R03R15_CodigoSequencial"
  val R03R15_NUMERO_DA_INSCRICAO_DO_SOCIO_NA_JUNTA_COMERCIAL = "R03R15_NumeroDaInscricaoDoSocioNaJuntaComercial"
  val R03R15_NOME_DA_EMPRESA = "R03R15_NomeDaEmpresa"
  val R03R15_DESCRICAO_DO_LOGRADOURO = "R03R15_DescricaoDoLogradouro"
  val R03R15_NUMERO_DO_LOGRADOURO = "R03R15_NumeroDoLogradouro"
  val R03R15_DESCRICAO_COMPLEMENTO_DO_LOGRADOURO = "R03R15_DescricaoComplementoDoLogradouro"
  val R03R15_NOME_DO_BAIRRO = "R03R15_NomedoBairro"
  val R03R15_CODIGO_DO_CEP = "R03R15_CodigoDoCep"

  val R03R15_CODIGO_DA_CIDADE = "R03R15_CodigoDaCidade"
  val R03R15_CODIGO_DO_PAIS = "R03R15_CodigoDoPais"
  val R03R15_DESCRICAO_DO_EMAIL = "R03R15_DescricaoDoEMail"
  val R03R15_NUMERO_DO_FAX = "R03R15_NumeroDoFAX"
  val R03R15_NUMERO_DOS_TELEFONES = "R03R15_NumeroDosTelefones"
  val R03R15_CODIGO_DA_SITUACAO_DA_EMPRESA = "R03R15_CodigoDaSituacaoDaEmpresa"
  val R03R15_CODIGO_IND_AUTORI_GOV = "R03R15_CodigoIndAutoriGov"
  val R03R15_DATA_DE_INICIO_DAS_ATIVIDADES = "R03R15_DataDeInicioDasAtividades"
  val R03R15_DATA_DE_TERMINO_DAS_ATIVIDADES = "R03R15_DataDeTerminoDasAtividades"
  val R03R15_VALOR_DO_CAPITAL_SOCIAL = "R03R15_ValorDoCapitalSocial"
  val R03R15_CODIGO_DO_TIPO_DE_MOEDA = "R03R15_CodigoDoTipoDeMoeda"
  val R03R15_DATA_DA_CONSTITUICAO = "R03R15_DataDaConstituicao"
  val R03R15_CODIGO_DA_NATUREZA_JURIDICA = "R03R15_CodigoDaNaturezaJuridica"
  val R03R15_NUMERO_DO_CNPJ = "R03R15_NumeroDoCNPJ"
  val R03R15_NUMERO_DA_INSCRICAO_ESTADUAL = "R03R15_NumeroDaInscricaoEstadual"
  val R03R15_CODIGO_DO_PORTE = "R03R15_CodigoDoPorte"
  val R03R15_CODIGO_DA_UF = "R03R15_CodigoDaUF"
  val R03R15_NOME_DA_CIDADE = "R03R15_NomeDaCidade"
  val R03R15_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R03R15_NumeroDaInscricaoDaSedeNaJuntaComercial"
  val R03R15_BRANCOS = "R03R15_Brancos"
  val R03R15_AS_OF_DATE = "R03R15_AsOfDate"

  /**
    * For Record Type 04, followings are the naming convention
    */
  val R04_CODIGO_DO_ARQUIVO = "R04_CodigoDoArquivo"
  val R04_CODIGO_DA_SEQUENCIA_DO_SOCIO = "R04_CodigoDaSequenciaDoSocio"
  val R04_CODIGO_DA_JUNTA_COMERCIAL = "R04_CodigoDaJuntaComercial"
  val R04_CODIGO_SEQUENCIAL = "R04_CodigoSequencial"
  val R04_NOME_DA_PESSOA = "R04_NomeDaPessoa"
  val R04_DESCRICAO_DO_LOGRADOURO = "R04_DescricaoDoLogradouro"
  val R04_NUMERO_DO_LOGRADOURO = "R04_NumeroDoLogradouro"
  val R04_DESCRICAO_COMPLEMENTO_DO_LOGRADOURO = "R04_DescricaoComplementoDoLogradouro"
  val R04_CODIGO_DO_CEP = "R04_CodigoDoCep"
  val R04_CODIGO_DA_CIDADE = "R04_CodigoDaCidade"
  val R04_CODIGO_DO_PAIS = "R04_CodigoDoPais"
  val R04_DESCRICAO_DO_EMAIL = "R04_DescricaoDoEMail"
  val R04_NUMERO_DO_FAX = "R04_NumeroDoFAX"
  val R04_NUMERO_DOS_TELEFONES = "R04_NumeroDosTelefones"
  val R04_NUMERO_DO_CPF = "R04_NumeroDoCPF"
  val R04_CODIGO_DO_DEPENDENTE = "R04_CodigoDoDependente"
  val R04_DESCRICAO_DA_NACIONALIDADE = "R04_DescricaoDaNacionalidade"
  val R04_CODIGO_DA_UF = "R04_CodigoDaUF"
  val R04_NOME_DO_BAIRRO = "R04_NomeDoBairro"
  val R04_NOME_DA_CIDADE = "R04_NomeDaCidade"
  val R04_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R04_NumeroDaInscricaoDaSedeNaJuntaComercial"
  val R04_BRANCOS = "R04_Brancos"
  val R04_AS_OF_DATE = "R04_AsOfDate"

  /**
    * For Record Type R04R02, followings are the naming convention
    */
  val R04R02_CODIGO_DO_ARQUIVO = "R04R02_CodigoDoArquivo"
  val R04R02_CODIGO_DA_JUNTA_COMERCIAL = "R04R02_CodigoDaJuntaComercial"
  val R04R02_CODIGO_SEQUENCIAL = "R04R02_CodigoSequencial"
  val R04R02_CODIGO_DA_SEQUENCIA_DO_SOCIO = "R04R02_CodigoDaSequenciaDoSocio"
  val R04R02_CODIGO_DA_SEQUENCIA_DA_EMPRESA = "R04R02_CodigoDaSequenciaDaEmpresa"
  val R04R02_CODIGO_DA_CONDICAO = "R04R02_CodigoDaCondicao"
  val R04R02_DATA_DA_VINCULACAO = "R04R02_DataDaVinculacao"
  val R04R02_DATA_DO_DESLIGAMENTO = "R04R02_DataDoDesligamento"
  val R04R02_VALOR_DO_CAPITAL_SOCIAL = "R04R02_ValorDoCapitalSocial"
  val R04R02_CODIGO_DO_TIPO_DE_MOEDA = "R04R02_CodigoDoTipoDeMoeda"
  val R04R02_NUMERO_DO_CPF = "R04R02_NumeroDoCPF"
  val R04R02_CODIGO_DO_DEPENDENTE = "R04R02_CodigoDoDependente"
  val R04R02_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R04R02_NumeroDaInscricaoDaSedeNaJuntaComercial"
  val R04R02_DATA_DE_TERMINO_DO_MANDATO = "R04R02_DataDeTerminoDoMandato"
  val R04R02_BRANCOS = "R04R02_Brancos"

  /**
    * For Record Type 05, followings are the naming convention
    */

  val R05_CODIGO_DO_ARQUIVO = "R05_CodigoDoArquivo"
  val R05_CODIGO_DA_SEQUENCIA_DA_EMPRESA = "R05_CodigoDaSequenciaDaEmpresa"
  val R05_CODIGO_DA_JUNTA_COMERCIAL = "R05_CodigoDaJuntaComercial"
  val R05_CODIGO_SEQUENCIAL = "R05_CodigoSequencial"
  val R05_NUMERO_DA_INSCRICAO_DA_FILIAL_NA_JUNTA_COMERCIAL = "R05_NumeroDaInscricaoDaFilialNaJuntaComercial"
  val R05_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R05_NumeroDaInscricaoDaSedeNaJuntaComercial"
  val R05_NOME_DA_EMPRESA = "R05_NomeDaEmpresa"
  val R05_DESCRICAO_DO_LOGRADOURO = "R05_DescricaoDoLogradouro"
  val R05_NUMERO_DO_LOGRADOURO = "R05_NumeroDoLogradouro"
  val R05_DESCRICAO_COMPLEMENTO_DO_LOGRADOURO = "R05_DescricaoComplementoDoLogradouro"
  val R05_NOME_DO_BAIRRO = "R05_NomedoBairro"
  val R05_CODIGO_DO_CEP = "R05_CodigoDoCep"
  val R05_CODIGO_DA_CIDADE = "R05_CodigoDaCidade"
  val R05_CODIGO_DO_PAIS = "R05_CodigoDoPais"
  val R05_DESCRICAO_DO_EMAIL = "R05_DescricaoDoEMail"
  val R05_NUMERO_DO_FAX = "R05_NumeroDoFAX"
  val R05_NUMERO_DOS_TELEFONES = "R05_NumeroDosTelefones"
  val R05_CODIGO_DA_SITUACAO_DA_EMPRESA = "R05_CodigoDaSituacaoDaEmpresa"
  val R05_CODIGO_IND_AUTORI_GOV = "R05_CodigoIndAutoriGov"
  val R05_DATA_DE_INICIO_DAS_ATIVIDADES = "R05_DataDeInicioDasAtividades"
  val R05_DATA_DE_TERMINO_DAS_ATIVIDADES = "R05_DataDeTerminoDasAtividades"
  val R05_VALOR_DO_CAPITAL_SOCIAL = "R05_ValorDoCapitalSocial"
  val R05_CODIGO_DO_TIPO_DE_MOEDA = "R05_CodigoDoTipoDeMoeda"
  val R05_DATA_DA_CONSTITUICAO = "R05_DataDaConstituicao"
  val R05_CODIGO_DA_NATUREZA_JURIDICA = "R05_CodigoDaNaturezaJuridica"
  val R05_NUMERO_DO_CNPJ = "R05_NumeroDoCNPJ"
  val R03_CNPJ_DO_SOCIO = "R03_CNPJDoSocio"
  val R05_NUMERO_DA_INSCRICAO_ESTADUAL = "R05_NumeroDaInscricaoEstadual"
  val R05_CODIGO_DO_PORTE = "R05_CodigoDoPorte"
  val R05_UF = "R05_Uf"
  val R05_CIDADE = "R05_Cidade"
  val R05_TIPO_DE_DADO = "R05_TipoDeDado"
  val R05_NUMERO_ULTIMO_ARQUIVAMENTO = "R05_NumeroUltimoArquivamento"
  val R05_DATA_ULTIMO_ARQUIVAMENTO = "R05_DataUltimoArquivamento"
  val R05_BRANCOS = "R05_Brancos"
  val R05_AS_OF_DATE = "R05_AsOfDate"

  /**
    * For Record Type 06, followings are the naming convention
    */


  val R06_CODIGO_DO_ARQUIVO = "R06_CodigoDoArquivo"
  val R06_CODIGO_DA_JUNTA_COMERCIAL = "R06_CodigoDaJuntaComercial"
  val R06_CODIGO_SEQUENCIAL = "R06_CodigoSequencial"
  val R06_CODIGO_DA_SEQUENCIA_DA_EMPRESA = "R06_CodigoDaSequenciaDaEmpresa"
  val R06_CODIGO_DO_RAMO_DE_ATIVIDADE = "R06_CodigoDoRamoDeAtividade"
  val R06_CODIGO_IND_PRINC_SECUND = "R06_CodigoIndPrincSecund"
  val R06_NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL = "R06_NumeroDaInscricaoNaJuntaComercial"
  val R06_AS_OF_DATE = "R06_AsOfDate"

  /**
    * For Record Type 07, followings are the naming convention
    */

  val R07_CODIGO_DO_ARQUIVO = "R07_CodigoDoArquivo"
  val R07_CODIGO_DA_ATIVIDADE = "R07_CodigoDaAtividade"
  val R07_DESCRICAO_DA_ATIVIDADE = "R07_DescricaoDaAtividade"

  /**
    * For Record Type 08, followings are the naming convention
    */

  val R08_CODIGO_DO_ARQUIVO = "R08_CodigoDoArquivo"
  val R08_CODIGO_DO_MUNICIPIO = "R08_CodigoDoMunicipio"
  val R08_DESCRICAO_DA_MUNICIPIO = "R08_DescricaoDaMunicipio"

  /**
    * For Record Type 09, followings are the naming convention
    */

  val R09_CODIGO_DO_ARQUIVO = "R09_CodigoDoArquivo"
  val R09_CODIGO_DA_PAIS = "R09_CodigoDoPais"
  val R09_DESCRICAO_DA_PAIS = "R09_DescricaoDoPais"

  /**
    * For Record Type 10, followings are the naming convention
    */
  val R10_CODIGO_DO_ARQUIVO = "R10_CodigoDoArquivo"
  val R10_CODIGO_DA_SITUACAO_DE_EMPRESA = "R10_CodigoDaSituacaoDeEmpresa"
  val R10_DESCRICAO_DA_SITUACAO_DE_EMPRESA = "R10_DescricaoDaSituacaoDeEmpresa"

  /**
    * For Record Type 11, followings are the naming convention
    */

  val R11_CODIGO_DO_ARQUIVO = "R11_CodigoDoArquivo"
  val R11_CODIGO_DA_NATUREZA_JURIDICA = "R11_CodigoDaNaturezaJuridica"
  val R11_DESCRICAO_DA_NATUREZA_JURIDICA = "R11_DescricaoDaNaturezaJuridica"

  /**
    * For Record Type 12, followings are the naming convention
    */

  val R12_CODIGO_DO_ARQUIVO = "R12_CodigoDoArquivo"
  val R12_CODIGO_DO_PORTE = "R12_CodigoDoPorte"
  val R12_DESCRICAO_DO_PORTE = "R12_DescricaoDoPorte"

  /**
    * For Record Type 13, followings are the naming convention
    */

  val R13_CODIGO_DO_ARQUIVO = "R13_CodigoDoArquivo"
  val R13_CODIGO_DA_CONDICAO = "R13_CodigoDaCondicao"
  val R13_DESCRICAO_DA_CONDICAO = "R13_DescricaoDaCondicao"

  /**
    * For Record Type 14, followings are the naming convention
    */

  val R14_CODIGO_DO_ARQUIVO = "R14_CodigoDoArquivo"
  val R14_CODIGO_DO_ATO = "R14_CodigoDoAto"
  val R14_DESCRICAO_DO_ATO = "R14_DescricaoDoAto"


  /**
    * For Record Type 15, followings are the naming convention
    */

  val R15_CODIGO_DO_ARQUIVO = "R15_CodigoDoArquivo"
  val R15_CODIGO_DA_SEQUENCIA_DO_SOCIO = "R15_CodigoDaSequenciaDoSocio"
  val R15_CODIGO_DA_JUNTA_COMERCIAL = "R15_CodigoDaJuntaComercial"
  val R15_CODIGO_SEQUENCIAL = "R15_CodigoSequencial"
  val R15_NUMERO_DA_INSCRICAO_DO_SOCIO_NA_JUNTA_COMERCIAL = "R15_NumeroDaInscricaoDoSocioNaJuntaComercial"
  val R15_NOME_DA_EMPRESA = "R15_NomeDaEmpresa"
  val R15_DESCRICAO_DO_LOGRADOURO = "R15_DescricaoDoLogradouro"
  val R15_NUMERO_DO_LOGRADOURO = "R15_NumeroDoLogradouro"
  val R15_DESCRICAO_COMPLEMENTO_DO_LOGRADOURO = "R15_DescricaoComplementoDoLogradouro"
  val R15_NOME_DO_BAIRRO = "R15_NomedoBairro"
  val R15_CODIGO_DO_CEP = "R15_CodigoDoCep"

  val R15_CODIGO_DA_CIDADE = "R15_CodigoDaCidade"
  val R15_CODIGO_DO_PAIS = "R15_CodigoDoPais"
  val R15_DESCRICAO_DO_EMAIL = "R15_DescricaoDoEMail"
  val R15_NUMERO_DO_FAX = "R15_NumeroDoFAX"
  val R15_NUMERO_DOS_TELEFONES = "R15_NumeroDosTelefones"
  val R15_CODIGO_DA_SITUACAO_DA_EMPRESA = "R15_CodigoDaSituacaoDaEmpresa"
  val R15_CODIGO_IND_AUTORI_GOV = "R15_CodigoIndAutoriGov"
  val R15_DATA_DE_INICIO_DAS_ATIVIDADES = "R15_DataDeInicioDasAtividades"
  val R15_DATA_DE_TERMINO_DAS_ATIVIDADES = "R15_DataDeTerminoDasAtividades"
  val R15_VALOR_DO_CAPITAL_SOCIAL = "R15_ValorDoCapitalSocial"
  val R15_CODIGO_DO_TIPO_DE_MOEDA = "R15_CodigoDoTipoDeMoeda"
  val R15_DATA_DA_CONSTITUICAO = "R15_DataDaConstituicao"
  val R15_CODIGO_DA_NATUREZA_JURIDICA = "R15_CodigoDaNaturezaJuridica"
  val R15_NUMERO_DO_CNPJ = "R15_NumeroDoCNPJ"
  val R15_NUMERO_DA_INSCRICAO_ESTADUAL = "R15_NumeroDaInscricaoEstadual"
  val R15_CODIGO_DO_PORTE = "R15_CodigoDoPorte"
  val R15_CODIGO_DA_UF = "R15_CodigoDaUF"
  val R15_NOME_DA_CIDADE = "R15_NomeDaCidade"
  val R15_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R15_NumeroDaInscricaoDaSedeNaJuntaComercial"
  val R15_BRANCOS = "R15_Brancos"
  val R15_AS_OF_DATE = "R15_AsOfDate"

  /**
    * For Record Type R15R03, followings are the naming convention
    */

  val R15R03_CODIGO_DO_ARQUIVO = "R15R03_CodigoDoArquivo"
  val R15R03_CODIGO_DA_JUNTA_COMERCIAL = "R15R03_CodigoDaJuntaComercial"
  val R15R03_CODIGO_SEQUENCIAL = "R15R03_CodigoSequencial"
  val R15R03_CODIGO_DA_SEQUENCIA_DO_SOCIO = "R15R03_CodigoDaSequenciaDoSocio"
  val R15R03_CODIGO_DA_SEQUENCIA_DA_EMPRESA = "R15R03_CodigoDaSequenciaDaEmpresa"
  val R15R03_CODIGO_DA_CONDICAO = "R15R03_CodigoDaCondicao"
  val R15R03_DATA_DA_VINCULACAO = "R15R03_DataDaVinculacao"
  val R15R03_DATA_DO_DESLIGAMENTO = "R15R03_DataDoDesligamento"
  val R15R03_VALOR_DO_CAPITAL_SOCIAL = "R15R03_ValorDoCapitalSocial"
  val R15R03_CODIGO_DO_TIPO_DE_MOEDA = "R15R03_CodigoDoTipoDeMoeda"
  val R15R03_NUMERO_DO_CNPJ_DO_SOCIO = "R15R03_NumeroDoCNPJDoSocio"
  val R15R03_NUMERO_DA_INSCRICAO_DA_SEDE_NA_JUNTA_COMERCIAL = "R15R03_NumeroDaInscricaoDaSedeNaJuntaComercial"
  val R15R03_NUMERO_DA_INSCRICAO_DO_SOCIO_NA_JUNTA_COMERCIAL = "R15R03_NumeroDaInscricaoDoSocioNaJuntaComercial"

  /**
    * For Record Type 16, followings are the naming convention
    */

  val R16_CODIGO_DO_ARQUIVO = "R16_CodigoDoArquivo"
  val R16_CODIGO_DO_ESTADO_CIVIL = "R16_CodigoDoEstadoCivil"
  val R16_DESCRICAO_DO_ESTADO_CIVIL = "R16_DescricaoDoEstadoCivil"

  /**
    * For Record Type 17, followings are the naming convention
    */

  val R17_CODIGO_DO_ARQUIVO = "R17_CodigoDoArquivo"
  val R17_NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL = "R17_NumeroDaInscricaoNaJuntaComercial"
  val R17_NUMERO_DO_CNPJ = "R17_NumeroDoCNPJ"
  val R17_INDICADOR_DE_CAPITAL_DIVERGENTE = "R17_IndicadorDeCapitalDivergente"
  val R17_INDICADOR_DE_CAPITAL_ABERTO = "R17_IndicadorDeCapitalAberto"
  val R17_NUMERO_DO_DOCUMENTO_DE_RECADASTRO = "R17_NumeroDoDocumentoDeRecadastro"
  val R17_ANO_DO_RECADASTRO = "R17_AnoDoRecadastro"
  val R17_DATA_DE_INSERCAO_DO_CAPITAL = "R17_DataDeInsercaoDoCapital"
  val R17_DATA_DA_RAZAO_SOCIAL = "R17_DataDaRazaoSocial"
  val R17_LATITUDE_DO_ENDERECO_DA_EMPRESA = "R17_LatitudeDoEnderecoDaEmpresa"
  val R17_LONGTITUDE_DO_ENDERECO_DA_EMPRESA = "R17_LongitudeDoEnderecoDaEmpresa"
  val R17_PRAZO_DE_DURACAO_DA_EMPRESA = "R17_PrazoDeDuracaoDaEmpresa"
  val R17_RAZAO_SOCIAL_ANTERIOR = "R17_RazaoSocialAnterior"
  val R17_DATA_DA_RAZAO_SOCIAL_ANTERIOR = "R17_DataDaRazaoSocialAnterior"
  val R17_NUMERO_DO_NIRE_DA_MATRIZ = "R17_NumeroDoNireDaMatriz"
  val R17_INDICADOR_DE_MUDANCA_DO_TIPO_JURIDICO = "R17_IndicadorDeMudancaDoTipoJuridico"
  val R17_DATA_DA_MUDANCA_DO_TIPO_JURIDICO = "R17_DataDaMudancaDoTipoJuridico"
  val R17_ENDERECO_DO_EMAIL = "R17_EnderecoDoEMail"
  val R17_AS_OF_DATE = "R17_AsOfDate"

  /**
    * For Record Type 18, followings are the naming convention
    */

  val R18_CODIGO_DO_ARQUIVO = "R18_CodigoDoArquivo"
  val R18_NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL = "R18_NumeroDaInscricaoNaJuntaComercial"
  val R18_TIPO_DE_PESSOA_F_J = "R18_TipoDePessoa"
  val R18_NUMERO_DO_DOCUMENTO = "R18_NumeroDoDocumento"
  val R18_DIGITO_DO_DOCUMENTO = "R18_DigitoDoDocumento"
  val R18_SEQUENCIA_DO_DOCUMENTO = "R18_SequenciaDoDocumento"
  val R18_ORGAO_EXPEDIDOR_RG_RNE = "R18_OrgaoExpedidorRG_RNE"
  val R18_TIPO_DO_REGISTRO_RG_RNE = "R18_TipoDoRegistroRG_RNE"
  val R18_NUMERO_DO_DOCUMENTO_RG_RNE = "R18_NumeroDoDocumentoRG_RNE"
  val R18_UF_EMISSOR_DO_RG_RNE = "R18_UFEmissorDoRG_RNE"
  val R18_CODIGO_DO_ESTADO_CIVIL = "R18_CodigoDoEstadoCivil"
  val R18_DESCRICAO_DO_TIPO_DO_SOCIO = "R18_DescricaoDoTipoDoSocio"
  val R18_NIRE_DO_SOCIO = "R18_NireDoSocio"
  val R18_TIPO_DO_DOCUMENTO = "R18_TipoDoDocumento"
  val R18_REGIME_DE_COMUNHAO_DE_BENS = "R18_RegimeDeComunhaoDeBens"
  val R18_ENDERECO_DO_EMAIL = "R18_EnderecoDoEMail"
  val R18_AS_OF_DATE = "R18_AsOfDate"

  /**
    * For Record Type 98, followings are the naming convention
    */
  val R98_CODIGO_DO_ARQUIVO = "R98_CodigoDoArquivo"
  val R98_01_COD_DO_TIPO_DE_DADO = "R98_CodDoTipoDeDado"
  val R98_01_DESCRICAO_DO_TIPO_DE_DADO = "R98_DescricaoDoTipoDeDado"
  val R98_COD_TIPO_DE_DADO = "R98_CodTipoDeDado"
  val R98_02_DESCRICAO_DO_TIPO_DE_DADO = "R98_DescricaoDoTipoDeDado"
  val R98_02_COD_DO_TIPO_DE_DADO = "R98_CodDoTipoDeDado"
  val R98_03_DESCRICAO_DO_TIPO_DE_DADO = "R98_DescricaoDoTipoDeDado"
  val R98_03_COD_DO_TIPO_DE_DADO = "R98_CodDoTipoDeDado"
  val R98_04_DESCRICAO_DO_TIPO_DE_DADO = "R98_DescricaoDoTipoDeDado"


  /**
    * For Record Type 99, followings are the naming convention
    */

  val R99_CODIGO_DO_ARQUIVO = "R99_CodigoDoArquivo"
  val R99_01_TEXTO_DESCRITIVO_DO_ATO_COM_15_DIGITOS = "R99_01_TextoDescritivoDoAtoCom15Digitos"
  val R99_QTDE_DE_CONSTITUICOES_SEDE = "R99_QtdeDeConstituiçoesSede"
  val R99_02_TEXTO_DESCRITIVO_DO_ATO_COM_15_DIGITOS = "R99_02_TextoDescritivoDoAtoCom15Digitos"
  val R99_QTDE_DE_ALTERACOES_SEDE = "R99_QtdeDeAlteraçoesSede"
  val R99_03_TEXTO_DESCRITIVO_DO_ATO_COM_15_DIGITOS = "R99_03_TextoDescritivoDoAtoCom15Digitos"
  val R99_QTDE_DE_EXTINCOES_SEDE = "R99_QtdeDeExtinçoesSede"
  val R99_04_TEXTO_DESCRITIVO_DO_ATO_COM_15_DIGITOS = "R99_04_TextoDescritivoDoAtoCom15Digitos"
  val R99_QTDE_DE_CONSTITUICOES_FILIAIS = "R99_QtdeDeConstituiçoesFiliais"
  val R99_05_TEXTO_DESCRITIVO_DO_ATO_COM_15_DIGITOS = "R99_05_TextoDescritivoDoAtoCom15Digitos"
  val R99_QTDE_DE_ALTERACOES_FILIAIS = "R99_QtdeDeAlteraçoesFiliais"
  val R99_06_TEXTO_DESCRITIVO_DO_ATO_COM_15_DIGITOS = "R99_06_TextoDescritivoDoAtoCom15Digitos"
  val R99_QTDE_DE_EXTINCOES_FILIAIS = "R99_QtdeDeExtinçoesFiliais"
  val R99_07_TEXTO_DESCRITIVO_DO_ATO_COM_15_DIGITOS = "R99_07_TextoDescritivoDoAtoCom15Digitos"
  val R99_QTDE_DE_RETIFICACOES_SEDE = "R99_QtdeDeRetificaçoesSede"
  val R99_08_TEXTO_DESCRITIVO_DO_ATO_COM_15_DIGITOS = "R99_08_TextoDescritivoDoAtoCom15Digitos"
  val R99_QTDE_DE_RETIFICACOES_FILIAIS = "R99_QtdeDeRetificaçoesFiliais"


  val NireMap = Map(
    "01" -> R01_NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL,
    "05" -> R05_NUMERO_DA_INSCRICAO_DA_FILIAL_NA_JUNTA_COMERCIAL,
    "15" -> R15_NUMERO_DA_INSCRICAO_DO_SOCIO_NA_JUNTA_COMERCIAL)

  val UFMap = Map(
    "01" -> R01_CODIGO_DA_UF,
    "05" -> R05_UF,
    "15" -> R15_CODIGO_DA_UF)
  val LegalNatureMap = Map(
    "01" -> R01_CODIGO_DA_NATUREZA_JURIDICA,
    "05" -> R05_CODIGO_DA_NATUREZA_JURIDICA,
    "15" -> R15_CODIGO_DA_NATUREZA_JURIDICA
  )

  val layoutMapUF = Map(
    "01" -> R01_CODIGO_DA_JUNTA_COMERCIAL,
    "02" -> R02_CODIGO_DA_JUNTA_COMERCIAL,
    "05" -> R05_CODIGO_DA_JUNTA_COMERCIAL,
    "04" -> R04_CODIGO_DA_JUNTA_COMERCIAL,
    "03" -> R03_CODIGO_DA_JUNTA_COMERCIAL,
    "15" -> R15_CODIGO_DA_JUNTA_COMERCIAL
  )
  val terminatedate = Map(
    "05" -> R05_DATA_DE_TERMINO_DAS_ATIVIDADES,
    "15" -> R15_DATA_DE_TERMINO_DAS_ATIVIDADES,
    "01" -> R01_DATA_DE_TERMINO_DAS_ATIVIDADES
  )
  val situationCode = Map(
    "05" -> R05_CODIGO_DA_SITUACAO_DA_EMPRESA,
    "15" -> R15_CODIGO_DA_SITUACAO_DA_EMPRESA,
    "01" -> R01_CODIGO_DA_SITUACAO_DA_EMPRESA

  )

  val valorDoCaptalSocialMap = Map(
    "01" -> R01_VALOR_DO_CAPITAL_SOCIAL,
    "05" -> R05_VALOR_DO_CAPITAL_SOCIAL
  )

  val codigoDoTipoDeMoedaMap = Map(
    "01" -> R01_CODIGO_DO_TIPO_DE_MOEDA,
    "05" -> R05_CODIGO_DO_TIPO_DE_MOEDA
  )

  val dataDaConstituicaoMap = Map(
    "01" -> R01_DATA_DA_CONSTITUICAO,
    "05" -> R05_DATA_DA_CONSTITUICAO
  )
  val companySituationCodeMap = Map(
    ("SP") -> List("1", "3", "9", "10", "11", "13", "17", "21", "22", "23", "24", "27", "29", "30", "90"),
    ("DNRC") -> List("0", "9"),
    ("MG") -> List("6")
  )
}


object RecordFileLengthConfig {
  val fileTypeRecordLength = Map(
    "SP_00" -> 833,
    "DNRC_00" -> 718,
    "MG_00" -> 851,
    "SP_01" -> 835,
    "DNRC_01" -> 820,
    "MG_01" -> 853,
    "SP_02" -> 673,
    "DNRC_02" -> 648,
    "MG_02" -> 1733,
    "SP_03" -> 721,
    "DNRC_03" -> 696,
    "MG_03" -> 1395,
    "SP_04" -> 654,
    "DNRC_04" -> 629,
    "MG_04" -> 1714,
    "SP_05" -> 639,
    "DNRC_05" -> 593,
    "MG_05" -> 861,
    "SP_06" -> 43,
    "DNRC_06" -> 43,
    "MG_06" -> 861,
    "SP_07" -> 209,
    "DNRC_07" -> 209,
    "MG_07" -> 209,
    "SP_10" -> 204,
    "DNRC_10" -> 204,
    "MG_10" -> 204,
    "SP_11" -> 207,
    "DNRC_11" -> 207,
    "MG_11" -> 207,
    "SP_13" -> 205,
    "DNRC_13" -> 205,
    "MG_13" -> 205,
    "DNRC_14" -> 204,
    "MG_14" -> 205,
    "SP_15" -> 721,
    "DNRC_15" -> 696,
    "MG_15" -> 1714,
    "SP_17" -> 601,
    "SP_18" -> 413,
    "SP_98" -> 835,
    "DNRC_98" -> 820,
    "MG_98" -> 720,
    "SP_99" -> 835,
    "DNRC_99" -> 820,
    "MG_99" -> 720

  )


  val originalRecordLength = Map(

    "SP_04" -> 548,
    "DNRC_04" -> 523,
    "MG_04" -> 853,
    "SP_15" -> 610,
    "DNRC_15" -> 585,
    "MG_15" -> 853,
    "SP_02" -> 99,
    "DNRC_02" -> 99,
    "MG_02" -> 854,
    "SP_03" -> 104,
    "DNRC_03" -> 104,
    "MG_03" -> 854

  )
}


object ConsistecnyClass {
  val R05_NIRE_DA_MATRIZ_CONSISTENCYCHECK = "R05NireDaMatrizConsistencyCheck"
  val R02_VALOR_DO_CAPITAL_SOCIAL_CONSISTENCY_CHECK = "R02ValorDoCapitalSocialConsistencyCheck"
  val R03_VALOR_DO_CAPITAL_SOCIAL_CONSISTENCY_CHECK = "R03ValorDoCapitalSocialConsistencyCheck"
  val R18_AND_R01_NIRE_CONSISTENCY_CHECK = "R18AndR01NireConsistencyCheck"
}

object Scenario {
  val MATCHES = "matches"
  val NOT_MATCHES = "notMatches"
  val MATCHES_100PERCENT_AND_COUNT_ONE = "matches100andCountOne"
  val NOT_MATCHES_100PERCENT_AND_COUNT_NOT_ONE = "notMatches100andCountNotOne"
  val MATCHES_85PERCENT_AND_COUNT_ONE = "matches85andCountOne"
  val NOT_MATCHES_85PERCENT_AND_COUNT_NOT_ONE = "notMatches85andCountNotOne"
  val LESS_THAN_85PERCENT = "lessThan85PercentMatch"
  val COMPLETE_FILE = "completeFile"
  val INCREMENTAL_FILE = "incrementalFile"
  val FOOTER_CHECK = "FooterCheck"
  val R03_CNPJ_MATCHES_R15_CNPJ = "R03_cnpj_matches_R15_cnpj"
  val R03_NIRE_DA_MATRIZ_MATCHES_R15_NIRE_DA_MATRIZ_AND_UNIQUE = "R03_nire_da_matriz_matches_R15_nire_da_matriz"
  val R03_NIRE_DA_MATRIZ_MATCHES_R15_NIRE_DA_MATRIZ_DUPLICATE = "R03_nire_da_matriz_matches_R15_nire_da_matriz_duplicate"
  val R03_NIRE_DA_MATRIZ_MATCHES_R15_NIRE_DA_MATRIZ_DUPLICATE_DIFFERENT_R15 = "R03_nire_da_matriz_matches_R15_nire_da_matriz_duplicate_different_R15"
  val R03_RECORD_R15_RECORD_NOT_JOIN = "R03_record_R15_record_cannot_join"
  val R03_NIRE_DA_MATRIZ_NOT_MATCHES_R15_NIRE_DA_MATRIZ = "R03_nire_da_matriz_not_matches_R15_nire_da_matriz"
  val R15_RECORD_UNIQUE_FOR_R03_CNPJ_DO_SOCIO = "R15_record_unique_for_R03_cnpj_do_socio"
  val R15_RECORD_DUPLICATE = "R15_record_duplicate"
  val R15_RECORD_IS_NOT_DUPLICATE = "R15_record_is_not_duplicate"
  val R03_NIRE_DA_MATRIZ_NOT_VALID = "R03_nire_da_matriz_not_valid"
  val R03_CNPJ_NOT_MATCHES_R15_CNPJ = "R03_cnpj_not_matches_R15_cnpj"
  val R03_NIRE_DA_SOCIO = "R03_nire_da_socio"
  val R03_NIRE_DA_SOCIO_MATCHES_R15_NIRE_DA_SOCIO = "R03_nire_da_socio_matches_R15_nire_da_socio"
  val R03_NIRE_DA_SOCIO_NOT_MATCHES_R15_NIRE_DA_SOCIO = "R03_nire_da_socio_not_matches_R15_nire_da_socio"

  val IS_EMPTY = "isEmpty"
  val IS_NOT_EMPTY = "isNotEmpty"
  val NIRE_NOT_PRESENT = "nireNotPresent"
  val CNPJ_PRESENT = "cnpjPresent"
  val CNPJ_NOT_PRESENT = "cnpjNotPresent"
  val VALID_SCENARIO_FOR_SP = "validScenarioForSP"
  val R02_AND_R01_NIRE_CONSISTENCY_CHECK = "R02AndR01NireConsistencyCheck"
  val VALID_SCENARIO_FOR_MG_AND_DNRC = "validScenarioForMGAndDNRC"
  val INVALID_SCENARIO_FOR_MG_AND_DNRC = "inValidScenarioForMGAndDNRC"
  val INVALID_SCENARIO_FOR_SP = "inValidScenarioForSP"
  val IS_100PERCENT_MATCH = "is100PercentMatch"
  val IS_85PERCENT_MATCH = "is85PercentMatch"
  val MATCH_NOT_FOUND = "matchNotFound"
  val MATCH_FOUND = "matchFound"
  val  OXYGEN_STORE_MATCH_NOT_FOUND = "oxygenStorMatchNotFound"
  val OXYGEN_STORE_MATCH_FOUND = "oxygenStorMatchFound"
  val R02_AND_R0402_CONSISTENCY_CHECK = "R02AndR0402ConsistencyCheck"
  val R02_PRESENT_AND_HAS_VALID_CPF = "r02PresentAndHasValidCpf"
  val RF_CONTAINS_CPF = "RFContainsCpf"
  val RF_DOES_NOT_CONTAINS_CPF = "RFDoesnotContainsCpf"
  val R02_AND_R0402_CONSISTENCY_CHECK_MG_DNRC_1 = "R02AndR0402ConsistencyCheck_MG_DNRC_1"
  val IS_UNIQUE_R04_RECORD_1 = "isUniqueR04Record1"
  val DATA_IS_SAME_IN_R04_RECORD_1 = "dataIsSameInR04Record1"
  val DATA_IS_NOT_SAME_IN_R04_RECORD_1 = "dataIsNotSameInR04Record1"
  val R01_PARTNER_CONSISTENCY_CHECK = "R01PartnerConsistencyCheck"
  val MEI_HAS_MORE_THAN_ONE_PERSON_DATA = "MEIHasMoreThanOnePersonData"
  val ENTREPRENEUR_HAS_MORE_THAN_ONE_PERSON_DATA = "EntrepreneurHasMoreThanOnePersonData"
  val EIRELI_HAS_MORE_THAN_ONE_PERSON_DATA = "EireliHasMoreThanOnePersonData"
  val R03_AND_R15_JOIN_ON_CNPJ_CONSISTENCY_CHECK = "R03AndR15JoinOnCnpjConsistencyCheck"
  val R15_AND_R03_CNPJ_DO_SOCIO_ARE_EQUAL = "R15AndR03CnpjDoSocioAreEqual"
  val R15_AND_R03_CODIGO_DO_SOCIO_ARE_EQUAL_AND_UNIQUE = "R15AndR03CodigoDoSocioAreEqualAndUnique"
  val R15_AND_R03_CODIGO_DO_SOCIO_ARE_EQUAL_MULTIPLE_SAME_DATA = "R15AndR03CodigoDoSocioAreEqualMultipleSameData"
  val R15_AND_R03_CODIGO_DO_SOCIO_ARE_EQUAL_MULTIPLE_DIFFERENT_DATA = "R15AndR03CodigoDoSocioAreEqualMultipleDifferentData"
  val R15_AND_R03_CODIGO_DO_SOCIO_NOT_EQUAL = "R15AndR03CodigodoSocioNotEqual"
  val R15_AND_R03_CNPJ_DO_SOCIO_ARE_EQUAL_AND_UNIQUE = "R15AndR03CNPJdoSocioAreEqualAndUnique"
  val R15_AND_R03_CNPJ_DO_SOCIO_ARE_EQUAL_MULTIPLE_SAME_DATA = "R15AndR03CNPJdoSocioAreEqualMultipleSameData"
  val R15_AND_R03_CNPJ_DO_SOCIO_ARE_EQUAL_MULTIPLE_DIFFERENT_DATA = "R15AndR03CNPJdoSocioAreEqualMultipleDifferentData"
  val R03_CODIGO_DO_SOCIO_NOT_VALID = "R03_CodigodoSocioNotValid"
  val R03_CNPJ_DO_SOCIO_NOT_EQUAL = "R03_CnpjdoSocioNotEqual"
  val R03_AND_R15_NIRE_DO_SOCIO_EQUAL = "R03AndR15_NIREdoSocioEqual"
  val R15_AND_R03_NIRE_DO_SOCIO_EQUAL_AND_UNIQUE = "R15AndR03_NIREdoSocioEqualAndUnique"
  val R15_AND_R03_NIRE_DO_SOCIO_ARE_EQUAL_MULTIPLE_SAME_DATA = "R15AndR03_NIREdoSocioAreEqualMultipleSameData"
  val R15_AND_R03_NIRE_DO_SOCIO_ARE_EQUAL_MULTIPLE_DIFFERENT_DATA = "R15AndR03_NIREdoSocioAreEqualMultipleDifferentData"
  val R15_AND_R03_CODIGO_DO_SOCIO_NOT_EQUAL_RFPESJUR = "R15AndR03_CodigoDoSocioNotEqualRfpesjur"
  val R02_AND_R0402_CONSISTENCY_CHECK_SP = "R02AndR0402ConsistencyCheck_SP"


  val IS_INVALID_DATA_DA_VINCULACAO = "isInValidDataDaVinculacao"
  val DATA_DO_DESLIGAMENTO_IS_LESSER_THAN_DATA_DA_VINCULACAO = "DataDoDesligamentoIsLesserThanDataDaVinculacao"
  val R02_DATA_DA_VINCULACAO_CONSISTENCY_CHECK = "R02DataDaVinculacaoConsistencyCheck"
  val R02_DATA_DO_DESLIGAMENTO_CONSISTENCY_CHECK = "R02DataDoDesligamentoConsistencyCheck"
  val R03_DATA_DA_VINCULACAO_CONSISTENCY_CHECK = "R03DataDaVinculacaoConsistencyCheck"
  val R03_DATA_DO_DESLIGAMENTO_CONSISTENCY_CHECK = "R03DataDoDesligamentoConsistencyCheck"
  val R17_CNPJ_CONSISTENCY_CHECK = "R17_CNPJConsistencyCheck"
  val R00_HISTORICAL_CHECK = "R00HistoricalCheck"
  val IS_VALID_NIRE_AND_MATCHES = "isValidNireAndMatchesOxygenStore"
  val IS_VALID_NIRE_AND_NOT_MATCHES = "isValidNireAndNotMatchesOxygenStore"
  val IS_VALID_NIRE_MATCHES_MATCHES = "isValidNireMatches"
  val IS_VALID_R05NIRE_NOT_VALID_R01NIRE_NOT_MATCHES = "isValidR05NireNotValidR01NireNotMatches"
  val IS_INVALID_R05R01NIRE = "isInValidR05R01Nire"
  val IS_INAVLID_R05NIRE_VALID_R01NIRE_NOT_MATCHES = "isInValidR05NireValidR01NireNotMatches"
  val IS_VALID_NIRE = "isValidNire"
  val IS_NOT_VALID_NIRE_OR_NOT_MATCH = "isInValidNireOrNotMatch"
  val IS_VALID_NIRE_AND_MATCH = "isValidNireAndMatch"
  val NOT_PRESENT = "notPresent"

  val IS_INVALID_NIRE = "isInValidNire"
  val IS_VALID_LENGTH_R04_NIRE = "isValidLength_R04_Nire"
  val IS_INVALID_LENGTH_R04_NIRE = "isInValidLength_R04_Nire"
  val UNIQUE_CPF_R04 = "uniqueCpfR04"
  val ONLY_1_CPF_R04 = "only1CpfR04"
  val MORE_THAN_1_CPF_R04 = "moreThan1CpfR04"
  val R02_CPF_NOT_PRESENT = "R02CpfNotPresent"

  val IS_PRESENT_IN_RECEITA_FEDERAL = "isPresentInReceitaFederal"
  val R02_AND_R0402_CONSISTENCY_CHECK_1 = "R02AndR0402ConsistencyCheck_1"
  val R02_AND_R0402_CONSISTENCY_CHECK_MG_DNRC = "R02AndR0402ConsistencyCheck_MG_DNRC"
  val IS_UNIQUE_R04_RECORD = "isUniqueR04Record"
  val DATA_IS_SAME_IN_R04_RECORDS = "dataIsSameInR04Records"
  val DATA_IS_NOT_SAME_IN_R04_RECORDS = "dataIsNotSameInR04Records"

  val R02_DATA_DA_ULTIMO_CONTRATUAL_CONSISTENCY_CHECK = "R01DataDaUltimoContratualConsistencyCheck"
  val IS_GREATER = "isGreater"
  val IS_LESSER = "isLesser"
  val IS_EQUALS = "isEquals"
  val IS_NOT_EQUALS = "isNotEquals"
  val NOT_PRESENT_IN_OXYGEN_STORE = "notPresentInOxygenStore"

  val PARTNER_SHARE_EQ_100 = "equalTo100"
  val PARTNER_SHARE_LT_100 = "lessThan100"
  val PARTNER_SHARE_GT_100 = "greaterThan100"

  val ENV_BRAZIL_PATH = "env-brazil.conf"
  val OXYGEN = "oxygen"
  val APPLICATION = "application"
  val PATHS = "paths"
  val USER_HOME = "userHome"
  val AVRO_READER_APPLICATION_CONF = "Avro_Reader_Application.conf"
  val AVRO_READER = "avroReader"
  val SPARK_MASTER = "master"
  val APPNAME = "Avro_Reader"
}

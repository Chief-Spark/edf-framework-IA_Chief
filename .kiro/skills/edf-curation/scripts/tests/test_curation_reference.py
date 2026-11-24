"""Reglas de transformación (implementación de referencia = espejo de Capa A). Datos sintéticos.

Traduce los casos de la prueba Java ReferenciaTest (deprecada, scripts/DEPRECADOS.md) y agrega los de la
1.7.0: concatenacion y la publicación estandarizada de los campos con severidad 3.
"""

import copy
import json

import pytest

import curation_reference as ref

ARCHIVO = "SISBEN_EXPERIAN_202401.20240205103000-0500.txt"


@pytest.fixture
def adv(spec):
    return spec["parametros"]["campo_advertencias"]


def test_nombre_mayusculas_sin_tildes_ni_especiales():
    assert ref.nombre(" José-María ", False) == ("JOSE MARIA", None)
    assert ref.nombre("Pruebañez", False) == ("PRUEBANEZ", None)
    assert ref.nombre("Pruebañez", True) == ("PRUEBAÑEZ", None)
    assert ref.nombre("   ", False) == (None, None)
    assert ref.nombre("123", False) == (None, ref.NO_NORMALIZABLE)


def test_padding_dane():
    assert ref.padding_izquierda("5", 2) == ("05", None)
    assert ref.padding_izquierda("5001", 5) == ("05001", None)
    assert ref.padding_izquierda("A1", 2) == (None, ref.FORMATO_INVALIDO)
    assert ref.padding_izquierda("123", 2) == (None, ref.FORMATO_INVALIDO)
    assert ref.padding_izquierda(None, 2) == (None, None)


def test_fechas_epoch_ms_estrictas():
    fmts = ["dd/MM/uuuu", "uuuu-MM-dd HH:mm:ss"]
    assert ref.fecha_epoch("15/01/2024", fmts, "UTC") == (1705276800000, None)
    assert ref.fecha_epoch("2024-01-15 10:30:00", fmts, "UTC") == (1705314600000, None)
    assert ref.fecha_epoch("31/02/2024", fmts, "UTC") == (None, ref.FORMATO_INVALIDO)
    assert ref.fecha_epoch("1/1/2024", fmts, "UTC") == (None, ref.FORMATO_INVALIDO)
    assert ref.fecha_epoch("15/01/0000", fmts, "UTC") == (None, ref.FORMATO_INVALIDO)
    assert ref.fecha_epoch(None, fmts, "UTC") == (None, None)
    # Bogotá = UTC-5
    assert ref.fecha_epoch("15/01/2024", fmts, "America/Bogota") == (1705276800000 + 5 * 3600 * 1000, None)
    with pytest.raises(ValueError):
        ref.fecha_epoch("15/01/2024", ["uuuu/MM/dd"], "UTC")


def test_fecha_iso_solo_fecha():
    """F-FW-10: ISO solo fecha (uuuu-MM-dd): 00:00 en la zona, estricto, convive con los otros formatos."""
    iso = ["uuuu-MM-dd"]
    assert ref.fecha_epoch("2024-01-15", iso, "UTC") == (1705276800000, None)
    assert ref.fecha_epoch("2024-01-15", iso, "America/Bogota") == (1705276800000 + 5 * 3600 * 1000, None)
    for malo in ("2024-02-30", "2024-1-5", "0000-01-15", "15/01/2024", "2024-01-15 10:30:00"):
        assert ref.fecha_epoch(malo, iso, "UTC") == (None, ref.FORMATO_INVALIDO), malo
    todos = ["dd/MM/uuuu", "uuuu-MM-dd HH:mm:ss", "uuuu-MM-dd"]
    assert ref.fecha_epoch("2024-01-15", todos, "UTC") == (1705276800000, None)
    assert ref.fecha_epoch("15/01/2024", todos, "UTC") == (1705276800000, None)
    assert ref.fecha_epoch("2024-01-15 10:30:00", todos, "UTC") == (1705314600000, None)


def test_homologacion_tip_documento_completa(spec):
    hom = spec["homologacion_tipo_documento"]
    tabla = {str(k): str(v) for k, v in hom["tabla"].items()}
    pend = {str(p) for p in hom["pendientes"]}
    esperado = {"1": "10", "2": "7", "3": "1", "4": "4", "5": "8", "6": "5", "8": "9", "PPT": "11"}
    for sisben, experian in esperado.items():
        assert ref.homologar(sisben, tabla, pend) == (experian, None)
    assert ref.homologar("ppt", tabla, pend) == ("11", None)
    assert ref.homologar("7", tabla, pend) == (None, ref.HOMOLOGACION_PENDIENTE)
    assert ref.homologar("99", tabla, pend) == (None, ref.NO_HOMOLOGABLE)
    assert ref.homologar(None, tabla, pend) == (None, None)


def test_documento_reglas_por_tipo(spec):
    rd = spec["reglas_documento"]
    por_tipo = rd["por_tipo"]
    assert ref.documento(" 000 9990001 ", rd["por_defecto"]) == ("9990001", None)
    assert ref.documento("PE0999A0003", por_tipo["8"]) == ("9990003", None)
    assert ref.documento("0099900002", por_tipo["PPT"]) == ("0099900002", None)
    assert ref.documento("AB0999006", por_tipo["6"]) == ("AB0999006", None)
    assert ref.documento("000", rd["por_defecto"]) == (None, ref.NO_NORMALIZABLE)
    assert ref.documento("42", {"padding": 5}) == ("00042", None)
    assert ref.documento(None, {}) == (None, None)


def test_documento_letras_en_mayusculas(spec):
    """F-FW-14: las letras del número salen en mayúsculas; conservar_original devuelve el valor tal como vino."""
    defecto = spec["reglas_documento"]["por_defecto"]
    assert ref.documento(" ab 0012c ", defecto) == ("AB0012C", None)
    assert ref.documento("0ab12", defecto) == ("AB12", None)
    assert ref.documento("pe0999a0003", {"solo_digitos": True, "quitar_ceros_izquierda": True}) == ("9990003", None)
    assert ref.documento("0ab12", {"conservar_original": True}) == ("0ab12", None)
    assert ref.documento("ab", {"solo_digitos": True}) == (None, ref.NO_NORMALIZABLE)


def test_subscriber_relation_une_campos_o_conserva_el_aceptado(spec):
    """F-FW-11 (D14): campos curados unidos; sin extensión o con un componente faltante, el aceptado."""
    con_ext = copy.deepcopy(spec)
    con_ext["extension_subscriber_relation"] = {"campos": ["tipoDocumento", "numeroDocumento"], "separador": "_"}
    completo = ref.curar('{"TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "0009990001"}', ARCHIVO, [], con_ext)
    assert ref.subscriber_relation(completo, con_ext, "COPIADO") == "1_9990001"
    assert ref.subscriber_relation(completo, spec, "COPIADO") == "COPIADO"  # sin extensión: se copia
    sin_numero = ref.curar('{"TIP_DOCUMENTO": "3"}', ARCHIVO, [], con_ext)
    assert ref.subscriber_relation(sin_numero, con_ext, "COPIADO") == "COPIADO"
    desconocido = ref.curar('{"TIP_DOCUMENTO": "99", "NUM_DOCUMENTO": "1"}', ARCHIVO, [], con_ext)
    assert ref.subscriber_relation(desconocido, con_ext, "COPIADO") == "COPIADO"
    assert ref.subscriber_relation({}, con_ext, "COPIADO") == "COPIADO"

    evento = {"eventId": "evt-x", "lineage": {"fileName": ARCHIVO},
              "validatedRecordJson": '{"TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "0009990001"}',
              "subscriberRelation": "0009990001"}
    assert ref.curar_evento(evento, con_ext)["subscriberRelation"] == "1_9990001"
    assert ref.curar_evento(evento, spec)["subscriberRelation"] == "0009990001"


def test_subscriber_relation_componente_faltante_advierte(spec, adv):
    """AUD-C3 (D14): con la extensión y un componente faltante, se conserva el aceptado (nunca "") y el registro
    lleva subscriberRelation:COMPONENTE_FALTANTE; sin la extensión, nunca."""
    falta = "subscriberRelation:COMPONENTE_FALTANTE"
    con_ext = copy.deepcopy(spec)
    con_ext["extension_subscriber_relation"] = {"campos": ["tipoDocumento", "numeroDocumento"], "separador": "_"}
    completo = ref.curar('{"TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "1"}', ARCHIVO, [], con_ext)
    assert falta not in completo[adv]
    for reg in ('{"TIP_DOCUMENTO": "3"}', '{"TIP_DOCUMENTO": "99", "NUM_DOCUMENTO": "1"}', "{no es json"):
        out = ref.curar(reg, ARCHIVO, [], con_ext)
        assert out[adv][-1] == falta, reg
        assert ref.subscriber_relation(out, con_ext, "ACEPTADO") == "ACEPTADO"
    # componente omitido por severidad 3 (la clave no está): también falta
    con_ext["extension_subscriber_relation"]["campos"] = ["tipoDocumento", "segundoApellido"]
    out = ref.curar('{"TIP_DOCUMENTO": "3", "SEG_APELLIDO": "X"}', ARCHIVO,
                    [{"codigo": "SISBEN_INC_011", "nivel": "3"}], con_ext)
    assert "segundoApellido" not in out and out[adv][-1] == falta
    assert falta not in ref.curar('{"TIP_DOCUMENTO": "3"}', ARCHIVO, [], spec)[adv]
    # el componente se compara como texto de Scala (true, no True)
    assert ref._partes_relacion({"x": True, "y": 1.5}, {"campos": ["x", "y"]}) == ["true", "1.5"]


def _spec_con_identidad(spec, **param):
    spec["campos_curados"] += [
        {"destino": "standardizedIdType", "origen": "", "tipo_salida": "entero", "estado": "cerrado",
         "transformacion": {"tipo": "copia_de", "campo": "tipoDocumento"}},
        {"destino": "standardizedIdNumber", "origen": "", "tipo_salida": "texto", "estado": "cerrado",
         "transformacion": {"tipo": "copia_de", "campo": "numeroDocumento"}},
    ]
    spec["parametros"].update(param)
    return spec


def test_claves_de_identidad_nulas_se_omiten(spec, adv):
    """AUD-C4: por defecto, standardizedId* nulos salen ausentes (nunca null); los demás nulos siguen como null."""
    _spec_con_identidad(spec)
    assert ref.omitir_si_nulas(spec) == ["standardizedIdType", "standardizedIdNumber"]
    out = ref.curar('{"TIP_DOCUMENTO": "99"}', ARCHIVO, [], spec)
    assert "standardizedIdType" not in out and "standardizedIdNumber" not in out
    assert "tipoDocumento" in out and out["tipoDocumento"] is None
    out = ref.curar('{"TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "007"}', ARCHIVO, [], spec)
    assert out["standardizedIdType"] == 1 and out["standardizedIdNumber"] == "7"
    # declarado en la spec: manda la lista (vacía = nada se omite)
    spec["parametros"]["omitir_si_nulas"] = []
    out = ref.curar('{"TIP_DOCUMENTO": "99"}', ARCHIVO, [], spec)
    assert out["standardizedIdType"] is None
    spec["parametros"]["omitir_si_nulas"] = ["numeroDocumento"]
    out = ref.curar("{}", ARCHIVO, [], spec)
    assert "numeroDocumento" not in out and out["standardizedIdNumber"] is None


def test_ejemplo_sisben_no_tiene_claves_de_identidad(spec):
    """AUD-C4: el ejemplo no tiene standardizedId*: su salida no cambia."""
    assert ref.omitir_si_nulas(spec) == []


def test_convertir_tipos():
    dos31 = 2**31
    assert ref.convertir("12", "entero") == (12, None)
    assert ref.convertir("X1", "entero") == (None, ref.FORMATO_INVALIDO)
    assert ref.convertir(str(dos31), "entero") == (None, ref.FORMATO_INVALIDO)
    assert ref.convertir(str(dos31), "largo") == (dos31, None)
    assert ref.convertir("1.5", "decimal") == (1.5, None)
    assert ref.convertir("1,5", "decimal") == (None, ref.FORMATO_INVALIDO)
    assert ref.convertir("TRUE", "booleano") == (True, None)
    assert ref.convertir("si", "booleano") == (None, ref.FORMATO_INVALIDO)
    assert ref.convertir(5, "texto") == ("5", None)
    assert ref.convertir(7, "largo") == (7, None)
    assert ref.convertir("x", "otro") == (None, ref.FORMATO_INVALIDO)


def test_info_archivo(spec):
    assert ref.info_archivo("RT_SISBEN_EXPERIAN_202402.20240306080000-0500.txt", spec) == ("RT", 1709164800000)
    assert ref.info_archivo("ruta/EP_SISBEN_EXPERIAN_202401.20240206080000-0500", spec)[0] == "EP"
    assert ref.info_archivo(ARCHIVO, spec)[0] == "REGULAR"
    assert ref.info_archivo("SISBEN_EXPERIAN_202413.20240205103000-0500.txt", spec) == ("REGULAR", None)
    assert ref.info_archivo("SISBEN_EXPERIAN_000001.20240205103000-0500.txt", spec) == ("REGULAR", None)
    assert ref.info_archivo("OTRA.txt", spec) == (None, None)


def test_info_archivo_regex_sin_dos_grupos_no_lanza(spec):
    """AUD-C2: igual que Scala (guarda de groupCount y Try): sin 2 grupos, (None, None); grupo 2 ausente, sin corte."""
    spec["archivo"]["regex_derivacion"] = r"(RT_)?SISBEN_EXPERIAN_\d{6}"
    assert ref.info_archivo("RT_SISBEN_EXPERIAN_202402.txt", spec) == (None, None)
    spec["archivo"]["regex_derivacion"] = r"(RT_)?SISBEN_EXPERIAN_(\d{6})?"
    assert ref.info_archivo("RT_SISBEN_EXPERIAN_X", spec) == ("RT", None)


def test_file_pattern(spec):
    for ej in spec["archivo"]["ejemplos_sinteticos"]:
        assert ref.coincide_file_pattern(ej, spec)
    for ej in spec["archivo"]["contraejemplos_sinteticos"]:
        assert not ref.coincide_file_pattern(ej, spec)


def test_curar_nunca_lanza_y_advierte(spec, adv):
    out = ref.curar("{no es json", ARCHIVO, [], spec)
    assert "registro:REGISTRO_ILEGIBLE" in out[adv]
    assert out["tipoArchivo"] == "REGULAR"
    out = ref.curar(None, None, None, spec)
    assert "tipoArchivo:ARCHIVO_NO_RECONOCIDO" in out[adv]
    assert "registro:REGISTRO_ILEGIBLE" in ref.curar("[1, 2]", ARCHIVO, None, spec)[adv]
    assert "registro:REGISTRO_ILEGIBLE" in ref.curar("{} extra", ARCHIVO, None, spec)[adv]


def test_severidad3_omite_y_0_anula(spec, adv):
    reg = '{"SEG_APELLIDO": "Ficticio", "SEG_NOMBRE": "Prueba", "TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "1"}'
    errores = [{"codigo": "SISBEN_INC_011", "nivel": "3"}, {"codigo": "SISBEN_INC_013", "nivel": "0"}]
    out = ref.curar(reg, ARCHIVO, errores, spec)
    assert "segundoApellido" not in out
    assert out["segundoNombre"] is None
    assert not any(a.startswith("segundo") for a in out[adv])
    # Forma del evento del motor (errorCodeValue / errorLevel) y código desconocido.
    out = ref.curar(reg, ARCHIVO, [{"errorCodeValue": "SISBEN_INC_011", "errorLevel": "3"},
                                   {"codigo": "NO_EXISTE", "nivel": "3"}], spec)
    assert "segundoApellido" not in out


def test_severidad3_publica_estandarizado_si_no_esta_en_las_listas(spec, adv):
    """1.7.0, opción B de SISBEN (PA-003): con niveles_no_almacenar vacío, el campo con severidad 3 se publica
    estandarizado; si no se puede transformar, nulo con advertencia."""
    spec["parametros"]["niveles_no_almacenar"] = []
    reg = ('{"SEG_APELLIDO": "Ficticío-Ñ", "ID_MARCA": "N", "TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "1", '
           '"FECHA_DIGITACION": "31/02/2024"}')
    errores = [{"codigo": "SISBEN_INC_011", "nivel": "3"}]
    out = ref.curar(reg, ARCHIVO, errores, spec)
    assert out["segundoApellido"] == "FICTICIO N"
    assert out["idMarca"] is None and "idMarca:FORMATO_INVALIDO" in out[adv]
    assert out["fechaDigitacion"] is None and "fechaDigitacion:FORMATO_INVALIDO" in out[adv]
    destinos = [m["destino"] for m in spec["campos_curados"]]
    assert all(d in out for d in destinos), "con la opción B ninguna clave se omite"


def test_concatenar():
    assert ref.concatenar(["ANA", None, "PRUEBA", " "], " ") == ("ANA PRUEBA", None)
    assert ref.concatenar([1, "X", True], "_") == ("1_X_true", None)
    assert ref.concatenar([None, ""], " ") == (None, None)
    assert ref.concatenar([], " ") == (None, None)


def _spec_con_nombre_completo(spec):
    spec["campos_curados"].append({
        "destino": "standardizedIdName", "origen": "", "tipo_salida": "texto", "estado": "cerrado",
        "transformacion": {"tipo": "concatenacion", "separador": " ",
                           "campos": ["primerNombre", "segundoNombre", "primerApellido", "segundoApellido"]}})
    return spec


def test_curar_concatenacion_usa_los_valores_estandarizados(spec, adv):
    _spec_con_nombre_completo(spec)
    reg = ('{"PRI_NOMBRE": " maría ", "SEG_NOMBRE": "", "PRI_APELLIDO": "Pérez-Prueba", "SEG_APELLIDO": "Ñandú",'
           ' "TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "1"}')
    out = ref.curar(reg, ARCHIVO, [], spec)
    assert out["standardizedIdName"] == "MARIA PEREZ PRUEBA NANDU"
    assert not any(a.startswith("standardizedIdName") for a in out[adv])
    # Componente omitido por severidad 3 (política por defecto del ejemplo): no aparece.
    out = ref.curar(reg, ARCHIVO, [{"codigo": "SISBEN_INC_011", "nivel": "3"}], spec)
    assert "segundoApellido" not in out
    assert out["standardizedIdName"] == "MARIA PEREZ PRUEBA"
    # Todos vacíos: nulo sin advertencia propia (los nombres ya advierten). Clave de identidad nula: ausente (AUD-C4).
    out = ref.curar('{"PRI_NOMBRE": "123"}', ARCHIVO, [], spec)
    assert "standardizedIdName" not in out
    assert "primerNombre:NO_NORMALIZABLE" in out[adv]
    assert not any(a.startswith("standardizedIdName") for a in out[adv])


def test_transformacion_desconocida_advierte(spec, adv):
    spec["campos_curados"][0]["transformacion"] = {"tipo": "otra"}
    out = ref.curar("{}", ARCHIVO, [], spec)
    assert out[spec["campos_curados"][0]["destino"]] is None
    assert f"{spec['campos_curados'][0]['destino']}:FORMATO_INVALIDO" in out[adv]


def test_advertencias_sin_valores_de_dato(spec, adv):
    reg = '{"ID_HOGAR": "VALOR-SECRETO", "TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "9990001"}'
    texto = json.dumps(ref.curar(reg, ARCHIVO, [], spec)[adv], ensure_ascii=False)
    assert "VALOR-SECRETO" not in texto and "9990001" not in texto


def test_numeros_json_sin_decimal(spec):
    out = ref.curar('{"ID_HOGAR": 2.0, "TIP_DOCUMENTO": 3}', ARCHIVO, [], spec)
    assert out["idHogar"] == 2
    assert out["tipoDocumento"] == 1


def test_valores_json_no_texto_como_gson():
    """AUD-C5: mismo texto que el lector Scala con Gson (comoTexto): booleanos en minúsculas, arreglos y objetos
    como JSON compacto con los números tal como vinieron, números de primer nivel sin ceros finales."""
    reg = ref._parse_registro('{" a ": true, "b": [1, "x", 1.50, null], "c": {"k": 1.5e3, "t": "q\\"z\\n"}, '
                              '"d": null, "h": false, "u": ["ñ", "\\u2028"]}')
    assert reg == {"A": "true", "B": '[1,"x",1.50,null]', "C": '{"k":1.5e3,"t":"q\\"z\\n"}', "H": "false",
                   "U": '["ñ","\\u2028"]'}
    assert ref._parse_registro(None) is None


def test_numeros_json_como_big_decimal_plano():
    """AUD-C5: getAsBigDecimal.stripTrailingZeros.toPlainString, sin pasar por float."""
    reg = ref._parse_registro('{"a": 2.0, "b": 0.10000000000000001, "c": 100, "d": -0.0, "e": 1.5e3, '
                              '"f": -1.50E-2, "g": 12345678901234567890123456789012.50, "h": 0.00015, "i": NaN}')
    assert reg == {"A": "2", "B": "0.10000000000000001", "C": "100", "D": "0", "E": "1500", "F": "-0.015",
                   "G": "12345678901234567890123456789012.5", "H": "0.00015", "I": "NaN"}
    assert ref._parse_registro('{"a": "x\ty"}') == {"A": "x\ty"}  # control sin escapar: Gson lo acepta


def test_errores_nulos_se_toleran_igual_que_en_scala(spec, adv):
    """AUD-C5: errores=None y un errorCode nulo no lanzan ni cambian el resultado (Scala: Option(errores))."""
    reg = '{"TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "1"}'
    base = ref.curar(reg, ARCHIVO, [], spec)
    assert ref.curar(reg, ARCHIVO, None, spec) == base
    assert ref.curar(reg, ARCHIVO, [None, {}], spec)[adv] == base[adv]
    evento = {"eventId": "e", "lineage": {"fileName": ARCHIVO}, "validatedRecordJson": reg, "errorCodes": [None]}
    assert ref.curar_evento(evento, spec)["curatedRecordJson"] == base
    assert ref.curar_evento({"eventId": "e"}, spec) is None  # sin lineage: fuera del filePattern


def test_nivel_nulo_conserva_el_campo_y_advierte(spec, adv):
    """AUD-C7: un código conocido con nivel nulo no omite ni anula; deja <campo>:NIVEL_DESCONOCIDO."""
    reg = '{"SEG_APELLIDO": "Ficticio", "TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "1"}'
    out = ref.curar(reg, ARCHIVO, [{"codigo": "SISBEN_INC_011", "nivel": None}], spec)
    assert out["segundoApellido"] == "FICTICIO"
    assert out[adv] == ["SEG_APELLIDO:NIVEL_DESCONOCIDO"]
    out = ref.curar(reg, ARCHIVO, [{"errorCodeValue": "SISBEN_INC_011"}], spec)  # sin nivel = nulo
    assert out[adv] == ["SEG_APELLIDO:NIVEL_DESCONOCIDO"]
    out = ref.curar(reg, ARCHIVO, [{"codigo": "NO_EXISTE", "nivel": None}], spec)  # código desconocido: nada
    assert out[adv] == []
    out = ref.curar("{no", ARCHIVO, [{"codigo": "SISBEN_INC_011", "nivel": None}], spec)
    assert out[adv][:2] == ["registro:REGISTRO_ILEGIBLE", "SEG_APELLIDO:NIVEL_DESCONOCIDO"]


def test_curar_evento_fuera_del_file_pattern(spec):
    assert ref.curar_evento({"lineage": {"fileName": "OTRA.txt"}}, spec) is None


def test_zonas():
    assert ref.zona_normalizada("Etc/UTC") == ref.zona_normalizada("utc") == "UTC"
    assert ref.zona_normalizada("America/Bogota") == "America/Bogota"
    with pytest.raises(ValueError):
        ref.zona("Marte/Olimpo")

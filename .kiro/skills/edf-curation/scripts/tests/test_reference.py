"""DEPRECADO (2026-10-20): prueban curation_reference.py, la versión Python deprecada.

Las pruebas vigentes están en scripts/java/src/test (ReferenciaTest). Ver scripts/DEPRECADOS.md.
Reglas de transformación (implementación de referencia = espejo de Capa A). Datos sintéticos."""

import json

import curation_reference as ref


def test_nombre_mayusculas_sin_tildes_ni_especiales():
    assert ref.nombre(" José-María ") == ("JOSE MARIA", None)
    assert ref.nombre("Pruebañez") == ("PRUEBANEZ", None)
    assert ref.nombre("Pruebañez", conservar_enie=True) == ("PRUEBAÑEZ", None)
    assert ref.nombre("   ") == (None, None)
    assert ref.nombre("123") == (None, ref.NO_NORMALIZABLE)


def test_padding_dane():
    assert ref.padding_izquierda("5", 2) == ("05", None)
    assert ref.padding_izquierda("5001", 5) == ("05001", None)
    assert ref.padding_izquierda("A1", 2) == (None, ref.FORMATO_INVALIDO)
    assert ref.padding_izquierda("123", 2) == (None, ref.FORMATO_INVALIDO)


def test_fechas_epoch_ms_estrictas():
    fmts = ["dd/MM/uuuu", "uuuu-MM-dd HH:mm:ss"]
    assert ref.fecha_epoch("15/01/2024", fmts, "UTC") == (1705276800000, None)
    assert ref.fecha_epoch("2024-01-15 10:30:00", fmts, "UTC") == (1705314600000, None)
    assert ref.fecha_epoch("31/02/2024", fmts, "UTC") == (None, ref.FORMATO_INVALIDO)
    assert ref.fecha_epoch("1/1/2024", fmts, "UTC") == (None, ref.FORMATO_INVALIDO)
    assert ref.fecha_epoch(None, fmts, "UTC") == (None, None)
    # Bogotá = UTC-5
    assert ref.fecha_epoch("15/01/2024", fmts, "America/Bogota") == (1705276800000 + 5 * 3600 * 1000, None)


def test_homologacion_tip_documento_completa(spec):
    tabla = spec["homologacion_tipo_documento"]["tabla"]
    pend = set(spec["homologacion_tipo_documento"]["pendientes"])
    esperado = {"1": "10", "2": "7", "3": "1", "4": "4", "5": "8", "6": "5", "8": "9", "PPT": "11"}
    for sisben, experian in esperado.items():
        assert ref.homologar(sisben, tabla, pend) == (experian, None)
    assert ref.homologar("ppt", tabla, pend) == ("11", None)
    assert ref.homologar("7", tabla, pend) == (None, ref.HOMOLOGACION_PENDIENTE)
    assert ref.homologar("99", tabla, pend) == (None, ref.NO_HOMOLOGABLE)


def test_documento_reglas_por_tipo(spec):
    rd = spec["reglas_documento"]
    assert ref.documento(" 000 9990001 ", rd["por_defecto"]) == ("9990001", None)
    assert ref.documento("PE0999A0003", rd["por_tipo"]["8"]) == ("9990003", None)
    assert ref.documento("0099900002", rd["por_tipo"]["PPT"]) == ("0099900002", None)
    assert ref.documento("AB0999006", rd["por_tipo"]["6"]) == ("AB0999006", None)
    assert ref.documento("000", rd["por_defecto"]) == (None, ref.NO_NORMALIZABLE)


def test_convertir_tipos():
    assert ref.convertir("12", "entero") == (12, None)
    assert ref.convertir("X1", "entero") == (None, ref.FORMATO_INVALIDO)
    assert ref.convertir(str(2**31), "entero") == (None, ref.FORMATO_INVALIDO)
    assert ref.convertir(str(2**31), "largo") == (2**31, None)
    assert ref.convertir("1.5", "decimal") == (1.5, None)
    assert ref.convertir("1,5", "decimal") == (None, ref.FORMATO_INVALIDO)
    assert ref.convertir("TRUE", "booleano") == (True, None)


def test_info_archivo(spec):
    assert ref.info_archivo("RT_SISBEN_EXPERIAN_202402.20240306080000-0500.txt", spec) == ("RT", 1709164800000)
    assert ref.info_archivo("ruta/EP_SISBEN_EXPERIAN_202401.20240206080000-0500", spec)[0] == "EP"
    assert ref.info_archivo("SISBEN_EXPERIAN_202401.20240205103000-0500.txt", spec)[0] == "REGULAR"
    assert ref.info_archivo("SISBEN_EXPERIAN_202413.20240205103000-0500.txt", spec) == ("REGULAR", None)
    assert ref.info_archivo("OTRA.txt", spec) == (None, None)


def test_file_pattern(spec):
    for ej in spec["archivo"]["ejemplos_sinteticos"]:
        assert ref.coincide_file_pattern(ej, spec)
    for ej in spec["archivo"]["contraejemplos_sinteticos"]:
        assert not ref.coincide_file_pattern(ej, spec)


def test_curar_nunca_lanza_y_advierte(spec):
    out = ref.curar("{no es json", "SISBEN_EXPERIAN_202401.20240205103000-0500.txt", [], spec)
    adv = out[spec["parametros"]["campo_advertencias"]]
    assert "registro:REGISTRO_ILEGIBLE" in adv
    assert out["tipoArchivo"] == "REGULAR"
    out = ref.curar(None, None, None, spec)
    assert "tipoArchivo:ARCHIVO_NO_RECONOCIDO" in out[spec["parametros"]["campo_advertencias"]]


def test_severidad_3_omite_y_0_anula(spec):
    reg = json.dumps({"SEG_APELLIDO": "Ficticio", "SEG_NOMBRE": "Prueba", "TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "1"})
    out = ref.curar(reg, "SISBEN_EXPERIAN_202401.20240205103000-0500.txt",
                    [{"codigo": "SISBEN_INC_011", "nivel": "3"}, {"codigo": "SISBEN_INC_013", "nivel": "0"}], spec)
    assert "segundoApellido" not in out
    assert out["segundoNombre"] is None
    assert not [a for a in out[spec["parametros"]["campo_advertencias"]] if a.startswith("segundo")]


def test_advertencias_sin_valores_de_dato(spec):
    reg = json.dumps({"ID_HOGAR": "VALOR-SECRETO", "TIP_DOCUMENTO": "3", "NUM_DOCUMENTO": "9990001"})
    out = ref.curar(reg, "SISBEN_EXPERIAN_202401.20240205103000-0500.txt", [], spec)
    texto = json.dumps(out[spec["parametros"]["campo_advertencias"]])
    assert "VALOR-SECRETO" not in texto and "9990001" not in texto


def test_numeros_json_sin_decimal(spec):
    out = ref.curar(json.dumps({"ID_HOGAR": 2.0, "TIP_DOCUMENTO": 3}), "SISBEN_EXPERIAN_202401.20240205103000-0500.txt", [], spec)
    assert out["idHogar"] == 2 and out["tipoDocumento"] == 1

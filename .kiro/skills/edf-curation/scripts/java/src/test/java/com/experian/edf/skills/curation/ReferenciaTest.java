package com.experian.edf.skills.curation;

import static com.experian.edf.skills.curation.Py.list;
import static com.experian.edf.skills.curation.Py.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.experian.edf.skills.curation.Referencia.Resultado;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Reglas de transformación (implementación de referencia = espejo de Capa A). Datos sintéticos. */
class ReferenciaTest {

    static final Path SPEC = RenderTemplates.SKILL_ROOT.resolve("assets/examples/sisben/source-spec.sisben.yaml");
    static final String ARCHIVO = "SISBEN_EXPERIAN_202401.20240205103000-0500.txt";
    static Map<String, Object> spec;
    static String campoAdv;

    @BeforeAll
    static void cargar() {
        spec = RenderTemplates.cargarYaml(SPEC);
        campoAdv = (String) map(spec.get("parametros")).get("campo_advertencias");
    }

    static Resultado r(Object valor, String motivo) {
        return new Resultado(valor, motivo);
    }

    @Test
    void nombreMayusculasSinTildesNiEspeciales() {
        assertEquals(r("JOSE MARIA", null), Referencia.nombre(" José-María ", false));
        assertEquals(r("PRUEBANEZ", null), Referencia.nombre("Pruebañez", false));
        assertEquals(r("PRUEBAÑEZ", null), Referencia.nombre("Pruebañez", true));
        assertEquals(r(null, null), Referencia.nombre("   ", false));
        assertEquals(r(null, Referencia.NO_NORMALIZABLE), Referencia.nombre("123", false));
    }

    @Test
    void paddingDane() {
        assertEquals(r("05", null), Referencia.paddingIzquierda("5", 2));
        assertEquals(r("05001", null), Referencia.paddingIzquierda("5001", 5));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.paddingIzquierda("A1", 2));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.paddingIzquierda("123", 2));
        assertEquals(r(null, null), Referencia.paddingIzquierda(null, 2));
    }

    @Test
    void fechasEpochMsEstrictas() {
        List<Object> fmts = List.of("dd/MM/uuuu", "uuuu-MM-dd HH:mm:ss");
        assertEquals(r(1705276800000L, null), Referencia.fechaEpoch("15/01/2024", fmts, "UTC"));
        assertEquals(r(1705314600000L, null), Referencia.fechaEpoch("2024-01-15 10:30:00", fmts, "UTC"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.fechaEpoch("31/02/2024", fmts, "UTC"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.fechaEpoch("1/1/2024", fmts, "UTC"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.fechaEpoch("15/01/0000", fmts, "UTC"));
        assertEquals(r(null, null), Referencia.fechaEpoch(null, fmts, "UTC"));
        // Bogotá = UTC-5
        assertEquals(r(1705276800000L + 5 * 3600 * 1000, null), Referencia.fechaEpoch("15/01/2024", fmts, "America/Bogota"));
    }

    /** F-FW-10: ISO solo fecha (uuuu-MM-dd): 00:00 en la zona, estricto, convive con los otros formatos. */
    @Test
    void fechaIsoSoloFecha() {
        List<Object> iso = List.of("uuuu-MM-dd");
        assertEquals(r(1705276800000L, null), Referencia.fechaEpoch("2024-01-15", iso, "UTC"));
        assertEquals(r(1705276800000L + 5 * 3600 * 1000, null), Referencia.fechaEpoch("2024-01-15", iso, "America/Bogota"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.fechaEpoch("2024-02-30", iso, "UTC"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.fechaEpoch("2024-1-5", iso, "UTC"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.fechaEpoch("0000-01-15", iso, "UTC"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.fechaEpoch("15/01/2024", iso, "UTC"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.fechaEpoch("2024-01-15 10:30:00", iso, "UTC"));
        List<Object> todos = List.of("dd/MM/uuuu", "uuuu-MM-dd HH:mm:ss", "uuuu-MM-dd");
        assertEquals(r(1705276800000L, null), Referencia.fechaEpoch("2024-01-15", todos, "UTC"));
        assertEquals(r(1705276800000L, null), Referencia.fechaEpoch("15/01/2024", todos, "UTC"));
        assertEquals(r(1705314600000L, null), Referencia.fechaEpoch("2024-01-15 10:30:00", todos, "UTC"));
    }

    @Test
    void homologacionTipDocumentoCompleta() {
        Map<String, Object> hom = map(spec.get("homologacion_tipo_documento"));
        Map<String, String> tabla = new LinkedHashMap<>();
        map(hom.get("tabla")).forEach((k, v) -> tabla.put(Py.str(k), Py.str(v)));
        Set<String> pend = new HashSet<>();
        list(hom.get("pendientes")).forEach(p -> pend.add(Py.str(p)));
        Map<String, String> esperado = Map.of("1", "10", "2", "7", "3", "1", "4", "4", "5", "8", "6", "5", "8", "9", "PPT", "11");
        esperado.forEach((sisben, experian) -> assertEquals(r(experian, null), Referencia.homologar(sisben, tabla, pend)));
        assertEquals(r("11", null), Referencia.homologar("ppt", tabla, pend));
        assertEquals(r(null, Referencia.HOMOLOGACION_PENDIENTE), Referencia.homologar("7", tabla, pend));
        assertEquals(r(null, Referencia.NO_HOMOLOGABLE), Referencia.homologar("99", tabla, pend));
    }

    @Test
    void documentoReglasPorTipo() {
        Map<String, Object> rd = map(spec.get("reglas_documento"));
        Map<String, Object> porTipo = map(rd.get("por_tipo"));
        assertEquals(r("9990001", null), Referencia.documento(" 000 9990001 ", map(rd.get("por_defecto"))));
        assertEquals(r("9990003", null), Referencia.documento("PE0999A0003", map(porTipo.get("8"))));
        assertEquals(r("0099900002", null), Referencia.documento("0099900002", map(porTipo.get("PPT"))));
        assertEquals(r("AB0999006", null), Referencia.documento("AB0999006", map(porTipo.get("6"))));
        assertEquals(r(null, Referencia.NO_NORMALIZABLE), Referencia.documento("000", map(rd.get("por_defecto"))));
        assertEquals(r("00042", null), Referencia.documento("42", Map.of("padding", 5)));
    }

    /** F-FW-14: las letras del número salen en mayúsculas; conservar_original devuelve el valor tal como vino. */
    @Test
    void documentoLetrasEnMayusculas() {
        Map<String, Object> defecto = map(map(spec.get("reglas_documento")).get("por_defecto"));
        assertEquals(r("AB0012C", null), Referencia.documento(" ab 0012c ", defecto));
        assertEquals(r("AB12", null), Referencia.documento("0ab12", defecto));
        assertEquals(r("9990003", null), Referencia.documento("pe0999a0003",
                Map.of("solo_digitos", true, "quitar_ceros_izquierda", true)));
        assertEquals(r("0ab12", null), Referencia.documento("0ab12", Map.of("conservar_original", true)));
        assertEquals(r(null, Referencia.NO_NORMALIZABLE), Referencia.documento("ab", Map.of("solo_digitos", true)));
    }

    /** F-FW-11 (D14): subscriberRelation = campos curados unidos; sin extensión o con un componente faltante, el aceptado. */
    @Test
    void subscriberRelationUneCamposOConservaElAceptado() {
        Map<String, Object> conExt = new LinkedHashMap<>(spec);
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("campos", List.of("tipoDocumento", "numeroDocumento"));
        ext.put("separador", "_");
        conExt.put("extension_subscriber_relation", ext);

        Map<String, Object> completo = Referencia.curar("{\"TIP_DOCUMENTO\": \"3\", \"NUM_DOCUMENTO\": \"0009990001\"}", ARCHIVO, List.of(), conExt);
        assertEquals("1_9990001", Referencia.subscriberRelation(completo, conExt, "COPIADO"));
        assertEquals("COPIADO", Referencia.subscriberRelation(completo, spec, "COPIADO")); // sin extensión: se copia

        Map<String, Object> sinNumero = Referencia.curar("{\"TIP_DOCUMENTO\": \"3\"}", ARCHIVO, List.of(), conExt);
        assertEquals("COPIADO", Referencia.subscriberRelation(sinNumero, conExt, "COPIADO"));
        Map<String, Object> tipoDesconocido = Referencia.curar("{\"TIP_DOCUMENTO\": \"99\", \"NUM_DOCUMENTO\": \"1\"}", ARCHIVO, List.of(), conExt);
        assertEquals("COPIADO", Referencia.subscriberRelation(tipoDesconocido, conExt, "COPIADO"));
        assertEquals("COPIADO", Referencia.subscriberRelation(Map.of(), conExt, "COPIADO"));

        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("eventId", "evt-x");
        evento.put("lineage", Map.of("fileName", ARCHIVO));
        evento.put("validatedRecordJson", "{\"TIP_DOCUMENTO\": \"3\", \"NUM_DOCUMENTO\": \"0009990001\"}");
        evento.put("subscriberRelation", "0009990001");
        assertEquals("1_9990001", Referencia.curarEvento(evento, conExt).get("subscriberRelation"));
        assertEquals("0009990001", Referencia.curarEvento(evento, spec).get("subscriberRelation"));
    }

    @Test
    void convertirTipos() {
        BigInteger dos31 = BigInteger.TWO.pow(31);
        assertEquals(r(12L, null), Referencia.convertir("12", "entero"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.convertir("X1", "entero"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.convertir(dos31.toString(), "entero"));
        assertEquals(r(dos31.longValue(), null), Referencia.convertir(dos31.toString(), "largo"));
        assertEquals(r(1.5, null), Referencia.convertir("1.5", "decimal"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.convertir("1,5", "decimal"));
        assertEquals(r(true, null), Referencia.convertir("TRUE", "booleano"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.convertir("si", "booleano"));
        assertEquals(r("5", null), Referencia.convertir(5L, "texto"));
        assertEquals(r(7, null), Referencia.convertir(7, "largo"));
        assertEquals(r(null, Referencia.FORMATO_INVALIDO), Referencia.convertir("x", "otro"));
    }

    @Test
    void infoArchivo() {
        assertEquals(List.of("RT", 1709164800000L),
                List.of(Referencia.infoArchivo("RT_SISBEN_EXPERIAN_202402.20240306080000-0500.txt", spec)));
        assertEquals("EP", Referencia.infoArchivo("ruta/EP_SISBEN_EXPERIAN_202401.20240206080000-0500", spec)[0]);
        assertEquals("REGULAR", Referencia.infoArchivo(ARCHIVO, spec)[0]);
        Object[] mes13 = Referencia.infoArchivo("SISBEN_EXPERIAN_202413.20240205103000-0500.txt", spec);
        assertEquals("REGULAR", mes13[0]);
        assertNull(mes13[1]);
        Object[] otra = Referencia.infoArchivo("OTRA.txt", spec);
        assertNull(otra[0]);
        assertNull(otra[1]);
    }

    @Test
    void filePattern() {
        Map<String, Object> archivo = map(spec.get("archivo"));
        list(archivo.get("ejemplos_sinteticos")).forEach(ej -> assertTrue(Referencia.coincideFilePattern((String) ej, spec)));
        list(archivo.get("contraejemplos_sinteticos")).forEach(ej -> assertFalse(Referencia.coincideFilePattern((String) ej, spec)));
    }

    @Test
    void curarNuncaLanzaYAdvierte() {
        Map<String, Object> out = Referencia.curar("{no es json", ARCHIVO, List.of(), spec);
        assertTrue(list(out.get(campoAdv)).contains("registro:REGISTRO_ILEGIBLE"));
        assertEquals("REGULAR", out.get("tipoArchivo"));
        out = Referencia.curar(null, null, null, spec);
        assertTrue(list(out.get(campoAdv)).contains("tipoArchivo:ARCHIVO_NO_RECONOCIDO"));
        assertTrue(list(Referencia.curar("[1, 2]", ARCHIVO, null, spec).get(campoAdv)).contains("registro:REGISTRO_ILEGIBLE"));
        assertTrue(list(Referencia.curar("{} extra", ARCHIVO, null, spec).get(campoAdv)).contains("registro:REGISTRO_ILEGIBLE"));
    }

    @Test
    void severidad3OmiteY0Anula() {
        String reg = "{\"SEG_APELLIDO\": \"Ficticio\", \"SEG_NOMBRE\": \"Prueba\", \"TIP_DOCUMENTO\": \"3\", \"NUM_DOCUMENTO\": \"1\"}";
        Map<String, Object> out = Referencia.curar(reg, ARCHIVO,
                List.of(Map.of("codigo", "SISBEN_INC_011", "nivel", "3"), Map.of("codigo", "SISBEN_INC_013", "nivel", "0")), spec);
        assertFalse(out.containsKey("segundoApellido"));
        assertNull(out.get("segundoNombre"));
        assertTrue(list(out.get(campoAdv)).stream().noneMatch(a -> ((String) a).startsWith("segundo")));
    }

    @Test
    void advertenciasSinValoresDeDato() {
        String reg = "{\"ID_HOGAR\": \"VALOR-SECRETO\", \"TIP_DOCUMENTO\": \"3\", \"NUM_DOCUMENTO\": \"9990001\"}";
        String texto = Py.jsonDumps(Referencia.curar(reg, ARCHIVO, List.of(), spec).get(campoAdv));
        assertFalse(texto.contains("VALOR-SECRETO") || texto.contains("9990001"));
    }

    @Test
    void numerosJsonSinDecimal() {
        Map<String, Object> out = Referencia.curar("{\"ID_HOGAR\": 2.0, \"TIP_DOCUMENTO\": 3}", ARCHIVO, List.of(), spec);
        assertEquals(2L, out.get("idHogar"));
        assertEquals(1L, out.get("tipoDocumento"));
    }

    @Test
    void valoresJsonNoTextoComoStrDePython() {
        Map<String, String> reg = Referencia.parseRegistro("{\" a \": true, \"b\": [1, \"x\"], \"c\": {\"k\": 1.5}, \"d\": null}");
        assertEquals(Map.of("A", "True", "B", "[1, 'x']", "C", "{'k': 1.5}"), reg);
    }
}

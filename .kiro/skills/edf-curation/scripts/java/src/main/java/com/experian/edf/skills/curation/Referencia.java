package com.experian.edf.skills.curation;

import static com.experian.edf.skills.curation.Py.list;
import static com.experian.edf.skills.curation.Py.map;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementación de referencia de la lógica de curado SISBEN.
 *
 * <p>Replica, función por función, lo que hacen las plantillas Scala de Capa A (Transformations.scala.tpl)
 * y el núcleo puro del processor (CurationProcessor.scala.tpl). Sirve para calcular la salida esperada del
 * fixture sintético sin Spark ni JAR del framework (F-01) y para probar las reglas de transformación.
 * No reemplaza la compilación ni la ejecución en Oxygen: si la lógica Scala cambia, esta clase debe cambiar
 * igual (lo verifica el checklist de entrega). Nunca lanza excepciones por valor de dato: devuelve
 * (valor | null, motivo | null) (CUR-RC03).
 */
public final class Referencia {

    // Motivos de advertencia (sin PII: solo nombre de campo + motivo).
    public static final String FORMATO_INVALIDO = "FORMATO_INVALIDO";
    public static final String NO_HOMOLOGABLE = "NO_HOMOLOGABLE";
    public static final String HOMOLOGACION_PENDIENTE = "HOMOLOGACION_PENDIENTE";
    public static final String NO_NORMALIZABLE = "NO_NORMALIZABLE";
    public static final String REGISTRO_ILEGIBLE = "REGISTRO_ILEGIBLE";
    public static final String ARCHIVO_NO_RECONOCIDO = "ARCHIVO_NO_RECONOCIDO";

    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    // \d y \s de Python reconocen dígitos y espacios Unicode.
    private static final int U = Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern DIGITOS = Pattern.compile("\\d+", U);
    private static final Pattern ESPACIOS = Pattern.compile("\\s+", U);
    private static final Pattern NO_DIGITO = Pattern.compile("\\D", U);
    private static final Pattern ENTERO = Pattern.compile("[+-]?\\d+", U);
    private static final Pattern DECIMAL = Pattern.compile("[+-]?\\d+(\\.\\d+)?", U);
    private static final Pattern SEPARADOR = Pattern.compile("[\\\\/]");
    private static final Map<String, Pattern> FORMATOS = Map.of(
            "dd/MM/uuuu", Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})$", U),
            "uuuu-MM-dd HH:mm:ss", Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})$", U),
            // F-FW-10: ISO solo fecha (00:00 en la zona). Año, mes y día quedan en n[0], n[1], n[2], como el formato con hora.
            "uuuu-MM-dd", Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$", U));
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    /** Valor transformado y motivo de advertencia (null si no hay). */
    public record Resultado(Object valor, String motivo) {
        static Resultado ok(Object valor) {
            return new Resultado(valor, null);
        }

        static Resultado falla(String motivo) {
            return new Resultado(null, motivo);
        }
    }

    private Referencia() {
    }

    /** Zona horaria; "UTC" sin distinguir mayúsculas. */
    public static ZoneId zona(String nombre) {
        return nombre.toUpperCase(Locale.ROOT).equals("UTC") ? ZoneOffset.UTC : ZoneId.of(nombre);
    }

    static String limpio(Object valor) {
        if (valor == null) {
            return null;
        }
        String s = ((String) valor).strip();
        return s.isEmpty() ? null : s;
    }

    /** Enteros con dígitos Unicode, como int() de Python. */
    static String asciiDigitos(String s) {
        StringBuilder out = new StringBuilder();
        s.codePoints().forEach(cp -> out.append(Character.isDigit(cp) ? (char) ('0' + Character.digit(cp, 10)) : (char) cp));
        return out.toString();
    }

    static String rellenarIzquierda(String s, int longitud) {
        return s.length() >= longitud ? s : "0".repeat(longitud - s.length()) + s;
    }

    // ---------------------------------------------------------------- Capa A: acciones puras

    public static Resultado texto(Object valor) {
        return Resultado.ok(limpio(valor));
    }

    public static Resultado nombre(Object valor, boolean conservarEnie) {
        String s = limpio(valor);
        if (s == null) {
            return Resultado.ok(null);
        }
        s = s.toUpperCase(Locale.ROOT);
        String marca = "\u0001";
        if (conservarEnie) {
            s = s.replace("Ñ", marca);
        }
        StringBuilder sinMarcas = new StringBuilder();
        Normalizer.normalize(s, Normalizer.Form.NFD).codePoints()
                .filter(cp -> Character.getType(cp) != Character.NON_SPACING_MARK)
                .forEach(sinMarcas::appendCodePoint);
        s = sinMarcas.toString();
        if (conservarEnie) {
            s = s.replace(marca, "Ñ");
        }
        s = s.replaceAll(conservarEnie ? "[^A-ZÑ ]" : "[^A-Z ]", " ");
        s = ESPACIOS.matcher(s).replaceAll(" ").strip();
        return s.isEmpty() ? Resultado.falla(NO_NORMALIZABLE) : Resultado.ok(s);
    }

    public static Resultado paddingIzquierda(Object valor, int longitud) {
        String s = limpio(valor);
        if (s == null) {
            return Resultado.ok(null);
        }
        if (!DIGITOS.matcher(s).matches() || s.codePointCount(0, s.length()) > longitud) {
            return Resultado.falla(FORMATO_INVALIDO);
        }
        return Resultado.ok(rellenarIzquierda(s, longitud));
    }

    public static Resultado fechaEpoch(Object valor, List<Object> formatos, String nombreZona) {
        String s = limpio(valor);
        if (s == null) {
            return Resultado.ok(null);
        }
        for (Object fmt : formatos) {
            Pattern patron = FORMATOS.get(fmt);
            if (patron == null) {
                throw new IllegalArgumentException("formato de fecha no soportado: " + fmt);
            }
            Matcher m = patron.matcher(s);
            if (m.matches()) {
                int[] n = new int[6];
                for (int i = 0; i < m.groupCount(); i++) {
                    n[i] = Integer.parseInt(asciiDigitos(m.group(i + 1)));
                }
                boolean diaPrimero = fmt.equals("dd/MM/uuuu");
                int anio = diaPrimero ? n[2] : n[0];
                int dia = diaPrimero ? n[0] : n[2];
                try {
                    if (anio < 1) {
                        throw new DateTimeException("año fuera de rango");
                    }
                    LocalDateTime dt = LocalDateTime.of(anio, n[1], dia, n[3], n[4], n[5]);
                    return Resultado.ok(dt.atZone(zona(nombreZona)).toInstant().toEpochMilli());
                } catch (DateTimeException e) {
                    return Resultado.falla(FORMATO_INVALIDO);
                }
            }
        }
        return Resultado.falla(FORMATO_INVALIDO);
    }

    public static Resultado homologar(Object valor, Map<String, String> tabla, Set<String> pendientes) {
        String s = limpio(valor);
        if (s == null) {
            return Resultado.ok(null);
        }
        s = s.toUpperCase(Locale.ROOT);
        if (pendientes.contains(s)) {
            return Resultado.falla(HOMOLOGACION_PENDIENTE);
        }
        if (tabla.containsKey(s)) {
            return Resultado.ok(tabla.get(s));
        }
        return Resultado.falla(NO_HOMOLOGABLE);
    }

    public static Resultado documento(Object valor, Map<String, Object> regla) {
        String s = limpio(valor);
        if (s == null) {
            return Resultado.ok(null);
        }
        if (Py.truthy(regla.get("conservar_original"))) {
            return Resultado.ok(s);
        }
        // F-FW-14: Pinning compara texto exacto (steering 11 §3); las letras del número van en mayúsculas.
        // conservar_original (arriba) no se toca: devuelve el valor tal como vino.
        s = ESPACIOS.matcher(s).replaceAll("").toUpperCase(Locale.ROOT);
        if (Py.truthy(regla.get("solo_digitos"))) {
            s = NO_DIGITO.matcher(s).replaceAll("");
        }
        if (Py.truthy(regla.get("quitar_ceros_izquierda"))) {
            s = s.replaceFirst("^0+", "");
        }
        if (Py.truthy(regla.get("padding"))) {
            s = rellenarIzquierda(s, Py.toInt(regla.get("padding")));
        }
        return s.isEmpty() ? Resultado.falla(NO_NORMALIZABLE) : Resultado.ok(s);
    }

    /**
     * F-FW-11 (D14): subscriberRelation según extension_subscriber_relation: los campos curados indicados, unidos
     * por el separador. Sin el bloque en la spec, o si falta algún componente (nulo o vacío), devuelve {@code copiado},
     * el subscriberRelation del evento aceptado. Nunca lanza.
     */
    public static String subscriberRelation(Map<String, Object> curado, Map<String, Object> spec, String copiado) {
        Object ext = spec.get("extension_subscriber_relation");
        if (ext == null) {
            return copiado;
        }
        List<String> partes = new ArrayList<>();
        for (Object campo : list(map(ext).get("campos"))) {
            Object valor = curado.get(campo);
            String s = valor == null ? null : limpio(Py.str(valor));
            if (s == null) {
                return copiado;
            }
            partes.add(s);
        }
        return String.join(Py.str(map(ext).get("separador")), partes);
    }

    /** Convierte el resultado de la transformación al tipo de salida (RF-CUR-05). */
    public static Resultado convertir(Object valor, String tipo) {
        if (valor == null) {
            return Resultado.ok(null);
        }
        if (tipo.equals("texto")) {
            return Resultado.ok(Py.str(valor));
        }
        boolean entero = tipo.equals("entero") || tipo.equals("largo");
        // En Python bool es un int: pasa tal cual igual que un entero.
        if ((Py.isInteger(valor) || valor instanceof Boolean) && entero) {
            return Resultado.ok(valor);
        }
        String s = Py.str(valor).strip();
        if (entero) {
            if (!ENTERO.matcher(s).matches()) {
                return Resultado.falla(FORMATO_INVALIDO);
            }
            BigInteger n = new BigInteger(asciiDigitos(s));
            BigInteger lo = tipo.equals("entero") ? INT_MIN : LONG_MIN;
            BigInteger hi = tipo.equals("entero") ? INT_MAX : LONG_MAX;
            return n.compareTo(lo) >= 0 && n.compareTo(hi) <= 0 ? Resultado.ok(n.longValue()) : Resultado.falla(FORMATO_INVALIDO);
        }
        if (tipo.equals("decimal")) {
            if (!DECIMAL.matcher(s).matches()) { // separador decimal: punto
                return Resultado.falla(FORMATO_INVALIDO);
            }
            return Resultado.ok(Double.parseDouble(asciiDigitos(s)));
        }
        if (tipo.equals("booleano")) {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.equals("true") || lower.equals("false")) {
                return Resultado.ok(lower.equals("true"));
            }
            return Resultado.falla(FORMATO_INVALIDO);
        }
        return Resultado.falla(FORMATO_INVALIDO);
    }

    // ---------------------------------------------------------------- archivo

    /** Tipo de archivo y fecha de corte (último día de AAAAMM, 00:00, epoch ms); null si no aplica. */
    public static Object[] infoArchivo(String fileName, Map<String, Object> spec) {
        String[] partes = SEPARADOR.split(fileName == null ? "" : fileName, -1);
        String base = partes[partes.length - 1];
        Map<String, Object> archivo = map(spec.get("archivo"));
        Matcher m = Pattern.compile((String) archivo.get("regex_derivacion")).matcher(base);
        if (!m.find()) {
            return new Object[] {null, null};
        }
        String prefijo = m.group(1) == null ? "" : m.group(1);
        Object tipo = map(archivo.get("tipo_por_prefijo")).get(prefijo);
        String aaaamm = m.group(2);
        int anio = Integer.parseInt(asciiDigitos(aaaamm.substring(0, 4)));
        int mes = Integer.parseInt(asciiDigitos(aaaamm.substring(4)));
        if (mes < 1 || mes > 12) {
            return new Object[] {tipo, null};
        }
        LocalDateTime dt = YearMonth.of(anio, mes).atEndOfMonth().atStartOfDay();
        String nombreZona = (String) map(spec.get("parametros")).get("zona_horaria_fechas");
        return new Object[] {tipo, dt.atZone(zona(nombreZona)).toInstant().toEpochMilli()};
    }

    /** El motor resuelve el processor con una coincidencia completa (String.matches). */
    public static boolean coincideFilePattern(String fileName, Map<String, Object> spec) {
        return Pattern.compile((String) map(spec.get("archivo")).get("file_pattern"))
                .matcher(fileName == null ? "" : fileName).matches();
    }

    // ---------------------------------------------------------------- núcleo del processor

    /** JSON del registro validado como mapa CLAVE -> texto; null si no es un objeto JSON legible. */
    static Map<String, String> parseRegistro(String validatedRecordJson) {
        if (validatedRecordJson == null) {
            return null;
        }
        Object datos;
        try {
            datos = JSON.readValue(validatedRecordJson, Object.class);
        } catch (Exception e) {
            return null;
        }
        if (!(datos instanceof Map<?, ?> mapa)) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : mapa.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof Double d && !d.isInfinite() && !d.isNaN() && d == Math.rint(d)) {
                v = new BigDecimal(d).toBigInteger();
            }
            out.put(Py.str(e.getKey()).strip().toUpperCase(Locale.ROOT), Py.str(v));
        }
        return out;
    }

    static Map<String, Object> reglaDocumento(String tipoRaw, Map<String, Object> spec) {
        Map<String, Object> reglas = map(spec.get("reglas_documento"));
        String clave = (tipoRaw == null ? "" : tipoRaw).strip().toUpperCase(Locale.ROOT);
        Map<String, Object> porTipo = map(reglas.get("por_tipo"));
        return porTipo.containsKey(clave) ? map(porTipo.get(clave)) : map(reglas.get("por_defecto"));
    }

    /** Mapa curado (claves = nombres publicados) más el campo de advertencias. */
    public static Map<String, Object> curar(String validatedRecordJson, String fileName, List<Map<String, Object>> errores,
                                            Map<String, Object> spec) {
        Map<String, Object> params = map(spec.get("parametros"));
        Map<String, Object> hom = map(spec.get("homologacion_tipo_documento"));
        Map<String, String> tablaHom = new LinkedHashMap<>();
        map(hom.get("tabla")).forEach((k, v) -> tablaHom.put(Py.str(k), Py.str(v)));
        Set<String> pendientes = new HashSet<>();
        list(hom.get("pendientes")).forEach(p -> pendientes.add(Py.str(p).toUpperCase(Locale.ROOT)));
        List<String> advertencias = new ArrayList<>();

        Map<String, String> registro = parseRegistro(validatedRecordJson);
        if (registro == null) {
            advertencias.add("registro:" + REGISTRO_ILEGIBLE);
            registro = new LinkedHashMap<>();
        }

        Map<String, Object> codigos = map(spec.get("codigos_error"));
        Set<Object> omitirEntrada = new HashSet<>();
        Set<Object> anularEntrada = new HashSet<>();
        for (Map<String, Object> e : errores == null ? List.<Map<String, Object>>of() : errores) {
            Object codigo = Py.truthy(e.get("codigo")) ? e.get("codigo") : e.get("errorCodeValue");
            Object campo = codigos.get(codigo);
            String nivel = Py.str(e.containsKey("nivel") ? e.get("nivel")
                    : e.containsKey("errorLevel") ? e.get("errorLevel") : "");
            if (campo == null) {
                continue;
            }
            if (list(params.get("niveles_no_almacenar")).contains(nivel)) {
                omitirEntrada.add(campo);
            } else if (list(params.get("niveles_anular")).contains(nivel)) {
                anularEntrada.add(campo);
            }
        }

        Object[] info = infoArchivo(fileName, spec);
        Map<String, Object> salida = new LinkedHashMap<>();
        for (Object o : list(spec.get("campos_curados"))) {
            Map<String, Object> m = map(o);
            String destino = (String) m.get("destino");
            String origen = Py.truthy(m.get("origen")) ? (String) m.get("origen") : "";
            Map<String, Object> t = map(m.get("transformacion"));
            if (!origen.isEmpty() && omitirEntrada.contains(origen)) {
                continue; // severidad 3: no se almacena (RF-CUR-10)
            }
            String crudo = origen.isEmpty() || anularEntrada.contains(origen) ? null : registro.get(origen);
            Resultado r = switch ((String) t.get("tipo")) {
                case "texto", "original" -> texto(crudo);
                case "nombre" -> nombre(crudo, Py.truthy(params.get("conservar_enie")));
                case "padding_izquierda" -> paddingIzquierda(crudo, Py.toInt(t.get("longitud")));
                case "fecha_epoch" -> fechaEpoch(crudo, list(params.get("formatos_fecha")),
                        (String) params.get("zona_horaria_fechas"));
                case "homologacion" -> homologar(crudo, tablaHom, pendientes);
                case "documento" -> documento(crudo, reglaDocumento(
                        registro.get((String) map(spec.get("reglas_documento")).get("campo_tipo")), spec));
                case "desde_archivo" -> {
                    Object valor = "tipo_archivo".equals(t.get("atributo")) ? info[0] : info[1];
                    yield new Resultado(valor, valor == null ? ARCHIVO_NO_RECONOCIDO : null);
                }
                case "copia_de" -> Resultado.ok(salida.get(t.get("campo")));
                case "constante" -> Resultado.ok(t.get("valor"));
                default -> Resultado.falla(FORMATO_INVALIDO);
            };
            if (r.motivo() == null) {
                r = convertir(r.valor(), (String) m.get("tipo_salida"));
            }
            if (r.motivo() != null) {
                advertencias.add(destino + ":" + r.motivo());
            }
            salida.put(destino, r.valor());
        }
        salida.put((String) params.get("campo_advertencias"), advertencias);
        return salida;
    }

    /** Simula el motor + processor sobre un TaggedValidatedEvent. null = el motor no lo procesa. */
    public static Map<String, Object> curarEvento(Map<String, Object> evento, Map<String, Object> spec) {
        Map<String, Object> lineage = map(evento.get("lineage"));
        String fileName = (String) lineage.get("fileName");
        if (!coincideFilePattern(fileName, spec)) {
            return null; // CUR-AP02: sin processor, el motor lo descarta sin log
        }
        List<Map<String, Object>> errores = new ArrayList<>();
        for (Object e : list(evento.get("errorCodes"))) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("codigo", map(e).get("errorCodeValue"));
            err.put("nivel", map(e).get("errorLevel"));
            errores.add(err);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("eventId", evento.get("eventId"));
        out.put("lineage", lineage);
        out.put("eventType", map(spec.get("fuente")).get("output_event_type"));
        out.put("payload", evento.containsKey("payload") ? evento.get("payload") : new LinkedHashMap<>());
        out.put("asOfDate", evento.get("asOfDate"));
        out.put("subscriber", evento.get("subscriber"));
        Map<String, Object> curado = curar((String) evento.get("validatedRecordJson"), fileName, errores, spec);
        out.put("subscriberRelation", subscriberRelation(curado, spec, (String) evento.get("subscriberRelation")));
        out.put("curatedRecordJson", curado);
        return out;
    }

    /** Parser JSON de los registros (lo usa también el renderizador para el resumen). */
    static Object leerJson(String texto) {
        try {
            return JSON.readValue(texto, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON inválido: " + e.getMessage(), e);
        }
    }
}

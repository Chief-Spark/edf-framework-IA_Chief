package com.experian.edf.skills.validation;

import static com.experian.edf.skills.validation.Verificaciones.list;
import static com.experian.edf.skills.validation.Verificaciones.listOr;
import static com.experian.edf.skills.validation.Verificaciones.map;
import static com.experian.edf.skills.validation.Verificaciones.strings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Valores de los {{huecos}} de las plantillas de Validation, calculados desde el source-spec. */
public final class Contexto {

    static final Map<String, String> SEVERIDAD_SCALA = Map.of("1", "Severidad.Error", "3", "Severidad.Inconsistencia");
    static final Map<String, String> TIPO_SCALA = Map.of("alfanumerico", "TipoDato.Alfanumerico",
            "numerico", "TipoDato.Numerico", "fecha", "TipoDato.Fecha");
    static final Map<String, String> NORMALIZACION_SCALA = Map.of("quitar_espacios", "Normalizacion.QuitarEspacios",
            "quitar_ceros_izquierda", "Normalizacion.QuitarCerosIzquierda");

    private Contexto() {
    }

    // ------------------------------------------------------------------------------------ utilidades

    /** Contenido de un literal de cadena Scala (sin comillas). */
    public static String scalaStr(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /** Contenido de una cadena HOCON entre comillas (mismas reglas que JSON). */
    public static String hoconStr(Object value) {
        String json = Py.jsonDumps(value);
        return json.substring(1, json.length() - 1);
    }

    static String scalaList(List<?> values) {
        return values.stream().map(v -> "\"" + scalaStr((String) v) + "\"").collect(Collectors.joining(", "));
    }

    /** NOMBRE_EN_MAYUSCULAS -> nombreEnMayusculas. */
    public static String camel(String name) {
        String[] parts = name.toLowerCase().split("_", -1);
        StringBuilder out = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            out.append(p.isEmpty() ? "" : Character.toUpperCase(p.charAt(0)) + p.substring(1));
        }
        return out.toString();
    }

    static String opt(Object value, Function<Object, String> render) {
        return value == null ? "None" : "Some(" + render.apply(value) + ")";
    }

    static String lower(Object b) {
        return Py.str(b).toLowerCase();
    }

    /** Línea cruda de un caso del fixture (registro base + cambios, o línea literal). */
    public static String buildFixtureLine(Map<String, Object> spec, Map<String, Object> caso) {
        if (caso.containsKey("linea")) {
            return (String) caso.get("linea");
        }
        Map<String, Object> fixture = map(spec.get("fixture"));
        List<String> values = new ArrayList<>(strings(fixture.get("registro_base")));
        Map<String, Map<String, Object>> fields = Verificaciones.fieldByName(spec);
        Object cambios = caso.get("cambios");
        if (Py.truthy(cambios)) {
            map(cambios).forEach((name, value) ->
                    values.set(((Number) fields.get(name).get("posicion")).intValue() - 1, (String) value));
        }
        List<String> result = values;
        if (caso.containsKey("columnas")) {
            int n = ((Number) caso.get("columnas")).intValue();
            result = new ArrayList<>(values.subList(0, Math.min(n, values.size())));
        }
        result.addAll(strings(caso.get("columnas_extra")));
        return String.join((String) map(spec.get("archivo")).get("delimitador"), result);
    }

    // ------------------------------------------------------------------------------- fragmentos

    /**
     * Una línea de comentario Scala {@code // ASSUMPTION <ID>: ...} por cada ID de la lista opcional
     * {@code supuestos} de un elemento del source-spec (campo, regla o catálogo), con la sangría dada (ADR-G1).
     */
    static String marcasSupuesto(Map<String, Object> item, String sangria) {
        StringBuilder out = new StringBuilder();
        for (Object id : list(item.get("supuestos"))) {
            out.append(sangria).append("// ASSUMPTION ").append(id)
                    .append(": valor supuesto en el source-spec (references/decisiones-y-abiertos.md)\n");
        }
        return out.toString();
    }

    static String renderFieldspec(Map<String, Object> c) {
        List<Object> alias = new ArrayList<>();
        alias.add(c.get("nombre"));
        list(c.get("alias")).stream().filter(a -> !a.equals(c.get("nombre"))).forEach(alias::add);
        String rango = opt(c.get("rango"), r -> "RangoEntero(" + Py.str(list(r).get(0)) + "L, " + Py.str(list(r).get(1)) + "L)");
        String dominio = opt(c.get("dominio"), d -> "Set(" + scalaList(list(d)) + ")");
        String catalogo = opt(c.get("catalogo"), k -> "CatalogoRef(\"" + scalaStr((String) map(k).get("tabla")) + "\", \""
                + scalaStr((String) map(k).get("columna")) + "\")");
        String normal = listOr(c, "normalizacion").stream().map(n -> NORMALIZACION_SCALA.get((String) n))
                .collect(Collectors.joining(", "));
        return marcasSupuesto(c, "    ") + "    FieldSpec(nombre = " + c.get("nombre") + ", posicion = " + Py.str(c.get("posicion"))
                + ", aliasHeader = Seq(" + scalaList(alias) + "),\n"
                + "      obligatorio = " + lower(c.get("obligatorio")) + ", severidad = " + SEVERIDAD_SCALA.get(c.get("severidad"))
                + ", tipo = " + TIPO_SCALA.get(c.get("tipo")) + ",\n"
                + "      longitudMax = " + opt(c.get("longitud_max"), Py::str) + ", rango = " + rango + ", dominio = " + dominio + ",\n"
                + "      catalogo = " + catalogo + ", formatosFecha = Seq(" + scalaList(listOr(c, "formatos_fecha")) + "), "
                + "noFutura = " + lower(c.getOrDefault("no_futura", false)) + ",\n"
                + "      normalizacion = Seq(" + normal + "), codigoError = \"" + scalaStr((String) c.get("codigo")) + "\")";
    }

    static String renderRegla(Map<String, Object> r) {
        String sev = SEVERIDAD_SCALA.get(r.get("severidad"));
        String marcas = marcasSupuesto(r, "    ");
        if ("correspondencia_catalogo".equals(r.get("tipo"))) {
            return marcas + "    CorrespondenciaCatalogo(id = \"" + scalaStr((String) r.get("id")) + "\", campoCodigo = "
                    + r.get("campo_codigo") + ", campoNombre = " + r.get("campo_nombre") + ",\n"
                    + "      tabla = \"" + scalaStr((String) r.get("tabla")) + "\", columnaClave = \""
                    + scalaStr((String) r.get("columna_clave")) + "\", columnaNombre = \""
                    + scalaStr((String) r.get("columna_nombre")) + "\",\n"
                    + "      codigo = \"" + scalaStr((String) r.get("codigo")) + "\", severidad = " + sev + ")";
        }
        return marcas + "    ValorConCodigoPropio(id = \"" + scalaStr((String) r.get("id")) + "\", campo = " + r.get("campo")
                + ", valores = Set(" + scalaList(list(r.get("valores"))) + "),\n"
                + "      codigo = \"" + scalaStr((String) r.get("codigo")) + "\", severidad = " + sev
                + ", causa = \"" + scalaStr((String) r.get("causa")) + "\")";
    }

    static String renderLookup(Map<String, Object> cat) {
        String keys = list(cat.get("keys")).stream().map(k -> "      { index = \"" + hoconStr(k) + "\" }")
                .collect(Collectors.joining(",\n"));
        return "  {\n"
                + "    tableName = \"" + hoconStr(cat.get("tabla")) + "\"\n"
                + "    file = \"" + hoconStr(cat.get("archivo")) + "\"\n"
                + "    fileFormat = \"csv\"\n"
                + "    delimiter = \"" + hoconStr(cat.get("delimitador")) + "\"\n"
                + "    keys = [\n" + keys + "\n    ]\n"
                + "  }";
    }

    static String renderMetric(Map<String, Object> m) {
        return "        {\n"
                + "          metricId = \"" + hoconStr(m.get("id")) + "\"\n"
                + "          metricExpr = \"" + hoconStr(m.get("expr")) + "\"\n"
                + "        }";
    }

    static String renderFieldTests(Map<String, Object> spec) {
        List<String> blocks = new ArrayList<>();
        for (Object o : list(spec.get("campos"))) {
            Map<String, Object> c = map(o);
            blocks.add("  test(\"" + c.get("nombre") + " inválido produce " + c.get("codigo") + "\") {\n"
                    + "    assert(codigos(conValor(" + Py.str(c.get("posicion")) + ", \""
                    + scalaStr((String) c.get("ejemplo_invalido")) + "\")).contains(\"" + c.get("codigo") + "\"))\n"
                    + "  }");
        }
        return String.join("\n\n", blocks);
    }

    static String renderFixtureTests(Map<String, Object> spec) {
        List<String> blocks = new ArrayList<>();
        List<String> dupLines = new ArrayList<>();
        List<Boolean> dupFlags = new ArrayList<>();
        for (Object o : list(map(spec.get("fixture")).get("casos"))) {
            Map<String, Object> caso = map(o);
            String line = scalaStr(buildFixtureLine(spec, caso));
            String title = scalaStr("fixture " + caso.get("id") + ": " + caso.get("descripcion"));
            Map<String, Object> esperado = map(caso.get("esperado"));
            String body;
            if (Py.truthy(esperado.get("filtrado"))) {
                body = "    assert(RecordValidator.debeFiltrar(spec, \"" + line + "\"))";
            } else {
                body = "    assert(!RecordValidator.debeFiltrar(spec, \"" + line + "\"))\n"
                        + "    assert(codigos(\"" + line + "\") == Seq(" + scalaList(listOr(esperado, "codigos")) + "))";
            }
            blocks.add("  test(\"" + title + "\") {\n" + body + "\n  }");
            if (esperado.containsKey("duplicado")) {
                dupLines.add(line);
                dupFlags.add(Py.truthy(esperado.get("duplicado")));
            }
        }
        if (!dupLines.isEmpty()) {
            int kept = dupFlags.indexOf(false);
            String lines = dupLines.stream().map(l -> "\"" + l + "\"").collect(Collectors.joining(", "));
            String delim = scalaStr((String) map(spec.get("archivo")).get("delimitador"));
            blocks.add("  test(\"fixture duplicados: misma clave y se conserva el más reciente (ASSUMPTION F-14)\") {\n"
                    + "    val grupo = Seq(" + lines + ")\n"
                    + "    val claves = grupo.map(l => RecordValidator.claveDuplicado(spec, Campos.separar(l, \"" + delim + "\")).toSeq)\n"
                    + "    assert(claves.distinct.size == 1)\n"
                    + "    val fechas = grupo.map(l => RecordValidator.asOfDate(spec, l).getOrElse(0L))\n"
                    + "    val candidatos = fechas.zipWithIndex.map { case (f, i) => (f, \"%05d\".format(i)) }\n"
                    + "    assert(RecordValidator.indiceAConservar(candidatos) == " + kept + ")\n"
                    + "  }");
        }
        return String.join("\n\n", blocks);
    }

    static String renderCatalogScala(Map<String, Object> spec) {
        List<String> tables = new ArrayList<>();
        for (Object o : list(spec.get("catalogos"))) {
            Map<String, Object> cat = map(o);
            List<String> cols = strings(cat.get("columnas"));
            List<String> rows = new ArrayList<>();
            for (Object r : list(cat.get("filas_sinteticas"))) {
                List<String> row = strings(r);
                List<String> pairs = new ArrayList<>();
                for (int i = 0; i < Math.min(cols.size(), row.size()); i++) {
                    pairs.add("\"" + scalaStr(cols.get(i)) + "\" -> \"" + scalaStr(row.get(i)) + "\"");
                }
                rows.add("        Map(" + String.join(", ", pairs) + ")");
            }
            tables.add(marcasSupuesto(cat, "      ") + "      \"" + scalaStr((String) cat.get("tabla")) + "\" -> Seq(\n" + String.join(",\n", rows) + "\n      )");
        }
        return String.join(",\n", tables);
    }

    static String renderErrorCodes(Map<String, Object> spec) {
        List<String> rows = new ArrayList<>();
        for (Object o : list(spec.get("campos"))) {
            Map<String, Object> c = map(o);
            rows.add(c.get("codigo") + ";" + c.get("severidad") + ";" + c.get("nombre") + ";Campo " + c.get("nombre") + " inválido");
        }
        for (Object o : list(spec.get("reglas_entre_campos"))) {
            Map<String, Object> r = map(o);
            Object campo = Py.truthy(r.get("campo")) ? r.get("campo") : r.get("campo_nombre");
            rows.add(r.get("codigo") + ";" + r.get("severidad") + ";" + Py.str(campo) + ";Regla " + r.get("id"));
        }
        Map<String, Object> especiales = map(spec.get("codigos_especiales"));
        rows.add(especiales.get("nombre_archivo") + ";1;FILENAME;Nombre de archivo inválido");
        rows.add(especiales.get("header") + ";1;HEADER;Header inválido");
        rows.add(especiales.get("estructura") + ";1;REGISTRO;Número de columnas distinto al layout");
        rows.add(especiales.get("interno") + ";1;REGISTRO;Error interno al validar el registro");
        // un código puede venir de un campo y de una regla (p. ej. correspondencia DANE)
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> unique = new ArrayList<>();
        for (String row : rows) {
            if (seen.add(row.split(";", 2)[0])) {
                unique.add(row);
            }
        }
        return String.join("\n", unique);
    }

    // ------------------------------------------------------------------------------------- contexto

    public static Map<String, String> build(Map<String, Object> spec) {
        Map<String, Object> f = map(spec.get("fuente"));
        Map<String, Object> archivo = map(spec.get("archivo"));
        Map<String, Object> dup = map(spec.get("duplicados"));
        Map<String, Map<String, Object>> fields = Verificaciones.fieldByName(spec);
        Map<String, Object> paquetes = map(spec.get("paquetes"));
        Map<String, Object> nombre = map(archivo.get("nombre"));
        Map<String, Object> grupos = map(nombre.get("grupos"));
        Map<String, Object> fixture = map(spec.get("fixture"));
        Map<String, Object> especiales = map(spec.get("codigos_especiales"));
        Map<String, Object> diagArchivo = map(spec.get("diagnostico_archivo"));
        List<Object> campos = list(spec.get("campos"));

        String paqueteFuente = paquetes.get("base_fuente") + "." + f.get("fuente");
        String delim = (String) archivo.get("delimitador");
        Map<String, Object> filtro = map(spec.get("filtro_filas"));
        Object filtroPos = Py.truthy(filtro.get("campo")) ? fields.get(filtro.get("campo")).get("posicion") : null;
        String header = campos.stream().map(c -> (String) map(c).get("nombre")).collect(Collectors.joining(delim));
        String[] ref = ((String) fixture.get("fecha_referencia")).split("-");
        String footerExpr = ((Number) archivo.get("filas_footer")).intValue() == 0 ? "false"
                : "Try(Option(row).exists(_.trim.matches(\"" + scalaStr((String) archivo.get("footer_regex"))
                + "\"))).getOrElse(false)";
        boolean ultimoDia = "ultimo_dia_periodo".equals(nombre.get("file_as_of_date"));
        String dedup = Map.of("EXTN", "EXTN", "RANDOM", "RANDOM", "AS-IS", "").get(dup.get("modo"));
        if (dedup == null) {
            throw new SpecError("duplicados.modo desconocido: " + dup.get("modo"));
        }
        List<String> filtroDesc = new ArrayList<>();
        if (Py.truthy(filtro.get("lineas_vacias"))) {
            filtroDesc.add("líneas vacías");
        }
        if (Py.truthy(filtroPos)) {
            filtroDesc.add(filtro.get("campo") + " en {" + String.join(", ", strings(filtro.get("valores"))) + "}");
        }
        List<String> clave = strings(dup.get("clave"));

        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("spec_version", Py.str(spec.get("spec_version")));
        ctx.put("Fuente", (String) f.get("Fuente"));
        ctx.put("fuente", (String) f.get("fuente"));
        ctx.put("event_type", Py.str(f.get("event_type")));
        ctx.put("datasource", scalaStr((String) f.get("datasource")));
        ctx.put("subscriber", scalaStr((String) f.get("subscriber")));
        ctx.put("zona_horaria", scalaStr((String) f.get("zona_horaria")));
        ctx.put("paquete_comun", (String) paquetes.get("comun"));
        ctx.put("paquete_fuente", paqueteFuente);
        ctx.put("ruta_comun", ((String) paquetes.get("comun")).replace(".", "/"));
        ctx.put("ruta_fuente", paqueteFuente.replace(".", "/"));
        ctx.put("processor_fqcn", paqueteFuente + ".processor." + f.get("Fuente") + "ValidationProcessor");
        ctx.put("file_format", (String) archivo.get("formato"));
        ctx.put("delimitador_scala", scalaStr(delim));
        ctx.put("filas_header", Py.str(archivo.get("filas_header")));
        ctx.put("filas_footer", Py.str(archivo.get("filas_footer")));
        ctx.put("is_footer_row_expr", footerExpr);
        ctx.put("linea_encoding", Py.truthy(archivo.get("encoding"))
                ? "  encoding = \"" + hoconStr(archivo.get("encoding")) + "\""
                : "  # sin clave encoding: ruta textFile UTF-8 (D5, F-17)");
        ctx.put("file_pattern_hocon", hoconStr(archivo.get("file_pattern")));
        ctx.put("regex_nombre_scala", scalaStr((String) nombre.get("regex")));
        ctx.put("grupo_prefijo", Py.str(grupos.get("prefijo")));
        ctx.put("grupo_periodo", Py.str(grupos.get("periodo")));
        ctx.put("grupo_marca_tiempo", Py.str(grupos.get("marca_tiempo")));
        ctx.put("grupo_offset", Py.str(grupos.get("offset")));
        ctx.put("bloque_file_tags", map(nombre.get("file_tag_por_prefijo")).entrySet().stream()
                .map(e -> "\"" + scalaStr(e.getKey()) + "\" -> \"" + scalaStr((String) e.getValue()) + "\"")
                .collect(Collectors.joining(", ")));
        ctx.put("expr_file_as_of_date", ultimoDia ? "i.periodo.atEndOfMonth" : "i.periodo.atDay(1)");
        ctx.put("file_as_of_date_desc", ultimoDia ? "último día del periodo AAAAMM" : "primer día del periodo AAAAMM");
        ctx.put("received_date_desc", "marca de tiempo del nombre con su offset");
        ctx.put("campo_subscriber_relation", Py.str(map(spec.get("guard_rails")).get("subscriber_relation")));
        ctx.put("campo_as_of_date", Py.str(map(spec.get("guard_rails")).get("as_of_date")));
        ctx.put("bloque_constantes_campos", campos.stream()
                .map(c -> "  val " + map(c).get("nombre") + ": String = \"" + map(c).get("nombre") + "\"")
                .collect(Collectors.joining("\n")));
        ctx.put("bloque_fieldspecs", campos.stream().map(c -> renderFieldspec(map(c))).collect(Collectors.joining(",\n")));
        ctx.put("bloque_reglas", list(spec.get("reglas_entre_campos")).stream().map(r -> renderRegla(map(r)))
                .collect(Collectors.joining(",\n")));
        ctx.put("bloque_accesores", campos.stream()
                .map(c -> "  val " + camel((String) map(c).get("nombre")) + ": String = Campos.valor(valores, "
                        + Py.str(map(c).get("posicion")) + ")")
                .collect(Collectors.joining("\n")));
        ctx.put("bloque_mapa_json", campos.stream()
                .map(c -> "    m.put(\"" + map(c).get("nombre") + "\", " + camel((String) map(c).get("nombre")) + ")")
                .collect(Collectors.joining("\n")));
        ctx.put("filtro_lineas_vacias", lower(filtro.get("lineas_vacias")));
        ctx.put("filtro_posicion_scala", Py.truthy(filtroPos) ? "Some(" + Py.str(filtroPos) + ")" : "None");
        ctx.put("filtro_valores_scala", scalaList(listOr(filtro, "valores")));
        ctx.put("filtro_descripcion", filtroDesc.isEmpty() ? "sin filtro" : String.join("; ", filtroDesc));
        ctx.put("bloque_quality_scores", list(spec.get("quality_scores")).stream()
                .map(q -> "QualityScoreSpec(\"" + scalaStr((String) map(q).get("nombre")) + "\", Set("
                        + scalaList(list(map(q).get("anulan_niveles"))) + "))")
                .collect(Collectors.joining(", ")));
        ctx.put("dup_habilitado", lower(dup.get("habilitado")));
        ctx.put("dup_clave_scala", String.join(", ", clave));
        ctx.put("dup_campo_fecha_scala", Py.truthy(dup.get("campo_fecha")) ? "Some(" + dup.get("campo_fecha") + ")" : "None");
        ctx.put("dup_descripcion", clave.isEmpty() ? "sin clave" : String.join(" + ", clave) + ", normalizados");
        ctx.put("dup_campo_fecha_desc", Py.truthy(dup.get("campo_fecha")) ? Py.str(dup.get("campo_fecha")) : "sin fecha");
        ctx.put("dedup_version", dedup);
        ctx.put("linea_duplicate_threshold", Py.truthy(dup.get("umbral"))
                ? "  duplicateThreshold = " + Py.str(dup.get("umbral"))
                : "  # duplicateThreshold omitido: default Integer.MAX_VALUE (F-15)");
        ctx.put("default_output_structure", lower(map(spec.get("ambiente")).get("default_output_structure")));
        ctx.put("codigo_nombre_archivo", (String) especiales.get("nombre_archivo"));
        ctx.put("codigo_header", (String) especiales.get("header"));
        ctx.put("codigo_estructura", (String) especiales.get("estructura"));
        ctx.put("codigo_interno", (String) especiales.get("interno"));
        ctx.put("validate_file_name_hocon", hoconStr(diagArchivo.get("fileName")));
        ctx.put("validate_header_hocon", hoconStr(diagArchivo.get("header")));
        ctx.put("bloque_metricas", list(spec.get("metricas")).stream().map(m -> renderMetric(map(m)))
                .collect(Collectors.joining(",\n")));
        ctx.put("bloque_lookups", list(spec.get("catalogos")).stream().map(c -> renderLookup(map(c)))
                .collect(Collectors.joining(",\n")));
        ctx.put("bloque_codigos_error", renderErrorCodes(spec));
        ctx.put("bloque_catalogo_sintetico", renderCatalogScala(spec));
        ctx.put("registro_base_scala", scalaList(list(fixture.get("registro_base"))));
        ctx.put("header_linea", header);
        ctx.put("header_linea_scala", scalaStr(header));
        ctx.put("numero_campos", String.valueOf(campos.size()));
        ctx.put("fecha_referencia_scala", "LocalDate.of(" + Integer.parseInt(ref[0]) + ", " + Integer.parseInt(ref[1])
                + ", " + Integer.parseInt(ref[2]) + ")");
        ctx.put("fixture_nombre_archivo", (String) fixture.get("nombre_archivo"));
        ctx.put("bloque_registros_fixture", list(fixture.get("casos")).stream()
                .map(c -> buildFixtureLine(spec, map(c))).collect(Collectors.joining("\n")));
        ctx.put("bloque_casos_campo", renderFieldTests(spec));
        ctx.put("bloque_casos_fixture", renderFixtureTests(spec));
        return ctx;
    }
}

package com.experian.edf.skills.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Carga del source-spec y verificaciones: esquema (draft 2020-12) y reglas semánticas (códigos de longitud
 * fija y sin colisiones de subcadena, referencias de catálogo, guard-rails, contrato de archivo, fixture).
 */
public final class Verificaciones {

    static final String DUPLICATE_MARK = "OxygenDuplicate";
    static final List<String> DSL_KEYWORDS = List.of("errorcodeIn", "errorcodeNotIn", "errorcodeInList",
            "errorcodeNotInList", "errorlevelIn", "errorlevelNotIn", "errorlevelInList", "errorlevelNotInList",
            "count(errors)", "and", "or", "not", "true", "false", DUPLICATE_MARK);
    private static final Map<String, String> JAVA_DATE_TOKENS = new LinkedHashMap<>();

    static {
        JAVA_DATE_TOKENS.put("dd", "%d");
        JAVA_DATE_TOKENS.put("MM", "%m");
        JAVA_DATE_TOKENS.put("yyyy", "%Y");
        JAVA_DATE_TOKENS.put("HH", "%H");
        JAVA_DATE_TOKENS.put("mm", "%M");
        JAVA_DATE_TOKENS.put("ss", "%S");
    }

    private Verificaciones() {
    }

    // ------------------------------------------------------------------------------------- carga

    static String readText(Path path) {
        try {
            // Igual que open(..., encoding="utf-8-sig"): sin BOM y con \r\n y \r como \n.
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (text.startsWith("﻿")) {
                text = text.substring(1);
            }
            return text.replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Object loadYaml(Path path) {
        return new Yaml(new SafeConstructor(new LoaderOptions())).load(readText(path));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadSpec(Path path) {
        Object data = loadYaml(path);
        if (!(data instanceof Map)) {
            throw new SpecError(path.getFileName() + ": el source-spec debe ser un objeto YAML");
        }
        return (Map<String, Object>) data;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        return o == null ? List.of() : (List<Object>) o;
    }

    /** {@code d.get(key, [])}: la lista si la clave existe; vacía si no. */
    static List<Object> listOr(Map<String, Object> d, String key) {
        return list(d.get(key));
    }

    static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        list(o).forEach(v -> out.add((String) v));
        return out;
    }

    // ------------------------------------------------------------------------------------ utilidades

    /** Traduce un patrón de fecha Java (dd, MM, yyyy, HH, mm, ss) a strftime; falla con otra letra. */
    public static String javaToPyDate(String pattern) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            boolean matched = false;
            for (Map.Entry<String, String> e : JAVA_DATE_TOKENS.entrySet()) {
                if (pattern.startsWith(e.getKey(), i)) {
                    out.append(e.getValue());
                    i += e.getKey().length();
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                if (Character.isLetter(pattern.charAt(i))) {
                    throw new SpecError("formato de fecha no soportado: " + Py.repr(pattern));
                }
                out.append(pattern.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    /** Códigos de campos, reglas y especiales, sin repetidos y en orden de aparición. */
    public static List<String> allCodes(Map<String, Object> spec) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        list(spec.get("campos")).forEach(c -> codes.add((String) map(c).get("codigo")));
        list(spec.get("reglas_entre_campos")).forEach(r -> codes.add((String) map(r).get("codigo")));
        map(spec.get("codigos_especiales")).values().forEach(v -> codes.add((String) v));
        return new ArrayList<>(codes);
    }

    public static Map<String, Map<String, Object>> fieldByName(Map<String, Object> spec) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        list(spec.get("campos")).forEach(c -> out.put((String) map(c).get("nombre"), map(c)));
        return out;
    }

    // ---------------------------------------------------------------------- verificaciones semánticas

    static List<String> checkCodes(Map<String, Object> spec) {
        List<String> errors = new ArrayList<>();
        List<String> codes = allCodes(spec);
        TreeSet<Integer> lengths = new TreeSet<>();
        codes.forEach(c -> lengths.add(c.length()));
        if (lengths.size() != 1) {
            errors.add("los códigos de error deben tener longitud fija; longitudes encontradas: " + Py.str(lengths));
        }
        codes.stream().filter(c -> c.length() == 11)
                .forEach(c -> errors.add("código de 11 caracteres (lo reescribe decodeErrorObject): " + c));
        for (String a : codes) {
            for (String b : codes) {
                if (!a.equals(b) && b.contains(a)) {
                    errors.add("colisión de subcadena en el DSL: " + a + " está dentro de " + b);
                }
            }
            if (DUPLICATE_MARK.contains(a) || a.contains(DUPLICATE_MARK)) {
                errors.add("colisión de subcadena con " + DUPLICATE_MARK + ": " + a);
            }
        }
        List<Object> fieldCodes = new ArrayList<>();
        list(spec.get("campos")).forEach(c -> fieldCodes.add(map(c).get("codigo")));
        if (fieldCodes.size() != new HashSet<>(fieldCodes).size()) {
            errors.add("cada campo debe tener un código propio (F-10)");
        }
        return errors;
    }

    static List<String> checkQualityScores(Map<String, Object> spec) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> diag = map(spec.get("diagnostico_archivo"));
        List<String> texts = List.of((String) diag.get("fileName"), (String) diag.get("header"),
                (String) map(spec.get("handoff_acceptance")).get("record_acceptance"));
        List<String> codes = allCodes(spec);
        for (Object qs : list(spec.get("quality_scores"))) {
            String name = (String) map(qs).get("nombre");
            if (DSL_KEYWORDS.stream().anyMatch(kw -> kw.contains(name)) || codes.stream().anyMatch(c -> c.contains(name))) {
                errors.add("el nombre de quality score " + Py.repr(name) + " aparece dentro de un operador o código del DSL");
            }
            if (texts.stream().anyMatch(t -> t.contains(name))) {
                errors.add("el nombre de quality score " + Py.repr(name) + " aparece en una expresión de registro/archivo");
            }
        }
        return errors;
    }

    static final Pattern QS_REF = Pattern.compile("qs\\.([A-Za-z0-9_]+)");

    /** AC-VAL-54a: cada referencia {@code qs.<X>} de metricas[].expr nombra un quality score declarado. */
    static List<String> checkMetrics(Map<String, Object> spec) {
        List<String> errors = new ArrayList<>();
        Set<Object> declarados = new HashSet<>();
        list(spec.get("quality_scores")).forEach(q -> declarados.add(map(q).get("nombre")));
        for (Object o : list(spec.get("metricas"))) {
            Map<String, Object> metrica = map(o);
            Set<String> usados = new LinkedHashSet<>();
            Matcher m = QS_REF.matcher((String) metrica.get("expr"));
            while (m.find()) {
                usados.add(m.group(1));
            }
            usados.stream().filter(x -> !declarados.contains(x)).forEach(x -> errors.add(
                    "métrica " + metrica.get("id") + " usa el quality score no declarado " + x + " (AC-VAL-54a)"));
        }
        return errors;
    }

    static List<String> checkFields(Map<String, Object> spec) {
        List<String> errors = new ArrayList<>();
        List<Object> campos = list(spec.get("campos"));
        List<Integer> positions = new ArrayList<>();
        campos.forEach(c -> positions.add(((Number) map(c).get("posicion")).intValue()));
        List<Integer> sorted = new ArrayList<>(positions);
        sorted.sort(null);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) != i + 1) {
                errors.add("las posiciones de los campos deben ser 1..N sin huecos ni repetidos");
                break;
            }
        }
        List<Object> names = new ArrayList<>();
        campos.forEach(c -> names.add(map(c).get("nombre")));
        if (names.size() != new HashSet<>(names).size()) {
            errors.add("nombres de campo repetidos");
        }
        for (Object o : campos) {
            Map<String, Object> c = map(o);
            if ("fecha".equals(c.get("tipo")) && !Py.truthy(c.get("formatos_fecha"))) {
                errors.add(c.get("nombre") + ": un campo fecha requiere formatos_fecha");
            }
            for (Object fmt : listOr(c, "formatos_fecha")) {
                try {
                    javaToPyDate((String) fmt);
                } catch (SpecError exc) {
                    errors.add(c.get("nombre") + ": " + exc.getMessage());
                }
            }
            if (Py.truthy(c.get("obligatorio")) != "1".equals(c.get("severidad"))) {
                errors.add(c.get("nombre") + ": obligatorio ⇒ severidad 1 y opcional ⇒ severidad 3");
            }
        }
        return errors;
    }

    static List<String> checkReferences(Map<String, Object> spec) {
        List<String> errors = new ArrayList<>();
        Map<String, Map<String, Object>> fields = fieldByName(spec);
        Map<String, Map<String, Object>> catalogs = new LinkedHashMap<>();
        list(spec.get("catalogos")).forEach(c -> catalogs.put((String) map(c).get("tabla"), map(c)));

        interface NeedField {
            void check(Object name, String where, String tipo);
        }
        NeedField needField = (name, where, tipo) -> {
            if (name == null) {
                return;
            }
            if (!fields.containsKey(name)) {
                errors.add(where + ": campo inexistente " + name);
            } else if (tipo != null && !tipo.equals(fields.get(name).get("tipo"))) {
                errors.add(where + ": " + name + " debe ser de tipo " + tipo);
            }
        };
        interface NeedColumn {
            void check(Object tabla, Object columna, String where, boolean key);
        }
        NeedColumn needColumn = (tabla, columna, where, key) -> {
            Map<String, Object> cat = catalogs.get(tabla);
            if (cat == null) {
                errors.add(where + ": tabla " + tabla + " no declarada en catalogos");
            } else if (!list(cat.get("columnas")).contains(columna)) {
                errors.add(where + ": columna " + columna + " no existe en " + tabla);
            } else if (key && !list(cat.get("keys")).contains(columna)) {
                errors.add(where + ": columna " + columna + " debe estar en keys de " + tabla + " (consulta por lookup)");
            }
        };

        for (Object o : list(spec.get("campos"))) {
            Map<String, Object> c = map(o);
            if (c.containsKey("catalogo")) {
                Map<String, Object> k = map(c.get("catalogo"));
                needColumn.check(k.get("tabla"), k.get("columna"), (String) c.get("nombre"), true);
            }
        }
        for (Object o : list(spec.get("reglas_entre_campos"))) {
            Map<String, Object> r = map(o);
            String id = (String) r.get("id");
            if ("correspondencia_catalogo".equals(r.get("tipo"))) {
                needField.check(r.get("campo_codigo"), id, null);
                needField.check(r.get("campo_nombre"), id, null);
                needColumn.check(r.get("tabla"), r.get("columna_clave"), id, true);
                needColumn.check(r.get("tabla"), r.get("columna_nombre"), id, false);
            } else {
                needField.check(r.get("campo"), id, null);
            }
        }
        for (Object o : list(spec.get("catalogos"))) {
            Map<String, Object> cat = map(o);
            List<Object> columnas = list(cat.get("columnas"));
            for (Object key : list(cat.get("keys"))) {
                if (!columnas.contains(key)) {
                    errors.add(cat.get("tabla") + ": key " + key + " no es una columna");
                }
            }
            for (Object row : list(cat.get("filas_sinteticas"))) {
                int n = list(row).size();
                if (n != columnas.size()) {
                    errors.add(cat.get("tabla") + ": fila sintética con " + n + " valores (esperado " + columnas.size() + ")");
                }
            }
        }
        Map<String, Object> gr = map(spec.get("guard_rails"));
        needField.check(gr.get("subscriber_relation"), "guard_rails.subscriber_relation", null);
        needField.check(gr.get("as_of_date"), "guard_rails.as_of_date", "fecha");
        needField.check(map(spec.get("filtro_filas")).get("campo"), "filtro_filas.campo", null);
        Map<String, Object> dup = map(spec.get("duplicados"));
        if (Py.truthy(dup.get("habilitado"))) {
            if (!Py.truthy(dup.get("clave"))) {
                errors.add("duplicados.habilitado exige una clave no vacía (assert(false) del motor)");
            }
            for (Object name : list(dup.get("clave"))) {
                needField.check(name, "duplicados.clave", null);
            }
            if ("EXTN".equals(dup.get("modo"))) {
                needField.check(dup.get("campo_fecha"), "duplicados.campo_fecha", "fecha");
            }
        }
        return errors;
    }

    static List<String> checkFileContract(Map<String, Object> spec) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> archivo = map(spec.get("archivo"));
        if (((Number) archivo.get("filas_footer")).intValue() > 0 && !Py.truthy(archivo.get("footer_regex"))) {
            errors.add("filas_footer > 0 exige footer_regex");
        }
        Map<String, Object> nombre = map(archivo.get("nombre"));
        Pattern nameRe;
        Pattern patternRe;
        try {
            nameRe = Pattern.compile((String) nombre.get("regex"));
            int groups = nameRe.matcher("").groupCount();
            for (Map.Entry<String, Object> g : map(nombre.get("grupos")).entrySet()) {
                int idx = ((Number) g.getValue()).intValue();
                if (idx > groups) {
                    errors.add("archivo.nombre.grupos." + g.getKey() + " = " + idx + " excede los grupos del regex");
                }
            }
            patternRe = Pattern.compile((String) archivo.get("file_pattern"));
        } catch (PatternSyntaxException exc) {
            errors.add("regex inválido: " + exc.getDescription());
            return errors;
        }
        String fixtureName = (String) map(spec.get("fixture")).get("nombre_archivo");
        if (!nameRe.matcher(fixtureName).matches()) {
            errors.add("fixture.nombre_archivo no cumple archivo.nombre.regex");
        }
        if (!patternRe.matcher("/ruta/sintetica/" + fixtureName).matches()) {
            errors.add("fixture.nombre_archivo no cumple archivo.file_pattern (ruta completa)");
        }
        if (Pattern.compile("[\"\\\\/]").matcher(fixtureName).find()) {
            errors.add("fixture.nombre_archivo no puede contener comillas, barras ni rutas");
        }
        return errors;
    }

    static List<String> checkFixture(Map<String, Object> spec) {
        List<String> errors = new ArrayList<>();
        Map<String, Map<String, Object>> fields = fieldByName(spec);
        Map<String, Object> fixture = map(spec.get("fixture"));
        if (list(fixture.get("registro_base")).size() != list(spec.get("campos")).size()) {
            errors.add("fixture.registro_base debe tener un valor por campo");
        }
        List<String> codes = allCodes(spec);
        List<Object> ids = new ArrayList<>();
        for (Object o : list(fixture.get("casos"))) {
            Map<String, Object> caso = map(o);
            ids.add(caso.get("id"));
            Object cambios = caso.get("cambios");
            if (Py.truthy(cambios)) {
                for (String name : map(cambios).keySet()) {
                    if (!fields.containsKey(name)) {
                        errors.add("fixture " + caso.get("id") + ": campo inexistente " + name);
                    }
                }
            }
            for (Object code : listOr(map(caso.get("esperado")), "codigos")) {
                if (!codes.contains(code)) {
                    errors.add("fixture " + caso.get("id") + ": código esperado no declarado " + code);
                }
            }
        }
        if (ids.size() != new HashSet<>(ids).size()) {
            errors.add("ids de casos del fixture repetidos");
        }
        return errors;
    }

    /** Errores de la spec contra el JSON Schema (draft 2020-12), ordenados por ruta. */
    public static List<String> validateSchema(Map<String, Object> spec, Path schemaPath) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemaNode = mapper.valueToTree(loadYaml(schemaPath));
        JsonSchema validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
        List<String[]> found = new ArrayList<>();
        for (ValidationMessage msg : validator.validate(mapper.valueToTree(spec))) {
            // "$.campos[3].severidad" -> "campos/3/severidad"
            String path = msg.getInstanceLocation().toString().replaceFirst("^\\$\\.?", "")
                    .replaceAll("\\[(\\d+)]", ".$1").replace('.', '/');
            found.add(new String[] {path, (path.isEmpty() ? "(raíz)" : path) + ": " + msg.getError()});
        }
        found.sort((a, b) -> a[0].compareTo(b[0]));
        List<String> out = new ArrayList<>();
        found.forEach(f -> out.add(f[1]));
        return out;
    }

    /** Esquema + reglas semánticas. Devuelve todos los errores encontrados. */
    public static List<String> verifySpec(Map<String, Object> spec, Path schemaPath) {
        List<String> errors = validateSchema(spec, schemaPath);
        if (!errors.isEmpty()) {
            return errors; // sin forma válida, las reglas semánticas no son confiables
        }
        errors = new ArrayList<>();
        errors.addAll(checkFields(spec));
        errors.addAll(checkCodes(spec));
        errors.addAll(checkQualityScores(spec));
        errors.addAll(checkMetrics(spec));
        errors.addAll(checkReferences(spec));
        errors.addAll(checkFileContract(spec));
        errors.addAll(checkFixture(spec));
        return errors;
    }
}

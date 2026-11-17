package com.experian.edf.skills.validation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Semántica de Python de la que depende la salida generada: veracidad, {@code str()}, {@code repr()} y
 * {@code json.dumps(..., ensure_ascii=False)} con y sin {@code indent}. Los valores vienen de SnakeYAML
 * (Map, List, String, Boolean, Integer, Long, BigInteger, Double o null), igual que de PyYAML antes.
 */
final class Py {

    private Py() {
    }

    /** Veracidad de Python: null, false, 0, 0.0, "", [] y {} son falsos. */
    static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return !s.isEmpty();
        }
        if (v instanceof Double d) {
            return d != 0.0;
        }
        if (v instanceof BigInteger bi) {
            return bi.signum() != 0;
        }
        if (v instanceof Number n) {
            return n.longValue() != 0;
        }
        if (v instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (v instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        return true;
    }

    static boolean isInteger(Object v) {
        return v instanceof Integer || v instanceof Long || v instanceof BigInteger
                || v instanceof Short || v instanceof Byte;
    }

    /** {@code str(v)}. */
    static String str(Object v) {
        if (v == null) {
            return "None";
        }
        if (v instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (v instanceof Double d) {
            return floatRepr(d);
        }
        if (v instanceof Collection<?> l) {
            return l.stream().map(Py::repr).collect(Collectors.joining(", ", "[", "]"));
        }
        return v.toString();
    }

    /** {@code repr(v)}: las cadenas van entre comillas simples (dobles si contienen una simple). */
    static String repr(Object v) {
        if (v instanceof String s) {
            if (s.contains("'") && !s.contains("\"")) {
                return "\"" + s.replace("\\", "\\\\") + "\"";
            }
            return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        return str(v);
    }

    /** {@code repr(float)}: dígitos mínimos; notación exponencial fuera de [1e-4, 1e16). */
    static String floatRepr(double d) {
        if (Double.isNaN(d)) {
            return "nan";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "inf" : "-inf";
        }
        if (d == 0.0) {
            return (1.0 / d < 0) ? "-0.0" : "0.0";
        }
        BigDecimal bd = new BigDecimal(Double.toString(d)).stripTrailingZeros();
        String digits = bd.unscaledValue().abs().toString();
        int exp = digits.length() - 1 - bd.scale();
        String sign = d < 0 ? "-" : "";
        if (exp >= -4 && exp < 16) {
            String plain = bd.abs().toPlainString();
            return sign + (plain.contains(".") ? plain : plain + ".0");
        }
        String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        return sign + mantissa + "e" + (exp < 0 ? "-" : "+") + String.format("%02d", Math.abs(exp));
    }

    /** {@code json.dumps(v, ensure_ascii=False)} con los separadores por defecto (", " y ": "). */
    static String jsonDumps(Object v) {
        StringBuilder sb = new StringBuilder();
        writeJson(v, sb, -1, 0);
        return sb.toString();
    }

    /** {@code json.dumps(v, ensure_ascii=False, indent=n)}. */
    static String jsonDumps(Object v, int indent) {
        StringBuilder sb = new StringBuilder();
        writeJson(v, sb, indent, 0);
        return sb.toString();
    }

    private static void newline(StringBuilder sb, int indent, int level) {
        sb.append('\n').append(" ".repeat(indent * level));
    }

    private static void writeJson(Object v, StringBuilder sb, int indent, int level) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Double d) {
            sb.append(Double.isNaN(d) ? "NaN" : Double.isInfinite(d) ? (d > 0 ? "Infinity" : "-Infinity") : floatRepr(d));
        } else if (isInteger(v)) {
            sb.append(v);
        } else if (v instanceof String s) {
            writeJsonString(s, sb);
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    sb.append(indent < 0 ? ", " : ",");
                }
                if (indent >= 0) {
                    newline(sb, indent, level + 1);
                }
                writeJson(l.get(i), sb, indent, level + 1);
            }
            if (indent >= 0) {
                newline(sb, indent, level);
            }
            sb.append(']');
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(indent < 0 ? ", " : ",");
                }
                first = false;
                if (indent >= 0) {
                    newline(sb, indent, level + 1);
                }
                writeJsonString(jsonKey(e.getKey()), sb);
                sb.append(": ");
                writeJson(e.getValue(), sb, indent, level + 1);
            }
            if (indent >= 0) {
                newline(sb, indent, level);
            }
            sb.append('}');
        } else {
            throw new IllegalArgumentException("valor no serializable a JSON: " + v.getClass().getSimpleName());
        }
    }

    private static String jsonKey(Object k) {
        if (k instanceof String s) {
            return s;
        }
        if (k == null) {
            return "null";
        }
        if (k instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (k instanceof Double d) {
            return floatRepr(d);
        }
        return k.toString();
    }

    private static void writeJsonString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}

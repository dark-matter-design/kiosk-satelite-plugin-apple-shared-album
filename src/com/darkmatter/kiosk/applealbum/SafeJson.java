package com.darkmatter.kiosk.applealbum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small bounded JSON codec used to avoid a runtime dependency. */
final class SafeJson {
    private SafeJson() {}

    static Object parse(byte[] bytes) throws AlbumException {
        if (bytes == null || bytes.length > SafeHttp.MAX_METADATA_BYTES) throw new AlbumException(AlbumException.Code.TOO_LARGE);
        try {
            String text = new String(bytes, "UTF-8");
            Parser parser = new Parser(text);
            Object value = parser.value(0);
            parser.space();
            if (parser.at != text.length()) throw new IllegalArgumentException();
            return value;
        } catch (AlbumException error) {
            throw error;
        } catch (Exception error) {
            throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    static String string(Map<String, Object> object, String key) {
        Object value = object == null ? null : object.get(key);
        return value instanceof String ? (String) value : null;
    }

    static boolean bool(Map<String, Object> object, String key) {
        return object != null && Boolean.TRUE.equals(object.get(key));
    }

    static String encode(Object value) throws AlbumException {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, int depth) throws AlbumException {
        if (depth > 24 || out.length() > SafeHttp.MAX_METADATA_BYTES) throw new AlbumException(AlbumException.Code.TOO_LARGE);
        if (value == null) { out.append("null"); return; }
        if (value instanceof String) { quote((String) value, out); return; }
        if (value instanceof Boolean || value instanceof Number) { out.append(value); return; }
        if (value instanceof Map) {
            out.append('{'); boolean first = true;
            for (Object entryObject : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                if (!(entry.getKey() instanceof String)) throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
                if (!first) out.append(','); first = false;
                quote((String) entry.getKey(), out); out.append(':'); write(entry.getValue(), out, depth + 1);
            }
            out.append('}'); return;
        }
        if (value instanceof Iterable) {
            out.append('['); boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) out.append(','); first = false;
                write(item, out, depth + 1);
            }
            out.append(']'); return;
        }
        throw new AlbumException(AlbumException.Code.INVALID_RESPONSE);
    }

    private static void quote(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c);
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int at;
        private int nodes;

        Parser(String text) { this.text = text; }

        Object value(int depth) throws AlbumException {
            if (depth > 24 || ++nodes > 20000) throw new AlbumException(AlbumException.Code.TOO_LARGE);
            space();
            if (at >= text.length()) throw new IllegalArgumentException();
            char c = text.charAt(at);
            if (c == '{') return object(depth + 1);
            if (c == '[') return array(depth + 1);
            if (c == '"') return string();
            if (text.startsWith("true", at)) { at += 4; return Boolean.TRUE; }
            if (text.startsWith("false", at)) { at += 5; return Boolean.FALSE; }
            if (text.startsWith("null", at)) { at += 4; return null; }
            return number();
        }

        Map<String, Object> object(int depth) throws AlbumException {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>(); at++; space();
            if (take('}')) return result;
            do {
                space(); if (at >= text.length() || text.charAt(at) != '"') throw new IllegalArgumentException();
                String key = string(); space(); if (!take(':')) throw new IllegalArgumentException();
                result.put(key, value(depth)); space();
                if (take('}')) return result;
            } while (take(','));
            throw new IllegalArgumentException();
        }

        List<Object> array(int depth) throws AlbumException {
            ArrayList<Object> result = new ArrayList<>(); at++; space();
            if (take(']')) return result;
            do {
                result.add(value(depth)); space();
                if (take(']')) return result;
            } while (take(','));
            throw new IllegalArgumentException();
        }

        String string() {
            StringBuilder out = new StringBuilder(); at++;
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c != '\\') { if (c < 0x20) throw new IllegalArgumentException(); out.append(c); continue; }
                if (at >= text.length()) throw new IllegalArgumentException();
                char e = text.charAt(at++);
                switch (e) {
                    case '"': case '\\': case '/': out.append(e); break;
                    case 'b': out.append('\b'); break; case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break; case 'r': out.append('\r'); break; case 't': out.append('\t'); break;
                    case 'u':
                        if (at + 4 > text.length()) throw new IllegalArgumentException();
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); at += 4; break;
                    default: throw new IllegalArgumentException();
                }
            }
            throw new IllegalArgumentException();
        }

        Number number() {
            int start = at;
            while (at < text.length() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            String value = text.substring(start, at);
            if (value.isEmpty() || value.length() > 64) throw new IllegalArgumentException();
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) throw new IllegalArgumentException();
            return parsed;
        }

        void space() { while (at < text.length() && " \n\r\t".indexOf(text.charAt(at)) >= 0) at++; }
        boolean take(char expected) { if (at < text.length() && text.charAt(at) == expected) { at++; return true; } return false; }
    }
}

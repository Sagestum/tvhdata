/*
 * Copyright (c) Henrik Niehaus
 * Copyright (c) 2026 Christian Echterling
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the project (Lazy Bones) nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package tvhdata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser and writer for the tvheadend HTTP API.
 *
 * Objects are mapped to {@link Map}, arrays to {@link List}, numbers to {@link Long} or {@link Double}, and true/false/null to {@link Boolean} and null.
 */
public class Json {

    private final String s;
    private int pos;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String json) {
        Json parser = new Json(json);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos < parser.s.length()) {
            throw parser.error("Unexpected trailing characters");
        }
        return value;
    }

    private Object readValue() {
        if (pos >= s.length()) {
            throw error("Unexpected end of input");
        }
        char c = s.charAt(pos);
        switch (c) {
        case '{':
            return readObject();
        case '[':
            return readArray();
        case '"':
            return readString();
        case 't':
            expect("true");
            return Boolean.TRUE;
        case 'f':
            expect("false");
            return Boolean.FALSE;
        case 'n':
            expect("null");
            return null;
        default:
            return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            if (peek() != ':') {
                throw error("Expected ':'");
            }
            pos++;
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == '}') {
                return map;
            } else if (c != ',') {
                throw error("Expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == ']') {
                return list;
            } else if (c != ',') {
                throw error("Expected ',' or ']'");
            }
        }
    }

    private String readString() {
        if (peek() != '"') {
            throw error("Expected '\"'");
        }
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= s.length()) {
                throw error("Unterminated string");
            }
            char c = s.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            } else if (c == '\\') {
                char esc = s.charAt(pos++);
                switch (esc) {
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default:
                    sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        boolean decimal = false;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '.' || c == 'e' || c == 'E') {
                decimal = true;
            } else if (!(c == '-' || c == '+' || Character.isDigit(c))) {
                break;
            }
            pos++;
        }
        String number = s.substring(start, pos);
        if (number.isEmpty()) {
            throw error("Unexpected character");
        }
        try {
            return decimal ? (Object) Double.valueOf(number) : (Object) Long.valueOf(number);
        } catch (NumberFormatException e) {
            return Double.valueOf(number);
        }
    }

    private void expect(String word) {
        if (!s.startsWith(word, pos)) {
            throw error("Expected " + word);
        }
        pos += word.length();
    }

    private char peek() {
        if (pos >= s.length()) {
            throw error("Unexpected end of input");
        }
        return s.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException(msg + " at position " + pos);
    }

    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            writeString(sb, (String) o);
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map) {
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = ((Map<?, ?>) o).entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> entry = it.next();
                writeString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                write(sb, entry.getValue());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append('}');
        } else if (o instanceof Iterable) {
            sb.append('[');
            Iterator<?> it = ((Iterable<?>) o).iterator();
            while (it.hasNext()) {
                write(sb, it.next());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append(']');
        } else {
            writeString(sb, o.toString());
        }
    }

    private static void writeString(StringBuilder sb, String str) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
            case '"':
                sb.append("\\\"");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            default:
                if (c < 0x20) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ####################### helpers to read values from parsed objects ###########################

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    public static String getString(Map<String, Object> map, String key) {
        Object o = map.get(key);
        return o == null ? "" : o.toString();
    }

    public static long getLong(Map<String, Object> map, String key) {
        Object o = map.get(key);
        if (o instanceof Number) {
            return ((Number) o).longValue();
        } else if (o instanceof String) {
            try {
                return Long.parseLong((String) o);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public static int getInt(Map<String, Object> map, String key) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, getLong(map, key)));
    }

    public static boolean getBoolean(Map<String, Object> map, String key) {
        Object o = map.get(key);
        if (o instanceof Boolean) {
            return (Boolean) o;
        } else if (o instanceof Number) {
            return ((Number) o).intValue() != 0;
        }
        return false;
    }
}

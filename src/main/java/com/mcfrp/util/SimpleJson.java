package com.mcfrp.util;

import com.mcfrp.McFrpClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量级 JSON 工具类
 * 仅支持简单的对象/数组/字符串/数字结构，满足本模组 P2P 条目持久化需求
 */
public final class SimpleJson {

    private SimpleJson() {}

    /** 将字符串转义为 JSON 字符串字面量 */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /** 将 P2PEntry 列表序列化为 JSON 数组字符串 */
    public static String entriesToJson(List<P2PEntry> entries) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('[');
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(',');
            P2PEntry e = entries.get(i);
            sb.append("{\"serverName\":\"").append(escape(e.serverName)).append('"');
            sb.append(",\"frpsAddr\":\"").append(escape(e.frpsAddr)).append('"');
            sb.append(",\"frpsPort\":").append(e.frpsPort);
            sb.append(",\"secretKey\":\"").append(escape(e.secretKey)).append('"');
            if (e.localPort != 0) sb.append(",\"localPort\":").append(e.localPort);
            sb.append(",\"lastPing\":").append(e.lastPing);
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    /** 从 JSON 数组字符串反序列化 P2PEntry 列表 */
    public static List<P2PEntry> entriesFromJson(String text) {
        List<P2PEntry> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        String t = text.trim();
        if (t.equals("[]") || t.equalsIgnoreCase("null") || t.equalsIgnoreCase("undefined")) return result;

        Parser p = new Parser(t);
        try {
            p.skipWs();
            p.expect('[');
            while (true) {
                p.skipWs();
                if (p.peek() == ']') { p.next(); break; }
                if (result.size() > 0) {
                    p.skipWs();
                    p.expect(',');
                    p.skipWs();
                }
                p.expect('{');
                String serverName = null, frpsAddr = null, secretKey = null;
                int frpsPort = 0, localPort = 0;
                long lastPing = 0;
                boolean first = true;
                while (true) {
                    p.skipWs();
                    if (p.peek() == '}') { p.next(); break; }
                    if (!first) { p.skipWs(); p.expect(','); }
                    first = false;
                    p.skipWs();
                    String key = p.readString();
                    p.skipWs();
                    p.expect(':');
                    p.skipWs();
                    switch (key) {
                        case "serverName": serverName = p.readString(); break;
                        case "frpsAddr":   frpsAddr = p.readString(); break;
                        case "secretKey":  secretKey = p.readString(); break;
                        case "frpsPort":   frpsPort = p.readInt(); break;
                        case "localPort":  localPort = p.readInt(); break;
                        case "lastPing":   lastPing = p.readLong(); break;
                        default: p.skipValue();
                    }
                }
                if (serverName != null && frpsAddr != null && secretKey != null) {
                    result.add(new P2PEntry(serverName, frpsAddr, frpsPort, secretKey, localPort, lastPing));
                }
            }
        } catch (ParseException e) {
            McFrpClient.LOGGER.warn("[MCFRP] P2P 条目解析失败: JSON 数据格式异常，已返回空列表", e);
        }
        return result;
    }

    /** P2P 条目数据 */
    public static class P2PEntry {
        public final String serverName;
        public final String frpsAddr;
        public final int frpsPort;
        public final String secretKey;
        public final int localPort;
        public final long lastPing;

        public P2PEntry(String serverName, String frpsAddr, int frpsPort, String secretKey, int localPort, long lastPing) {
            this.serverName = serverName;
            this.frpsAddr = frpsAddr;
            this.frpsPort = frpsPort;
            this.secretKey = secretKey;
            this.localPort = localPort;
            this.lastPing = lastPing;
        }
    }

    private static class ParseException extends RuntimeException {
        public ParseException(String msg) { super(msg); }
    }

    private static class Parser {
        private final String src;
        private int pos;

        Parser(String src) { this.src = src; this.pos = 0; }

        char peek() {
            if (pos >= src.length()) throw new ParseException("unexpected end");
            return src.charAt(pos);
        }

        char next() {
            if (pos >= src.length()) throw new ParseException("unexpected end");
            return src.charAt(pos++);
        }

        void expect(char c) {
            if (next() != c) throw new ParseException("expected " + c);
        }

        void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw new ParseException("bad unicode");
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw new ParseException("bad unicode");
                            }
                            break;
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new ParseException("unterminated string");
        }

        int readInt() { return (int) readNumber(true); }
        long readLong() { return readNumber(false); }

        long readNumber(boolean asInt) {
            int start = pos;
            if (peek() == '-') next();
            while (pos < src.length() && (Character.isDigit(src.charAt(pos))
                || src.charAt(pos) == '.' || src.charAt(pos) == 'e'
                || src.charAt(pos) == 'E' || src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            String num = src.substring(start, pos);
            try {
                if (asInt) return Long.parseLong(num);
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                throw new ParseException("bad number " + num);
            }
        }

        void skipValue() {
            skipWs();
            char c = peek();
            if (c == '"') { readString(); return; }
            if (c == '{') {
                next(); int depth = 1;
                while (depth > 0 && pos < src.length()) {
                    char ch = next();
                    if (ch == '"') { // 跳过字符串
                        while (pos < src.length()) {
                            char s = next();
                            if (s == '\\') { next(); continue; }
                            if (s == '"') break;
                        }
                    } else if (ch == '{') depth++;
                    else if (ch == '}') depth--;
                }
                return;
            }
            if (c == '[') {
                next(); int depth = 1;
                while (depth > 0 && pos < src.length()) {
                    char ch = next();
                    if (ch == '"') {
                        while (pos < src.length()) {
                            char s = next();
                            if (s == '\\') { next(); continue; }
                            if (s == '"') break;
                        }
                    } else if (ch == '[') depth++;
                    else if (ch == ']') depth--;
                }
                return;
            }
            // 数字或字面量
            while (pos < src.length() && !Character.isWhitespace(src.charAt(pos))
                && src.charAt(pos) != ',' && src.charAt(pos) != '}' && src.charAt(pos) != ']') {
                pos++;
            }
        }
    }
}

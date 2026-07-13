package com.testingai.sdlc.ticket;

import java.util.List;
import java.util.Map;

public final class AdfTextExtractor {

    private AdfTextExtractor() {
    }

    public static String extractText(Object node) {
        if (node == null) {
            return "";
        }
        if (node instanceof String text) {
            return text;
        }
        if (node instanceof Map<?, ?> map) {
            return extractFromMap(map);
        }
        if (node instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                sb.append(extractText(item));
            }
            return sb.toString();
        }
        return "";
    }

    private static String extractFromMap(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder();
        if ("text".equals(map.get("type")) && map.get("text") instanceof String text) {
            sb.append(text);
        }
        Object content = map.get("content");
        if (content instanceof List<?> children) {
            for (Object child : children) {
                sb.append(extractText(child));
            }
            if (!children.isEmpty() && "paragraph".equals(map.get("type"))) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}

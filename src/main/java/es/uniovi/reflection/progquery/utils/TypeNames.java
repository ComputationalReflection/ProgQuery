package es.uniovi.reflection.progquery.utils;

import java.util.regex.Pattern;

/** Names persisted from javac types must not include its identity-based capture number. */
public final class TypeNames {
    private static final Pattern CAPTURE_ID = Pattern.compile("\\bcapture#\\d+\\b");

    private TypeNames() { }

    public static String stable(String description) {
        if (description == null || !description.contains("capture#"))
            return description;
        var matcher = CAPTURE_ID.matcher(description);
        var result = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        int scanned = 0;
        while (matcher.find()) {
            // Type-use annotations may contain string/char literals. Preserve them.
            for (; scanned < matcher.start(); scanned++) {
                char current = description.charAt(scanned);
                if (quote != 0) {
                    if (escaped) escaped = false;
                    else if (current == '\\') escaped = true;
                    else if (current == quote) quote = 0;
                } else if (current == '"' || current == '\'') {
                    quote = current;
                }
            }
            if (quote == 0)
                matcher.appendReplacement(result, "capture");
            // A matched capture token contains neither quotes nor escapes.
            scanned = matcher.end();
            escaped = false;
        }
        return matcher.appendTail(result).toString();
    }
}

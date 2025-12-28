import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restores multiline formatting in YAML files after yq processing.
 * Converts single-line strings with \n back to literal block scalars (|).
 */
public class YamlMultilineRestorer {

    // Pattern to match fields with quoted strings (may contain \n)
    private static final Pattern QUOTED_STRING_PATTERN =
        Pattern.compile("^(\\s+)(\\w+):\\s*\"(.*)\"\\s*$", Pattern.DOTALL);

    // Pattern to match Unicode escapes like \U0001F6AB
    private static final Pattern UNICODE_PATTERN =
        Pattern.compile("\\\\U([0-9A-Fa-f]{8})");

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: YamlMultilineRestorer <yaml-file>");
            System.exit(1);
        }

        String yamlFile = args[0];
        restoreMultilineFormatting(yamlFile);
    }

    public static void restoreMultilineFormatting(String yamlFile) throws IOException {
        Path filePath = Path.of(yamlFile);
        List<String> lines = Files.readAllLines(filePath);
        List<String> output = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = QUOTED_STRING_PATTERN.matcher(line);

            // Check if this line has a quoted string with \n (multiline content)
            if (matcher.matches() && matcher.group(3).contains("\\n")) {
                String indent = matcher.group(1);
                String key = matcher.group(2);
                String value = matcher.group(3);

                // Unescape unicode first, then other escapes
                value = unescapeUnicodeInString(value);
                String unescaped = unescapeString(value);

                // Split by actual newlines
                String[] valueLines = unescaped.split("\n", -1); // -1 to keep trailing empty strings

                // Convert to multiline format
                output.add(indent + key + ": |");

                // Add each line with proper indentation
                for (int j = 0; j < valueLines.length; j++) {
                    String valueLine = valueLines[j];
                    // Skip the last line if it's empty (from trailing \n)
                    if (j == valueLines.length - 1 && valueLine.isEmpty()) {
                        break;
                    }
                    // Don't add indentation to empty lines
                    output.add(valueLine.isEmpty() ? "" : indent + "  " + valueLine);
                }
            } else {
                // Keep line as-is, but unescape unicode characters if present
                output.add(unescapeUnicode(line));
            }
        }

        Files.write(filePath, output);
    }

    /**
     * Unescapes common escape sequences in strings.
     */
    private static String unescapeString(String input) {
        return input
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    /**
     * Converts Unicode escape sequences like \U0001F6AB back to actual Unicode characters.
     */
    private static String unescapeUnicode(String input) {
        return unescapeUnicodeInString(input);
    }

    /**
     * Converts Unicode escape sequences in a string.
     */
    private static String unescapeUnicodeInString(String input) {
        Matcher matcher = UNICODE_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(input, lastEnd, matcher.start());
            try {
                String hexCode = matcher.group(1);
                int codePoint = Integer.parseInt(hexCode, 16);
                result.append(Character.toChars(codePoint));
            } catch (IllegalArgumentException e) {
                // Invalid unicode, keep as-is
                result.append(matcher.group(0));
            }
            lastEnd = matcher.end();
        }
        result.append(input.substring(lastEnd));

        return result.toString();
    }
}

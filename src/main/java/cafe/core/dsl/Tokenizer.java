package cafe.core.dsl;

import java.util.ArrayList;
import java.util.List;

public final class Tokenizer {

    public List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int line = 1;
        int lineStart = 0;

        while (i < source.length()) {
            char c = source.charAt(i);

            if (c == '\n') {
                line++;
                lineStart = i + 1;
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            int col = i - lineStart + 1;

            if (c == '"') {
                int end = source.indexOf('"', i + 1);
                if (end < 0) {
                    throw new ParseException(line, col, "unterminated string literal");
                }
                tokens.add(new Token(source.substring(i + 1, end), TokenKind.STRING, line, col));
                i = end + 1;
                continue;
            }

            if (isIdentStart(c)) {
                int j = i;
                while (j < source.length() && isIdentPart(source.charAt(j))) {
                    j++;
                }
                tokens.add(new Token(source.substring(i, j), TokenKind.WORD, line, col));
                i = j;
                continue;
            }

            if (Character.isDigit(c)) {
                int j = i;
                while (j < source.length() && Character.isDigit(source.charAt(j))) {
                    j++;
                }
                tokens.add(new Token(source.substring(i, j), TokenKind.NUMBER, line, col));
                i = j;
                continue;
            }

            String two = i + 1 < source.length() ? source.substring(i, i + 2) : "";
            switch (two) {
                case "->", "++", "--", "+=", "-=", "==", "!=", "<=", ">=", "&&", "||" -> {
                    tokens.add(new Token(two, TokenKind.SYMBOL, line, col));
                    i += 2;
                    continue;
                }
                default -> { /* fall through */ }
            }

            if ("=+-*/().,;:{}<>!".indexOf(c) >= 0) {
                tokens.add(new Token(String.valueOf(c), TokenKind.SYMBOL, line, col));
                i++;
                continue;
            }

            throw new ParseException(line, col, "unexpected character '" + c + "'");
        }

        return tokens;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}

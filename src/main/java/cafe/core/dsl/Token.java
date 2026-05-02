package cafe.core.dsl;

public record Token(String text, TokenKind kind, int line, int column) {
}

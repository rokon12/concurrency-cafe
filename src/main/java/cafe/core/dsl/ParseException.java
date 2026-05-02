package cafe.core.dsl;

public final class ParseException extends RuntimeException {
    private final int line;
    private final int column;

    public ParseException(int line, int column, String message) {
        super("Line " + line + ", column " + column + ": " + message);
        this.line = line;
        this.column = column;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}

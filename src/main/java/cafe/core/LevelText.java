package cafe.core;

final class LevelText {
    private LevelText() {
    }

    static String indent(String text, String prefix) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String line : text.split("\n", -1)) {
            if (!first) {
                sb.append('\n');
            }
            first = false;
            if (!line.isEmpty()) {
                sb.append(prefix).append(line);
            }
        }
        return sb.toString();
    }
}

package cafe.core.levels;

import cafe.core.SharedType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative description of a level. Everything that's text-content is a
 * field; the only logic each level supplies is its custom {@code validate}
 * implementation in {@link AbstractLevel}.
 */
public record LevelSpec(
    String id,
    String title,
    String chapter,
    String lessonClassName,
    String passingCondition,
    Map<String, SharedType> declarations,
    String intro,
    String starterCode,
    List<String> hints,
    String resultPrintlnExpression
) {

    public LevelSpec {
        declarations = Map.copyOf(declarations);
        hints = List.copyOf(hints);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String title;
        private String chapter = "Chapter I · Shared state";
        private String lessonClassName;
        private String passingCondition = "Pass the level";
        private final Map<String, SharedType> declarations = new LinkedHashMap<>();
        private String intro = "";
        private String starterCode = "";
        private final List<String> hints = new ArrayList<>();
        private String resultPrintlnExpression;

        public Builder id(String id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder chapter(String chapter) { this.chapter = chapter; return this; }
        public Builder lessonClassName(String name) { this.lessonClassName = name; return this; }
        public Builder passingCondition(String s) { this.passingCondition = s; return this; }

        public Builder declare(String name, SharedType type) {
            declarations.put(name, type);
            return this;
        }

        public Builder intro(String intro) { this.intro = intro; return this; }
        public Builder starterCode(String code) { this.starterCode = code; return this; }

        public Builder hint(String hint) {
            hints.add(hint);
            return this;
        }

        /**
         * Optional expression to print at the end of {@code main(...)}.
         * Pass the bare expression (no quotes around it), e.g.
         * {@code "\"Final counter: \" + counter"}. {@code null} skips the print.
         */
        public Builder resultPrintln(String expression) {
            this.resultPrintlnExpression = expression;
            return this;
        }

        public LevelSpec build() {
            if (id == null) throw new IllegalStateException("LevelSpec.id is required");
            if (title == null) throw new IllegalStateException("LevelSpec.title is required");
            if (lessonClassName == null) throw new IllegalStateException("LevelSpec.lessonClassName is required");
            return new LevelSpec(
                id, title, chapter, lessonClassName, passingCondition,
                declarations, intro, starterCode, hints, resultPrintlnExpression
            );
        }
    }
}

package cafe.web;

import cafe.core.Level;
import cafe.core.LevelRegistry;
import cafe.core.Outcome;
import cafe.core.sim.SimulationResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Main {

    private static final int HINT_AFTER_FAILS = 2;

    private static final String STORAGE_COMPLETED = "cc.completed";
    private static final String STORAGE_CURRENT = "cc.current";
    private static final String STORAGE_CODE_PREFIX = "cc.code.";

    private static final LevelRegistry REGISTRY = LevelRegistry.defaultRegistry();
    private static final Set<String> COMPLETED = new HashSet<>();

    private static int currentIndex;
    private static int failsSinceLastPass;
    private static int hintsRevealed;
    private static boolean fullSourceVisible;

    public static void main(String[] args) {
        Browser.onClick("runBtn", Main::run);
        Browser.onClick("resetBtn", Main::reset);
        Browser.onClick("showSourceBtn", Main::toggleFullSource);
        Browser.onClick("prevBtn", Main::goToPrevious);
        Browser.onClick("nextBtn", Main::goToNext);
        Browser.onClickInside("breadcrumb", "data-level-index", Main::switchToLevel);

        loadProgressFromStorage();

        String savedId = Browser.getStorage(STORAGE_CURRENT);
        int startIndex = 0;
        if (savedId != null) {
            int found = REGISTRY.indexOf(savedId);
            if (found >= 0) {
                startIndex = found;
            }
        }
        loadLevel(startIndex);
    }

    private static void loadProgressFromStorage() {
        String completed = Browser.getStorage(STORAGE_COMPLETED);
        if (completed != null && !completed.isEmpty()) {
            for (String id : completed.split(",")) {
                if (!id.isEmpty()) {
                    COMPLETED.add(id);
                }
            }
        }
    }

    private static void saveCompleted() {
        Browser.setStorage(STORAGE_COMPLETED, String.join(",", COMPLETED));
    }

    private static void loadLevel(int index) {
        currentIndex = index;
        failsSinceLastPass = 0;
        hintsRevealed = 0;
        fullSourceVisible = false;
        Level level = REGISTRY.get(currentIndex);
        Browser.setStorage(STORAGE_CURRENT, level.id());

        String savedCode = Browser.getStorage(STORAGE_CODE_PREFIX + level.id());
        String editorContent = (savedCode != null && !savedCode.isEmpty())
            ? savedCode
            : level.starterCode();

        Browser.setText("levelTitle", level.title());
        Browser.setText("levelIntro", level.intro());
        Browser.setValue("editor", editorContent);
        Browser.setHtml("metrics", "<p>Press <strong>Run</strong> to simulate the kitchen.</p>");
        Browser.setHtml("eventLog", "<p>No events yet.</p>");
        Browser.setHtml("hintPanel", "");
        Browser.setClassName("statusBanner",
            COMPLETED.contains(level.id()) ? "status-banner status-pass" : "status-banner status-idle");
        Browser.setText("statusBanner",
            COMPLETED.contains(level.id()) ? "Already completed — feel free to revisit." : "Ready");
        renderFullSource();
        renderBreadcrumb();
        renderNavButtons();
    }

    private static void reset() {
        Level level = REGISTRY.get(currentIndex);
        Browser.removeStorage(STORAGE_CODE_PREFIX + level.id());
        loadLevel(currentIndex);
    }

    private static void run() {
        Level level = REGISTRY.get(currentIndex);
        String code = Browser.getValue("editor");
        Browser.setStorage(STORAGE_CODE_PREFIX + level.id(), code);

        Outcome outcome = level.run(code);
        render(outcome);
        renderFullSource();

        if (outcome.passed()) {
            if (!COMPLETED.contains(level.id())) {
                COMPLETED.add(level.id());
                saveCompleted();
            }
            failsSinceLastPass = 0;
            hintsRevealed = 0;
        } else {
            failsSinceLastPass++;
            maybeRevealHint(level);
        }
        renderBreadcrumb();
        renderNavButtons();
    }

    private static void switchToLevel(int index) {
        if (index == currentIndex) {
            return;
        }
        if (!isAccessible(index)) {
            return;
        }
        saveCurrentEditor();
        loadLevel(index);
    }

    private static void goToPrevious() {
        if (currentIndex > 0) {
            saveCurrentEditor();
            loadLevel(currentIndex - 1);
        }
    }

    private static void goToNext() {
        if (currentIndex < REGISTRY.size() - 1 && isAccessible(currentIndex + 1)) {
            saveCurrentEditor();
            loadLevel(currentIndex + 1);
        }
    }

    private static void saveCurrentEditor() {
        Level level = REGISTRY.get(currentIndex);
        Browser.setStorage(STORAGE_CODE_PREFIX + level.id(), Browser.getValue("editor"));
    }

    private static boolean isAccessible(int index) {
        if (index <= currentIndex) {
            return true;
        }
        for (int i = 0; i < index; i++) {
            if (!COMPLETED.contains(REGISTRY.get(i).id())) {
                return false;
            }
        }
        return true;
    }

    private static void render(Outcome outcome) {
        if (outcome.hasErrors()) {
            renderErrors(outcome);
            return;
        }

        SimulationResult sim = outcome.simulation();
        StringBuilder metrics = new StringBuilder();
        metrics.append("<p><strong>").append(outcome.passed() ? "✅ Pass" : "❌ Fail")
            .append(":</strong> ").append(escape(outcome.summary())).append("</p>");
        metrics.append("<p><strong>Final state:</strong></p><ul>");
        sim.finalGlobals().forEach((k, v) ->
            metrics.append("<li>").append(escape(k)).append(" = ").append(v).append("</li>"));
        metrics.append("</ul>");
        Browser.setHtml("metrics", metrics.toString());

        StringBuilder log = new StringBuilder("<ol>");
        for (String event : sim.events()) {
            log.append("<li>").append(escape(event)).append("</li>");
        }
        log.append("</ol>");
        Browser.setHtml("eventLog", log.toString());

        if (outcome.passed()) {
            Browser.setClassName("statusBanner", "status-banner status-pass");
            String banner = currentIndex < REGISTRY.size() - 1
                ? "Level complete — click Next to continue"
                : "Level complete — you've cleared the kitchen";
            Browser.setText("statusBanner", banner);
        } else {
            Browser.setClassName("statusBanner", "status-banner status-fail");
            Browser.setText("statusBanner", "Bug still there. Keep editing.");
        }
    }

    private static void renderErrors(Outcome outcome) {
        StringBuilder html = new StringBuilder("<p><strong>Could not run your code:</strong></p><ul>");
        for (String err : outcome.errors()) {
            html.append("<li>").append(escape(err)).append("</li>");
        }
        html.append("</ul>");
        Browser.setHtml("metrics", html.toString());
        Browser.setHtml("eventLog", "<p>No events. Fix the error above and try again.</p>");
        Browser.setClassName("statusBanner", "status-banner status-fail");
        Browser.setText("statusBanner", outcome.summary());
    }

    private static void toggleFullSource() {
        fullSourceVisible = !fullSourceVisible;
        renderFullSource();
    }

    private static void renderFullSource() {
        if (!fullSourceVisible) {
            Browser.setText("showSourceBtn", "Show full Java source");
            Browser.setClassName("fullSourcePanel", "panel hidden");
            Browser.setText("fullSource", "");
            return;
        }
        Browser.setText("showSourceBtn", "Hide full Java source");
        Browser.setClassName("fullSourcePanel", "panel");
        Level level = REGISTRY.get(currentIndex);
        String code = Browser.getValue("editor");
        Browser.setText("fullSource", level.fullSourceWith(code));
    }

    private static void renderBreadcrumb() {
        List<Level> levels = REGISTRY.levels();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels.size(); i++) {
            Level l = levels.get(i);
            boolean done = COMPLETED.contains(l.id());
            boolean current = i == currentIndex;
            boolean accessible = isAccessible(i);

            String classes = "level-tab";
            if (current) classes += " current";
            if (done) classes += " done";
            if (!accessible) classes += " locked";

            sb.append("<button class=\"").append(classes).append("\"")
                .append(" data-level-index=\"").append(i).append("\"")
                .append(accessible ? "" : " disabled")
                .append(">");
            sb.append("<span class=\"level-num\">").append(i + 1).append("</span>");
            sb.append("<span class=\"level-name\">").append(escape(stripPrefix(l.title()))).append("</span>");
            if (done) {
                sb.append("<span class=\"level-mark\">✓</span>");
            } else if (!accessible) {
                sb.append("<span class=\"level-mark\">🔒</span>");
            }
            sb.append("</button>");
        }
        Browser.setHtml("breadcrumb", sb.toString());
    }

    private static void renderNavButtons() {
        Browser.setDisabled("prevBtn", currentIndex == 0);
        boolean canGoNext = currentIndex < REGISTRY.size() - 1
            && isAccessible(currentIndex + 1);
        Browser.setDisabled("nextBtn", !canGoNext);
    }

    private static String stripPrefix(String title) {
        int colon = title.indexOf(':');
        return colon >= 0 ? title.substring(colon + 1).trim() : title;
    }

    private static void maybeRevealHint(Level level) {
        List<String> hints = level.hints();
        if (failsSinceLastPass < HINT_AFTER_FAILS) {
            return;
        }
        if (hintsRevealed >= hints.size()) {
            return;
        }
        hintsRevealed++;

        StringBuilder html = new StringBuilder();
        html.append("<h3>Hints (").append(hintsRevealed).append("/").append(hints.size()).append(")</h3>");
        html.append("<ul>");
        for (int i = 0; i < hintsRevealed; i++) {
            html.append("<li>").append(escape(hints.get(i))).append("</li>");
        }
        html.append("</ul>");
        Browser.setHtml("hintPanel", html.toString());
    }

    private static String escape(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}

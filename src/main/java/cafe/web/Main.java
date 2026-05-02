package cafe.web;

import cafe.core.Level;
import cafe.core.LevelRegistry;
import cafe.core.Outcome;
import cafe.core.dsl.ParseException;
import cafe.core.sim.SimulationException;
import cafe.core.sim.SimulationResult;
import cafe.core.sim.Simulator;

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

    private static Simulator activeSimulator;
    private static String activeSimulatorCode;

    private static final int PLAY_TICK_MS = 500;
    private static boolean playing;
    private static int playTimerId = -1;

    public static void main(String[] args) {
        Browser.onClick("runBtn", Main::run);
        Browser.onClick("stepBtn", Main::step);
        Browser.onClick("playBtn", Main::togglePlay);
        Browser.onClick("resetBtn", Main::reset);
        Browser.onClick("resetProgressBtn", Main::resetAllProgress);
        Browser.onClick("showSourceBtn", Main::toggleFullSource);
        Browser.onClick("downloadBtn", Main::downloadJava);
        Browser.onClick("prevBtn", Main::goToPrevious);
        Browser.onClick("nextBtn", Main::goToNext);
        Browser.onClickInside("breadcrumb", "data-level-index", Main::switchToLevel);
        Browser.onClickInside("scheduleControls", "data-chef-index", Main::stepSpecificChef);

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
        stopPlay();
        currentIndex = index;
        failsSinceLastPass = 0;
        hintsRevealed = 0;
        fullSourceVisible = false;
        activeSimulator = null;
        activeSimulatorCode = null;
        Level level = REGISTRY.get(currentIndex);
        Browser.setStorage(STORAGE_CURRENT, level.id());

        String savedCode = Browser.getStorage(STORAGE_CODE_PREFIX + level.id());
        String editorContent = (savedCode != null && !savedCode.isEmpty())
            ? savedCode
            : level.starterCode();

        Browser.setText("levelTitle", level.title());
        Browser.setText("levelIntro", level.intro());
        Browser.setEditorCode(editorContent);
        Browser.setHtml("metrics", "<p>Press <strong>Run to end</strong> for the full simulation, or <strong>Step</strong> to advance one round at a time.</p>");
        Browser.setHtml("eventLog", "<p>No events yet.</p>");
        Browser.setHtml("hintPanel", "");
        Browser.setClassName("statusBanner",
            COMPLETED.contains(level.id()) ? "status-banner status-pass" : "status-banner status-idle");
        Browser.setText("statusBanner",
            COMPLETED.contains(level.id()) ? "Already completed — feel free to revisit." : "Ready");
        renderFullSource();
        renderBreadcrumb();
        renderNavButtons();
        renderSchedulePanel();
    }

    private static void reset() {
        Level level = REGISTRY.get(currentIndex);
        Browser.removeStorage(STORAGE_CODE_PREFIX + level.id());
        loadLevel(currentIndex);
    }

    private static void resetAllProgress() {
        if (!Browser.confirm("Clear all completed levels and saved code? This cannot be undone.")) {
            return;
        }
        Browser.clearStoragePrefix("cc.");
        COMPLETED.clear();
        loadLevel(0);
    }

    private static void run() {
        stopPlay();
        Level level = REGISTRY.get(currentIndex);
        String code = Browser.getEditorCode();
        Browser.setStorage(STORAGE_CODE_PREFIX + level.id(), code);
        activeSimulator = null;
        activeSimulatorCode = null;

        Outcome outcome = level.run(code);
        renderOutcome(outcome, level);
        renderSchedulePanel();
    }

    private static void togglePlay() {
        if (playing) {
            stopPlay();
            return;
        }
        if (!ensureActiveSimulator()) {
            return;
        }
        playing = true;
        Browser.setText("playBtn", "Pause");
        schedulePlayTick();
    }

    private static void stopPlay() {
        playing = false;
        Browser.setText("playBtn", "Play");
        if (playTimerId != -1) {
            Browser.clearTimeout(playTimerId);
            playTimerId = -1;
        }
    }

    private static void schedulePlayTick() {
        playTimerId = Browser.setTimeout(Main::playTick, PLAY_TICK_MS);
    }

    private static void playTick() {
        playTimerId = -1;
        if (!playing || activeSimulator == null) {
            return;
        }
        Level level = REGISTRY.get(currentIndex);
        try {
            activeSimulator.stepInstruction();
        } catch (SimulationException e) {
            stopPlay();
            renderOutcome(new Outcome(false, "Runtime error", null, List.of(e.getMessage())), level);
            return;
        }
        afterStep(level);
        if (activeSimulator == null || activeSimulator.isFinished()) {
            stopPlay();
        } else {
            schedulePlayTick();
        }
    }

    private static void step() {
        if (!ensureActiveSimulator()) {
            return;
        }
        Level level = REGISTRY.get(currentIndex);
        try {
            activeSimulator.stepInstruction();
        } catch (SimulationException e) {
            activeSimulator = null;
            activeSimulatorCode = null;
            renderOutcome(new Outcome(false, "Runtime error", null, List.of(e.getMessage())), level);
            return;
        }
        afterStep(level);
    }

    private static void stepSpecificChef(int chefIndex) {
        if (!ensureActiveSimulator()) {
            return;
        }
        Level level = REGISTRY.get(currentIndex);
        try {
            activeSimulator.stepChef(chefIndex);
        } catch (SimulationException e) {
            activeSimulator = null;
            activeSimulatorCode = null;
            renderOutcome(new Outcome(false, "Runtime error", null, List.of(e.getMessage())), level);
            return;
        }
        afterStep(level);
    }

    private static boolean ensureActiveSimulator() {
        Level level = REGISTRY.get(currentIndex);
        String code = Browser.getEditorCode();
        Browser.setStorage(STORAGE_CODE_PREFIX + level.id(), code);
        if (activeSimulator == null
            || !code.equals(activeSimulatorCode)
            || activeSimulator.isFinished()) {
            try {
                activeSimulator = level.startSimulation(code);
                activeSimulatorCode = code;
            } catch (ParseException e) {
                activeSimulator = null;
                activeSimulatorCode = null;
                renderOutcome(new Outcome(false, "Parse error", null, List.of(e.getMessage())), level);
                return false;
            }
        }
        return true;
    }

    private static void afterStep(Level level) {
        SimulationResult snap = activeSimulator.snapshot();
        if (activeSimulator.isFinished()) {
            Outcome outcome = level.validate(snap);
            renderOutcome(outcome, level);
        } else {
            renderInProgress(snap);
        }
        renderSchedulePanel();
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
        Browser.setStorage(STORAGE_CODE_PREFIX + level.id(), Browser.getEditorCode());
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

    private static void renderOutcome(Outcome outcome, Level level) {
        renderFullSource();

        if (outcome.hasErrors()) {
            renderErrors(outcome);
            failsSinceLastPass++;
            maybeRevealHint(level);
            renderBreadcrumb();
            renderNavButtons();
            return;
        }

        SimulationResult sim = outcome.simulation();
        renderSnapshotMetrics(outcome, sim);
        renderEventLog(sim);

        if (outcome.passed()) {
            if (!COMPLETED.contains(level.id())) {
                COMPLETED.add(level.id());
                saveCompleted();
            }
            failsSinceLastPass = 0;
            hintsRevealed = 0;
            Browser.setClassName("statusBanner", "status-banner status-pass");
            String banner = currentIndex < REGISTRY.size() - 1
                ? "Level complete — click Next to continue"
                : "Level complete — you've cleared the kitchen";
            Browser.setText("statusBanner", banner);
        } else {
            failsSinceLastPass++;
            maybeRevealHint(level);
            Browser.setClassName("statusBanner", "status-banner status-fail");
            Browser.setText("statusBanner", "Bug still there. Keep editing.");
        }
        renderBreadcrumb();
        renderNavButtons();
    }

    private static void renderSchedulePanel() {
        if (activeSimulator == null || activeSimulator.isFinished()) {
            Browser.setClassName("schedulePanel", "panel hidden");
            Browser.setHtml("scheduleControls", "");
            return;
        }
        Browser.setClassName("schedulePanel", "panel");
        StringBuilder sb = new StringBuilder();
        for (Simulator.ChefSnapshot chef : activeSimulator.chefSnapshots()) {
            String classes = "chef-step";
            String label = chef.name();
            boolean disabled = chef.done();
            if (chef.done()) {
                classes += " done";
                label += " (done)";
            } else if (chef.blockedOnLock() != null) {
                classes += " blocked";
                label += " (waiting on " + chef.blockedOnLock() + ")";
            }
            sb.append("<button class=\"").append(classes).append("\"")
                .append(" data-chef-index=\"").append(chef.index()).append("\"")
                .append(disabled ? " disabled" : "")
                .append(">")
                .append(escape(label))
                .append("</button>");
        }
        Browser.setHtml("scheduleControls", sb.toString());
    }

    private static void renderInProgress(SimulationResult snap) {
        renderFullSource();
        StringBuilder metrics = new StringBuilder();
        metrics.append("<p><strong>Stepping…</strong> ").append(snap.events().size())
            .append(" event").append(snap.events().size() == 1 ? "" : "s").append(" so far.</p>");
        metrics.append("<p><strong>Current state:</strong></p><ul>");
        snap.finalGlobals().forEach((k, v) ->
            metrics.append("<li>").append(escape(k)).append(" = ").append(v).append("</li>"));
        metrics.append("</ul>");
        Browser.setHtml("metrics", metrics.toString());
        renderEventLog(snap);

        Browser.setClassName("statusBanner", "status-banner status-idle");
        Browser.setText("statusBanner", "Stepping — click Step again to advance, or Run to end.");
    }

    private static void renderSnapshotMetrics(Outcome outcome, SimulationResult sim) {
        StringBuilder metrics = new StringBuilder();
        metrics.append("<p><strong>").append(outcome.passed() ? "✅ Pass" : "❌ Fail")
            .append(":</strong> ").append(escape(outcome.summary())).append("</p>");
        metrics.append("<p><strong>Final state:</strong></p><ul>");
        sim.finalGlobals().forEach((k, v) ->
            metrics.append("<li>").append(escape(k)).append(" = ").append(v).append("</li>"));
        metrics.append("</ul>");
        Browser.setHtml("metrics", metrics.toString());
    }

    private static void renderEventLog(SimulationResult sim) {
        if (sim.events().isEmpty()) {
            Browser.setHtml("eventLog", "<p>No events yet.</p>");
            return;
        }
        StringBuilder log = new StringBuilder("<ol>");
        for (String event : sim.events()) {
            log.append("<li>").append(escape(event)).append("</li>");
        }
        log.append("</ol>");
        Browser.setHtml("eventLog", log.toString());
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

    private static void downloadJava() {
        Level level = REGISTRY.get(currentIndex);
        String code = Browser.getEditorCode();
        Browser.downloadFile(level.lessonClassName() + ".java", level.fullSourceWith(code));
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
        String code = Browser.getEditorCode();
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

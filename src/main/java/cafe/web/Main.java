package cafe.web;

import cafe.core.Level;
import cafe.core.LevelRegistry;
import cafe.core.Outcome;
import cafe.core.SharedType;
import cafe.core.dsl.ParseException;
import cafe.core.sim.SimulationException;
import cafe.core.sim.SimulationResult;
import cafe.core.sim.Simulator;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Main {

    private static final int HINT_AFTER_FAILS = 2;
    private static final int PLAY_TICK_MS = 500;

    private static final String STORAGE_COMPLETED = "cc.completed";
    private static final String STORAGE_CURRENT = "cc.current";
    private static final String STORAGE_THEME = "cc.theme";
    private static final String STORAGE_DEV = "cc.dev";
    private static final String STORAGE_HELP_SEEN = "cc.helpSeen";
    private static final String STORAGE_CODE_PREFIX = "cc.code.";

    private static final LevelRegistry REGISTRY = LevelRegistry.defaultRegistry();
    private static final Set<String> COMPLETED = new HashSet<>();

    private static int currentIndex;
    private static int failsSinceLastPass;
    private static int hintsRevealed;
    private static boolean fullSourceVisible;
    private static String activeTab = "viz";
    private static String theme = "dark";
    private static boolean devMode;

    /** Last-render values of each int global, used to flash on change. */
    private static final java.util.HashMap<String, Integer> previousGlobals = new java.util.HashMap<>();

    private static Simulator activeSimulator;
    private static String activeSimulatorCode;
    private static int totalTraceSteps;

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
        Browser.onClick("themeBtn", Main::toggleTheme);
        Browser.onClick("helpBtn", Main::openHelp);
        Browser.onClick("helpCloseBtn", Main::closeHelp);
        Browser.onDevShortcut(Main::toggleDevMode);
        Browser.onClick("overlayCloseBtn", Main::closeOverlay);
        Browser.onClick("overlayNextBtn", Main::overlayNext);
        Browser.onClickInside("breadcrumb", "data-level-index", Main::switchToLevel);
        Browser.onClickInside("kitchenView", "data-chef-index", Main::stepSpecificChef);
        Browser.onClickInside("simTabs", "data-tab-key", Main::switchTab);
        Browser.onClick("tabViz", () -> setTab("viz"));
        Browser.onClick("tabLog", () -> setTab("log"));

        loadProgressFromStorage();
        applyTheme();

        String savedId = Browser.getStorage(STORAGE_CURRENT);
        int startIndex = 0;
        if (savedId != null) {
            int found = REGISTRY.indexOf(savedId);
            if (found >= 0) {
                startIndex = found;
            }
        }
        loadLevel(startIndex);

        // First-visit tutorial
        if (!"1".equals(Browser.getStorage(STORAGE_HELP_SEEN))) {
            openHelp();
        }
    }

    private static void openHelp() {
        Browser.setClassName("helpOverlay", "overlay");
    }

    private static void closeHelp() {
        Browser.setClassName("helpOverlay", "overlay hidden");
        Browser.setStorage(STORAGE_HELP_SEEN, "1");
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
        String savedTheme = Browser.getStorage(STORAGE_THEME);
        if (savedTheme != null && (savedTheme.equals("dark") || savedTheme.equals("light"))) {
            theme = savedTheme;
        }
        devMode = "1".equals(Browser.getStorage(STORAGE_DEV));
        renderDevBadge();
    }

    private static void toggleDevMode() {
        devMode = !devMode;
        Browser.setStorage(STORAGE_DEV, devMode ? "1" : "");
        renderDevBadge();
        renderBreadcrumb();
        renderNavButtons();
    }

    private static void renderDevBadge() {
        Browser.setClassName("devBadge", devMode ? "dev-badge" : "dev-badge hidden");
    }

    private static void saveCompleted() {
        Browser.setStorage(STORAGE_COMPLETED, String.join(",", COMPLETED));
    }

    private static void applyTheme() {
        Browser.setDataAttribute("html", "data-theme", theme);
        Browser.setText("themeBtn", theme.equals("dark") ? "☾" : "☀");
    }

    private static void toggleTheme() {
        theme = theme.equals("dark") ? "light" : "dark";
        Browser.setStorage(STORAGE_THEME, theme);
        applyTheme();
    }

    private static void loadLevel(int index) {
        stopPlay();
        Browser.setActiveLine(0);
        previousGlobals.clear();
        currentIndex = index;
        failsSinceLastPass = 0;
        hintsRevealed = 0;
        fullSourceVisible = false;
        activeSimulator = null;
        activeSimulatorCode = null;
        totalTraceSteps = 0;
        Level level = REGISTRY.get(currentIndex);
        Browser.setStorage(STORAGE_CURRENT, level.id());

        String savedCode = Browser.getStorage(STORAGE_CODE_PREFIX + level.id());
        String editorContent = (savedCode != null && !savedCode.isEmpty())
            ? savedCode
            : level.starterCode();

        Browser.setText("levelTitle", stripPrefix(level.title()));
        Browser.setText("levelChapter", level.chapter());
        Browser.setText("levelNumber",
            "Level " + pad2(currentIndex + 1) + " of " + pad2(REGISTRY.size()));
        Browser.setText("paneCodeFile", level.id() + ".java");
        Browser.setText("paneCodeMeta", primaryTypeLabel(level));
        Browser.setHtml("levelIntro", introWithHighlights(level.intro()));
        Browser.setText("objectiveGoalLabel", level.passingCondition());
        Browser.setEditorCode(editorContent);

        // Build a simulator from the loaded code so the kitchen previews
        // real chef names + initial state before the user clicks Run / Step.
        // Parse failures fall back to the generic placeholder.
        try {
            activeSimulator = level.startSimulation(editorContent);
            activeSimulatorCode = editorContent;
        } catch (RuntimeException ignore) {
            activeSimulator = null;
            activeSimulatorCode = null;
        }

        renderInitialState(level);
        renderFullSource();
        renderBreadcrumb();
        renderNavButtons();
        renderHintCard();
        Browser.setClassName("successOverlay", "overlay hidden");
        renderSchedulePanel();
    }

    private static void renderInitialState(Level level) {
        if (activeSimulator != null) {
            renderKitchen(activeSimulator.snapshot(), level);
        } else {
            Browser.setHtml("kitchenView", emptyKitchenHtml(level));
        }
        Browser.setHtml("eventLog", "");
        Browser.setHtml("resultBar", idleResultHtml());
        Browser.setText("stepCounter", "step 0 / 0");
        setObjectiveState(false, false);
        renderFooterNote(false);
        Browser.setClassName("simStatusDot", "dot");
        Browser.setHtmlAttribute("simStatusDot", "style", "background: var(--fg-3)");
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
        Browser.setActiveLine(0);
        Level level = REGISTRY.get(currentIndex);
        String code = Browser.getEditorCode();
        Browser.setStorage(STORAGE_CODE_PREFIX + level.id(), code);
        activeSimulator = null;
        activeSimulatorCode = null;
        totalTraceSteps = 0;

        Outcome outcome = level.run(code);
        if (outcome.simulation() != null) {
            totalTraceSteps = outcome.simulation().events().size();
        }
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
        Browser.setText("playBtn", "⏸ Pause");
        schedulePlayTick();
    }

    private static void stopPlay() {
        playing = false;
        Browser.setText("playBtn", "↻ Play");
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
                totalTraceSteps = 0;
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
        Browser.setActiveLine(activeSimulator.lastStepLine());
        if (activeSimulator.isFinished()) {
            totalTraceSteps = snap.events().size();
            Outcome outcome = level.validate(snap);
            renderOutcome(outcome, level);
        } else {
            renderInProgress(snap, level);
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

    private static void overlayNext() {
        Browser.setClassName("successOverlay", "overlay hidden");
        if (currentIndex < REGISTRY.size() - 1 && isAccessible(currentIndex + 1)) {
            saveCurrentEditor();
            loadLevel(currentIndex + 1);
        }
    }

    private static void closeOverlay() {
        Browser.setClassName("successOverlay", "overlay hidden");
    }

    private static void saveCurrentEditor() {
        Level level = REGISTRY.get(currentIndex);
        Browser.setStorage(STORAGE_CODE_PREFIX + level.id(), Browser.getEditorCode());
    }

    private static boolean isAccessible(int index) {
        if (devMode) {
            return true;
        }
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
            renderErrors(outcome, level);
            failsSinceLastPass++;
            maybeRevealHint();
            renderBreadcrumb();
            renderNavButtons();
            renderFooterNote(false);
            return;
        }

        SimulationResult sim = outcome.simulation();
        renderKitchen(sim, level);
        renderEventLog(sim);
        renderResultBar(outcome, sim, true);
        renderStepCounter(sim);

        boolean passed = outcome.passed();
        setObjectiveState(true, passed);
        Browser.setHtmlAttribute("simStatusDot", "style",
            "background: " + (passed ? "var(--ok)" : "var(--err)"));

        if (passed) {
            boolean firstTime = !COMPLETED.contains(level.id());
            if (firstTime) {
                COMPLETED.add(level.id());
                saveCompleted();
            }
            failsSinceLastPass = 0;
            hintsRevealed = 0;
            renderHintCard();
            if (firstTime) {
                showSuccessOverlay(level);
            }
        } else {
            failsSinceLastPass++;
            maybeRevealHint();
        }
        renderBreadcrumb();
        renderNavButtons();
        renderFooterNote(passed);
    }

    private static void renderInProgress(SimulationResult snap, Level level) {
        renderFullSource();
        renderKitchen(snap, level);
        renderEventLog(snap);
        renderStepCounter(snap);
        Browser.setHtml("resultBar",
            "<span class=\"pill warn\">running</span><span>"
                + escape(stepLabel(snap)) + " · counter = "
                + escape(currentCounterText(snap, level)) + "</span>");
        Browser.setHtmlAttribute("simStatusDot", "style", "background: var(--warn)");
        setObjectiveState(true, false);
    }

    private static void renderErrors(Outcome outcome, Level level) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"chef-card\" style=\"grid-column: 1 / -1\">");
        sb.append("<div class=\"chef-card-head\" style=\"color: var(--err)\">");
        sb.append("<span class=\"chef-dot\" style=\"background: var(--err)\"></span>");
        sb.append("<span>Could not run your code</span>");
        sb.append("</div>");
        sb.append("<div class=\"chef-action\" style=\"white-space: pre-wrap\">");
        for (String err : outcome.errors()) {
            sb.append(escape(err)).append("\n");
        }
        sb.append("</div></div>");
        Browser.setHtml("kitchenView", sb.toString());
        Browser.setHtml("eventLog", "");
        Browser.setHtml("resultBar",
            "<span class=\"pill err\">FAIL</span><span>" + escape(outcome.summary()) + "</span>");
        Browser.setHtmlAttribute("simStatusDot", "style", "background: var(--err)");
    }

    private static void renderKitchen(SimulationResult snap, Level level) {
        List<Simulator.ChefSnapshot> chefs = activeSimulator != null
            ? activeSimulator.chefSnapshots()
            : List.of();
        Map<String, Integer> globals = snap.finalGlobals();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chefs.size(); i++) {
            Simulator.ChefSnapshot c = chefs.get(i);
            sb.append(renderChefCard(c, i));
            if (i == (chefs.size() - 1) / 2) {
                sb.append(renderCounterStage(globals, level, chefs));
            }
        }
        if (chefs.isEmpty()) {
            sb.append(emptyKitchenHtml(level));
        }
        Browser.setHtml("kitchenView", sb.toString());
    }

    private static String renderChefCard(Simulator.ChefSnapshot c, int displayIndex) {
        String classKey = "c" + (displayIndex % 3 + 1);
        String classes = "chef-card " + classKey;
        if (c.done()) classes += " done";
        else if (c.blockedOnLock() != null) classes += " blocked";
        else if (c.lastEventDetail() != null) classes += " active";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"").append(classes).append("\">");
        sb.append("<div class=\"chef-card-head\"><span class=\"chef-dot\"></span>");
        sb.append("<span>").append(escape(c.name())).append("</span>");
        if (c.blockedOnLock() != null) {
            sb.append("<span class=\"chef-lock-tag\">⏳ waiting on ").append(escape(c.blockedOnLock())).append("</span>");
        }
        sb.append("</div>");

        sb.append("<div class=\"chef-status\"><span class=\"status-label\">status</span>");
        sb.append("<span class=\"status-val\">").append(escape(statusLabel(c))).append("</span></div>");

        String localsLine = primaryLocalString(c.locals());
        sb.append("<div class=\"chef-status\"><span class=\"status-label\">locals</span>");
        sb.append("<span class=\"status-val mono\">").append(escape(localsLine)).append("</span></div>");

        sb.append("<div class=\"chef-action\">");
        sb.append(escape(c.lastEventDetail() != null ? c.lastEventDetail() : "waiting"));
        sb.append("</div>");

        boolean disabled = c.done()
            || activeSimulator == null
            || activeSimulator.isFinished();
        sb.append("<button class=\"chef-step-btn\" data-chef-index=\"").append(c.index()).append("\"")
            .append(disabled ? " disabled" : "").append(">→ Step ")
            .append(escape(c.name())).append("</button>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String renderCounterStage(Map<String, Integer> globals, Level level,
                                             List<Simulator.ChefSnapshot> chefs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"counter-stage\">");

        sb.append(renderQueueDisplay(level));

        Map.Entry<String, SharedType> primary = primaryDataDeclaration(level);
        if (primary != null) {
            String name = primary.getKey();
            int value = globals.getOrDefault(name, 0);
            boolean changed = pulseFor(name, value);
            sb.append("<div class=\"counter-label\">")
                .append(escape(primary.getValue().javaTypeName())).append(' ')
                .append(escape(name)).append("</div>");
            sb.append("<div class=\"counter-display").append(changed ? " pulse" : "").append("\">")
                .append(value).append("</div>");
            sb.append("<div class=\"counter-target\">target = ")
                .append(escape(level.passingCondition())).append("</div>");
        } else {
            sb.append("<div class=\"counter-label\">shared resources</div>");
        }

        // Other globals (locks, additional data, executors)
        StringBuilder mini = new StringBuilder();
        for (var entry : level.sharedDeclarations().entrySet()) {
            String name = entry.getKey();
            if (primary != null && primary.getKey().equals(name)) continue;
            SharedType type = entry.getValue();
            if (type instanceof SharedType.MonitorType || type instanceof SharedType.LockType
                || type instanceof SharedType.FixedExecutorType
                || type instanceof SharedType.VirtualExecutorType
                || type instanceof SharedType.QueueType) {
                mini.append("<div class=\"global-mini\">")
                    .append(escape(type.javaTypeName())).append(" <b>")
                    .append(escape(name)).append("</b></div>");
            } else {
                int v = globals.getOrDefault(name, 0);
                boolean changed = pulseFor(name, v);
                mini.append("<div class=\"global-mini").append(changed ? " pulse" : "").append("\">")
                    .append(escape(type.javaTypeName())).append(" <b>")
                    .append(escape(name)).append("</b> = ").append(v).append("</div>");
            }
        }
        sb.append(mini);
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Returns true if the int global has a different value from the last
     * render, so the UI can flash it. Updates the cached value.
     */
    private static boolean pulseFor(String name, int value) {
        Integer prev = previousGlobals.put(name, value);
        return prev != null && prev != value;
    }

    private static String renderQueueDisplay(Level level) {
        StringBuilder sb = new StringBuilder();
        Map<String, List<Integer>> snapshots = activeSimulator != null
            ? activeSimulator.queueSnapshot()
            : Map.of();
        for (var entry : level.sharedDeclarations().entrySet()) {
            if (!(entry.getValue() instanceof SharedType.QueueType q)) continue;
            String name = entry.getKey();
            int cap = q.capacity();
            List<Integer> contents = snapshots.getOrDefault(name, List.of());
            sb.append("<div class=\"queue-display\">");
            sb.append("<div class=\"queue-label\">").append(escape(name))
                .append(" · ").append(contents.size()).append("/").append(cap).append("</div>");
            sb.append("<div class=\"queue-slots\">");
            for (int i = 0; i < cap; i++) {
                if (i < contents.size()) {
                    sb.append("<div class=\"queue-slot filled\">").append(contents.get(i)).append("</div>");
                } else {
                    sb.append("<div class=\"queue-slot empty\"></div>");
                }
            }
            sb.append("</div></div>");
        }
        return sb.toString();
    }

    private static String emptyKitchenHtml(Level level) {
        StringBuilder sb = new StringBuilder();
        sb.append(emptyChefCardHtml("c1"));
        sb.append(renderCounterStage(initialGlobalsForDisplay(level), level, List.of()));
        sb.append(emptyChefCardHtml("c2"));
        return sb.toString();
    }

    private static String emptyChefCardHtml(String colorClass) {
        return "<div class=\"chef-card " + colorClass + "\">"
            + "<div class=\"chef-card-head\"><span class=\"chef-dot\"></span><span>chef</span></div>"
            + "<div class=\"chef-status\"><span class=\"status-label\">status</span><span class=\"status-val\">idle</span></div>"
            + "<div class=\"chef-action\">Press Run to start the kitchen.</div>"
            + "</div>";
    }

    private static Map<String, Integer> initialGlobalsForDisplay(Level level) {
        return level.initialGlobals();
    }

    private static void renderEventLog(SimulationResult sim) {
        if (sim.events().isEmpty()) {
            Browser.setHtml("eventLog", "<li class=\"empty-log\">Run the code to see the kitchen log.</li>");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sim.events().size(); i++) {
            sb.append("<li class=\"ev\">");
            sb.append("<span class=\"ev-num\">").append(pad2(i + 1)).append("</span>");
            sb.append("<span class=\"ev-text\">").append(escape(sim.events().get(i))).append("</span>");
            sb.append("</li>");
        }
        Browser.setHtml("eventLog", sb.toString());
    }

    private static void renderResultBar(Outcome outcome, SimulationResult sim, boolean done) {
        StringBuilder sb = new StringBuilder();
        if (outcome.passed()) {
            sb.append("<span class=\"pill ok\">PASS</span>");
            sb.append("<span><b>").append(escape(outcome.summary())).append("</b></span>");
            Browser.setClassName("resultBar", "result-bar pass");
        } else {
            sb.append("<span class=\"pill err\">FAIL</span>");
            sb.append("<span>").append(escape(outcome.summary())).append("</span>");
            Browser.setClassName("resultBar", "result-bar fail");
        }
        Browser.setHtml("resultBar", sb.toString());
    }

    private static String idleResultHtml() {
        Browser.setClassName("resultBar", "result-bar");
        return "<span class=\"pill muted\">idle</span><span>Run the code to begin.</span>";
    }

    private static void renderStepCounter(SimulationResult sim) {
        int current = sim.events().size();
        int total = Math.max(totalTraceSteps, current);
        Browser.setText("stepCounter", "step " + current + " / " + total);
    }

    private static String stepLabel(SimulationResult sim) {
        return "step " + sim.events().size() + " of " + Math.max(totalTraceSteps, sim.events().size());
    }

    private static String currentCounterText(SimulationResult sim, Level level) {
        Map.Entry<String, SharedType> primary = primaryDataDeclaration(level);
        if (primary == null) return "—";
        return String.valueOf(sim.finalGlobals().getOrDefault(primary.getKey(), 0));
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
            sb.append("<span class=\"level-num\">").append(pad2(i + 1)).append("</span>");
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

    private static void renderFooterNote(boolean passed) {
        Browser.setText("footerNote", passed
            ? "Solved. The bug is dead, long live the bug."
            : "Stuck? Open the hint panel above.");
    }

    private static void setObjectiveState(boolean attempted, boolean passed) {
        StringBuilder sb = new StringBuilder();
        if (!attempted) {
            sb.append("<span class=\"pill muted\">untested</span><span>Run the code to see the bug.</span>");
        } else if (passed) {
            sb.append("<span class=\"pill ok\">PASS</span><span>Both increments stuck. Nicely done.</span>");
        } else {
            sb.append("<span class=\"pill err\">FAIL</span><span>The bug is still there. Fix it.</span>");
        }
        Browser.setHtml("objectiveState", sb.toString());
        Browser.setClassName("objectiveStepRun", "oc-dot" + (attempted ? " on" : ""));
        Browser.setClassName("objectiveStepPass", "oc-dot" + (passed ? " on" : ""));
    }

    private static String introWithHighlights(String intro) {
        // Wrap numeric literals and keywords in <code> for visual rhythm
        String escaped = escape(intro.trim());
        // Wrap standalone integers
        escaped = escaped.replaceAll("\\b(\\d{2,})\\b", "<code>$1</code>");
        return escaped.replace("\n", "<br>");
    }

    private static void renderHintCard() {
        Level level = REGISTRY.get(currentIndex);
        List<String> hints = level.hints();
        Browser.setText("hintMeta", hintsRevealed + " / " + hints.size() + " revealed");

        if (hintsRevealed == 0) {
            Browser.setHtml("hintBody",
                "<p class=\"hint-empty\">Run a couple of times — hints unlock after two failed attempts.</p>");
            return;
        }
        StringBuilder sb = new StringBuilder("<ol class=\"hint-list\">");
        for (int i = 0; i < hintsRevealed; i++) {
            sb.append("<li>").append(escape(hints.get(i))).append("</li>");
        }
        sb.append("</ol>");
        Browser.setHtml("hintBody", sb.toString());
    }

    private static void maybeRevealHint() {
        Level level = REGISTRY.get(currentIndex);
        List<String> hints = level.hints();
        if (failsSinceLastPass < HINT_AFTER_FAILS) {
            renderHintCard();
            return;
        }
        if (hintsRevealed >= hints.size()) {
            renderHintCard();
            return;
        }
        hintsRevealed++;
        Browser.setHtmlAttribute("hintCard", "open", "");
        renderHintCard();
    }

    private static void toggleFullSource() {
        fullSourceVisible = !fullSourceVisible;
        renderFullSource();
    }

    private static void renderFullSource() {
        if (!fullSourceVisible) {
            Browser.setText("showSourceBtn", "Show full source");
            Browser.setClassName("fullSourcePanel", "full-source-panel hidden");
            Browser.setText("fullSource", "");
            return;
        }
        Browser.setText("showSourceBtn", "Hide full source");
        Browser.setClassName("fullSourcePanel", "full-source-panel");
        Level level = REGISTRY.get(currentIndex);
        String code = Browser.getEditorCode();
        Browser.setText("fullSource", level.fullSourceWith(code));
    }

    private static void downloadJava() {
        Level level = REGISTRY.get(currentIndex);
        String code = Browser.getEditorCode();
        Browser.downloadFile(level.lessonClassName() + ".java", level.fullSourceWith(code));
    }

    private static void renderSchedulePanel() {
        // Schedule controls were merged into chef cards (each card has a Step button)
        // Chef step buttons are rendered as part of the kitchen view.
    }

    private static void switchTab(int unused) {
        // unused; we handle tabs via setTab
    }

    private static void setTab(String tab) {
        activeTab = tab;
        boolean viz = tab.equals("viz");
        Browser.setClassName("tabViz", viz ? "tab on" : "tab");
        Browser.setClassName("tabLog", viz ? "tab" : "tab on");
        Browser.setHtmlAttribute("kitchenView", "style", viz ? "" : "display:none");
        Browser.setHtmlAttribute("eventLog", "style", viz ? "display:none" : "");
    }

    private static void showSuccessOverlay(Level level) {
        Browser.setText("overlayEyebrow",
            "Level " + pad2(currentIndex + 1) + " complete · " + stripPrefix(level.title()));
        Browser.setText("overlayTitle", successTitleFor(level));
        Browser.setText("overlayBody", successBodyFor(level));
        Browser.setText("overlayNextBtn",
            currentIndex < REGISTRY.size() - 1 ? "Next level →" : "Replay");
        Browser.setClassName("successOverlay", "overlay");
    }

    private static String successTitleFor(Level level) {
        return switch (level.id()) {
            case "lost-update" -> "You sealed the race.";
            case "monitor-counter" -> "Critical section secured.";
            case "many-chefs" -> "All four counted.";
            case "same-monitor" -> "One lock, one truth.";
            case "atomic-counter" -> "Lock-free, and it works.";
            case "atomic-read-modify-write" -> "One atomic op, not two.";
            case "atomic-add" -> "+5, atomically.";
            case "reentrant-lock" -> "Lock acquired, released, repeat.";
            case "deadlock-kitchen" -> "Both chefs serve.";
            case "lock-ordering" -> "Cycle broken.";
            case "wait-notify" -> "Signal received.";
            case "producer-consumer" -> "Orders flow.";
            case "virtual-blocking-sleep" -> "Park, don't block.";
            default -> "Level complete.";
        };
    }

    private static String successBodyFor(Level level) {
        return switch (level.id()) {
            case "lost-update" -> "Synchronizing the read+write makes it atomic — no other chef can sneak between the read and the write.";
            case "monitor-counter" -> "Read and write together are the critical section. One monitor, all updates inside it — the loop is now safe to run from any number of threads.";
            case "many-chefs" -> "The same lock that fixed two chefs scales to four. Synchronization serializes the work — that's the cost.";
            case "same-monitor" -> "Synchronizing on a different object is the same as not synchronizing at all. The state and its guard have to share an identity.";
            case "atomic-counter" -> "AtomicInteger.incrementAndGet does the read+add+write as a single CAS — no lock, no contention.";
            case "atomic-read-modify-write" -> "AtomicInteger isn't magic. get() and set() are each atomic, but the pair isn't. Use the operation that's atomic *as a whole*: incrementAndGet, addAndGet, compareAndSet.";
            case "atomic-add" -> "addAndGet(n) is just incrementAndGet generalized — atomically add anything you want, lock-free.";
            case "reentrant-lock" -> "ReentrantLock is the explicit cousin of synchronized: lock(), do work, unlock(). In real code wrap unlock in finally.";
            case "deadlock-kitchen" -> "Consistent lock ordering breaks the cycle. Every chef now grabs the oven first, then the fryer.";
            case "lock-ordering" -> "Same idea as the synchronized-deadlock level, made explicit with ReentrantLock. A global acquisition order — knife always before pan — is the only general fix.";
            case "wait-notify" -> "Object.wait() releases the monitor and parks the thread. Object.notify() wakes one waiter, which then re-acquires the monitor before returning. This is the primitive BlockingQueue is built on.";
            case "producer-consumer" -> "BlockingQueue is the cooperation primitive: producer parks when full, consumer parks when empty. The handoff is the lesson.";
            case "virtual-blocking-sleep" -> "Virtual threads park without holding an OS thread. The platform pool stays free for tasks that actually need to run, and blocking work — sleeps, I/O, queue puts — doesn't starve the pool.";
            default -> "Bug squashed. On to the next.";
        };
    }

    private static String statusLabel(Simulator.ChefSnapshot c) {
        if (c.done()) return "done";
        if (c.blockedOnLock() != null) return "blocked";
        if (c.lastEventDetail() == null) return "idle";
        String e = c.lastEventDetail();
        if (e.startsWith("reads")) return "reading";
        if (e.startsWith("writes")) return "writing";
        if (e.startsWith("acquires")) return "locking";
        if (e.startsWith("releases")) return "unlocking";
        if (e.startsWith("atomically increments")) return "incrementing";
        if (e.startsWith("atomically adds")) return "adding";
        if (e.startsWith("CAS")) return "CAS";
        if (e.startsWith("sets")) return "computing";
        if (e.startsWith("logs:")) return "logging";
        return "running";
    }

    private static String primaryLocalString(Map<String, Integer> locals) {
        if (locals == null || locals.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var entry : locals.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith("_t")) continue;
            if (!first) sb.append("  ");
            sb.append(name).append("=").append(entry.getValue());
            first = false;
        }
        if (sb.length() == 0) return "—";
        return sb.toString();
    }

    private static Map.Entry<String, SharedType> primaryDataDeclaration(Level level) {
        for (var e : level.sharedDeclarations().entrySet()) {
            if (e.getValue() instanceof SharedType.IntType
                || e.getValue() instanceof SharedType.AtomicIntegerType) {
                return e;
            }
        }
        return null;
    }

    private static String primaryTypeLabel(Level level) {
        var p = primaryDataDeclaration(level);
        if (p != null) return p.getValue().javaTypeName();
        // No int data → use first shared name's type
        for (var e : level.sharedDeclarations().entrySet()) {
            return e.getValue().javaTypeName();
        }
        return "void";
    }

    private static String stripPrefix(String title) {
        int colon = title.indexOf(':');
        return colon >= 0 ? title.substring(colon + 1).trim() : title;
    }

    private static String pad2(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}

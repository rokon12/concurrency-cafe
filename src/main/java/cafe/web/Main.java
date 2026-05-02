package cafe.web;

import cafe.core.Level;
import cafe.core.LostUpdateLevel;
import cafe.core.Outcome;
import cafe.core.sim.SimulationResult;

import java.util.List;

public final class Main {

    private static final int HINT_AFTER_FAILS = 2;

    private static final Level LEVEL = new LostUpdateLevel();
    private static int failsSinceLastPass;
    private static int hintsRevealed;
    private static boolean fullSourceVisible;

    public static void main(String[] args) {
        Browser.onClick("runBtn", Main::run);
        Browser.onClick("resetBtn", Main::reset);
        Browser.onClick("showSourceBtn", Main::toggleFullSource);

        renderIntro();
    }

    private static void renderIntro() {
        Browser.setText("levelTitle", LEVEL.title());
        Browser.setText("levelIntro", LEVEL.intro());
        Browser.setValue("editor", LEVEL.starterCode());
        Browser.setHtml("metrics", "<p>Press <strong>Run</strong> to simulate the kitchen.</p>");
        Browser.setHtml("eventLog", "<p>No events yet.</p>");
        Browser.setHtml("hintPanel", "");
        Browser.setClassName("statusBanner", "status-banner status-idle");
        Browser.setText("statusBanner", "Ready");
        fullSourceVisible = false;
        renderFullSource();
    }

    private static void reset() {
        failsSinceLastPass = 0;
        hintsRevealed = 0;
        renderIntro();
    }

    private static void run() {
        String code = Browser.getValue("editor");
        Outcome outcome = LEVEL.run(code);
        render(outcome);
        renderFullSource();

        if (outcome.passed()) {
            failsSinceLastPass = 0;
            hintsRevealed = 0;
        } else {
            failsSinceLastPass++;
            maybeRevealHint();
        }
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
            Browser.setText("statusBanner", "Level complete");
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
        String code = Browser.getValue("editor");
        Browser.setText("fullSource", LEVEL.fullSourceWith(code));
    }

    private static void maybeRevealHint() {
        List<String> hints = LEVEL.hints();
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

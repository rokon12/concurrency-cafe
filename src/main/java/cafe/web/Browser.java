package cafe.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

public final class Browser {
    private Browser() {
    }

    @JSFunctor
    public interface Callback extends JSObject {
        void run();
    }

    @JSFunctor
    public interface IntCallback extends JSObject {
        void run(int value);
    }

    @JSBody(
        params = { "id", "html" },
        script = "document.getElementById(id).innerHTML = html;"
    )
    public static native void setHtml(String id, String html);

    @JSBody(
        params = { "id", "text" },
        script = "document.getElementById(id).textContent = text;"
    )
    public static native void setText(String id, String text);

    @JSBody(
        params = { "id", "value" },
        script = "document.getElementById(id).value = value;"
    )
    public static native void setValue(String id, String value);

    @JSBody(
        params = { "id" },
        script = "return document.getElementById(id).value;"
    )
    public static native String getValue(String id);

    @JSBody(
        params = { "id", "className" },
        script = "document.getElementById(id).className = className;"
    )
    public static native void setClassName(String id, String className);

    @JSBody(
        params = { "id", "disabled" },
        script = "document.getElementById(id).disabled = disabled;"
    )
    public static native void setDisabled(String id, boolean disabled);

    @JSBody(
        params = { "id", "callback" },
        script = "document.getElementById(id).addEventListener('click', callback);"
    )
    public static native void onClick(String id, Callback callback);

    @JSBody(
        params = { "containerId", "attribute", "callback" },
        script =
            "document.getElementById(containerId).addEventListener('click', function(e) {" +
            "  var t = e.target;" +
            "  while (t && t.getAttribute && t.getAttribute(attribute) === null) { t = t.parentElement; }" +
            "  if (t && t.getAttribute) {" +
            "    var v = t.getAttribute(attribute);" +
            "    if (v !== null) callback(parseInt(v, 10));" +
            "  }" +
            "});"
    )
    public static native void onClickInside(String containerId, String attribute, IntCallback callback);
}

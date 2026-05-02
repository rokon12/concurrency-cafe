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
        params = { "key" },
        script = "var v = window.localStorage.getItem(key); return v === null ? null : v;"
    )
    public static native String getStorage(String key);

    @JSBody(
        params = { "key", "value" },
        script = "window.localStorage.setItem(key, value);"
    )
    public static native void setStorage(String key, String value);

    @JSBody(
        params = { "key" },
        script = "window.localStorage.removeItem(key);"
    )
    public static native void removeStorage(String key);

    @JSBody(
        params = { "prefix" },
        script =
            "var keys = [];" +
            "for (var i = 0; i < window.localStorage.length; i++) {" +
            "  var k = window.localStorage.key(i);" +
            "  if (k && k.indexOf(prefix) === 0) keys.push(k);" +
            "}" +
            "for (var j = 0; j < keys.length; j++) window.localStorage.removeItem(keys[j]);"
    )
    public static native void clearStoragePrefix(String prefix);

    @JSBody(
        params = { "message" },
        script = "return window.confirm(message);"
    )
    public static native boolean confirm(String message);

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

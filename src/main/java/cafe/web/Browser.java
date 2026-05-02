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
        params = { "id", "callback" },
        script = "document.getElementById(id).addEventListener('click', callback);"
    )
    public static native void onClick(String id, Callback callback);
}

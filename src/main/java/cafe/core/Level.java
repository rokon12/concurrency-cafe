package cafe.core;

import java.util.List;
import java.util.Map;

public interface Level {

    String id();

    String title();

    String intro();

    String starterCode();

    List<String> hints();

    Map<String, SharedType> sharedDeclarations();

    String fullSourceWith(String userCode);

    Outcome run(String code);
}

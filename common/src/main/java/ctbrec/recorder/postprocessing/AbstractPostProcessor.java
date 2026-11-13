package ctbrec.recorder.postprocessing;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public abstract class AbstractPostProcessor implements PostProcessor {

    private Map<String, String> config = new HashMap<>();

    private boolean enabled = true;

    @Override
    public String toString() {
        return getName();
    }
}

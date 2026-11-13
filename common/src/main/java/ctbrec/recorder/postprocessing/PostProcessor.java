package ctbrec.recorder.postprocessing;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

public interface PostProcessor extends Serializable {
    String getName();

    /**
     * Runs the post-processing code on the given recording
     *
     * @param ctx {@link PostProcessingContext}, which allows access to post-processing related objects
     * @return false to stop futher post-processing, true to continue
     * @throws IOException
     * @throws InterruptedException
     */
    boolean postprocess(PostProcessingContext ctx) throws IOException, InterruptedException;

    boolean isEnabled();

    void setEnabled(boolean enabled);

    Map<String, String> getConfig();

    void setConfig(Map<String, String> conf);
}

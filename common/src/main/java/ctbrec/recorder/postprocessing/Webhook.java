package ctbrec.recorder.postprocessing;

import java.io.IOException;

import ctbrec.NotImplementedExcetion;

public class Webhook extends AbstractPlaceholderAwarePostProcessor {

    public static final String URL = "webhook.url";
    public static final String HEADERS = "webhook.headers";
    public static final String METHOD = "webhook.method";
    public static final String DATA = "webhook.data";
    public static final String SECRET = "webhook.secret";

    @Override
    public String getName() {
        return "webhook";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException, InterruptedException {
        throw new NotImplementedExcetion();
    }

    @Override
    public String toString() {
        return (getName() + " " + getConfig().getOrDefault(Webhook.URL, "")).trim();
    }
}


package ctbrec.ui.tabs;

import java.io.IOException;

import ctbrec.recorder.postprocessing.AbstractPlaceholderAwarePostProcessor;
import ctbrec.recorder.postprocessing.PostProcessingContext;

public class DownloadPostprocessor extends AbstractPlaceholderAwarePostProcessor {

    @Override
    public String getName() {
        return "download renamer";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException, InterruptedException {
        // nothing really to do in here, we just inherit from AbstractPlaceholderAwarePostProcessor to use fillInPlaceHolders
        return true;
    }
}

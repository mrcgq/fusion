package ctbrec.recorder.postprocessing;

import java.io.IOException;

import ctbrec.NotImplementedExcetion;

public class CreateTimelineThumbs extends AbstractPlaceholderAwarePostProcessor {

    @Override
    public String getName() {
        return "create timeline thumbnails";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException, InterruptedException {
        // create 1 thumb every second with a width of 360 pixels and save it as jpeg with a 5-digit sequence number
        // ffmpeg -i <file> -vf 'fps=1,scale=360:-1' thumbs/out%05d.jpg
        throw new NotImplementedExcetion();
    }
}

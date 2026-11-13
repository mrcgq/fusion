package ctbrec.recorder.postprocessing;

import ctbrec.Config;
import ctbrec.Recording;
import ctbrec.recorder.RecordingManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoveKeepFileTest extends AbstractPpTest {

    @Test
    void testPostProcessWithSingleFile() throws IOException, InterruptedException {
        Recording rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(original);
        rec.setPostProcessedFile(postProcessed);
        rec.setStartDate(now);
        rec.setSingleFile(true);

        Config config = mockConfig();
        RecordingManager rm = new RecordingManager(config, Collections.emptyList());
        rm.add(rec);
        assertEquals(1, rm.getAll().size());
        RemoveKeepFile pp = new RemoveKeepFile();
        pp.postprocess(createPostProcessingContext(rec, rm, config));

        assertTrue(rec.getAbsoluteFile().exists());
        assertTrue(rec.getPostProcessedFile().exists());
        assertTrue(rm.getAll().isEmpty());
    }

    @Test
    void testGetName() {
        assertEquals("remove recording, but keep the files", new RemoveKeepFile().getName());
    }

}

package ctbrec.recorder.postprocessing;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import ctbrec.Config;
import ctbrec.Recording;

class CopyTest extends AbstractPpTest {

    @Test
    void testCopySingleFile() throws IOException, InterruptedException {
        Config config = mockConfig();
        Recording rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(original);
        rec.setStartDate(now);
        rec.setSingleFile(false);
        Copy pp = new Copy();
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));

        assertNotEquals(rec.getAbsoluteFile(), rec.getPostProcessedFile());
        assertTrue(original.exists());
        assertTrue(rec.getPostProcessedFile().exists());
    }

    @Test
    void testCopyDirectory() throws IOException, InterruptedException {
        Config config = mockConfig();
        Recording rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(originalDir);
        rec.setStartDate(now);
        rec.setSingleFile(false);
        Copy pp = new Copy();
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));

        assertNotEquals(rec.getAbsoluteFile(), rec.getPostProcessedFile());
        assertTrue(originalDir.exists());
        assertTrue(rec.getPostProcessedFile().exists());
    }

    @Test
    void testGetName() {
        assertEquals("create a copy", new Copy().getName());
    }
}

package ctbrec.recorder.postprocessing;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import ctbrec.Config;
import ctbrec.Recording;

class DeleteOriginalTest extends AbstractPpTest {

    @Test
    void testPostProcessWithSingleFile() throws IOException, InterruptedException {
        Recording rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(original);
        rec.setPostProcessedFile(postProcessed);
        rec.setStartDate(now);
        rec.setSingleFile(true);

        Config config = mockConfig();
        DeleteOriginal pp = new DeleteOriginal();
        pp.postprocess(createPostProcessingContext(rec, null, config));

        assertEquals(postProcessed, rec.getAbsoluteFile());
        assertTrue(rec.getAbsoluteFile().exists());
        assertFalse(original.exists());
    }

    @Test
    void testPostProcessWithDirectory() throws IOException, InterruptedException {
        Recording rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(originalDir);
        rec.setPostProcessedFile(postProcessedDir);
        rec.setStartDate(now);
        rec.setSingleFile(true);

        Config config = mockConfig();
        Files.createDirectories(postProcessedDir.toPath());
        DeleteOriginal pp = new DeleteOriginal();
        pp.postprocess(createPostProcessingContext(rec, null, config));

        assertEquals(postProcessedDir, rec.getAbsoluteFile());
        assertTrue(rec.getAbsoluteFile().exists());
        assertFalse(originalDir.exists());
    }

    @Test
    void testGetName() {
        assertEquals("delete original", new DeleteOriginal().getName());
    }

}

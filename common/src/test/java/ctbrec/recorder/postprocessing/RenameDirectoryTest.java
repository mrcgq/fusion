package ctbrec.recorder.postprocessing;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RenameDirectoryTest extends AbstractPpTest {

    @Test
    void testOriginalFileReplacement() throws IOException {
        Config config = mockConfig();
        Recording rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(originalDir);
        rec.setStartDate(now);
        rec.setSingleFile(false);
        Rename pp = new Rename();
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));

        Matcher m = Pattern.compile("Mockita_Boobilicious_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}").matcher(rec.getAbsoluteFile().getName());
        assertTrue(m.matches(), () -> rec.getAbsoluteFile().getName() + " does not match");
        assertEquals(rec.getAbsoluteFile(), rec.getPostProcessedFile());
        assertNotEquals(rec.getAbsoluteFile(), original);
    }

    @Test
    void absoluteFileShouldKeepBeingOriginalIfFilesDiffer() throws IOException {
        Config config = mockConfig();
        Model model = mockModel();
        Recording rec = mock(Recording.class);
        when(rec.getModel()).thenReturn(model);
        when(rec.getAbsoluteFile()).thenReturn(originalDir);
        when(rec.getPostProcessedFile()).thenReturn(postProcessedDir);
        when(rec.getStartDate()).thenReturn(now);
        doThrow(new RuntimeException("Unexpected call of setAbsoluteFile")).when(rec).setAbsoluteFile(any());

        Files.createDirectories(postProcessedDir.toPath());
        Rename pp = new Rename();
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));
    }
}

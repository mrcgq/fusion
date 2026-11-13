package ctbrec.recorder.postprocessing;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RenameSingleFileTest extends AbstractPpTest {

    @Test
    void testOriginalFileReplacement() throws IOException {
        Config config = mockConfig();
        Recording rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(original);
        rec.setStartDate(now);
        rec.setSingleFile(true);
        Rename pp = new Rename();
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));

        Matcher m = Pattern.compile("Mockita_Boobilicious_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.ts").matcher(rec.getAbsoluteFile().getName());
        assertTrue(m.matches());
        assertEquals(rec.getAbsoluteFile(), rec.getPostProcessedFile());
        assertNotEquals(rec.getAbsoluteFile(), original);
    }

    @Test
    void testEarlyExit() throws IOException {
        Config config = mockConfig();
        Model model = mockModel();
        Recording rec = mock(Recording.class);
        when(rec.getModel()).thenReturn(model);
        when(rec.getAbsoluteFile()).thenReturn(original);
        when(rec.getPostProcessedFile()).thenReturn(original);
        when(rec.getStartDate()).thenReturn(now);
        doThrow(new RuntimeException("Unexpected call of setAbsoluteFile")).when(rec).setAbsoluteFile(any());
        Rename pp = new Rename();
        pp.getConfig().put(Rename.FILE_NAME_TEMPLATE, original.getName());
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));
    }

    @Test
    void absoluteFileShouldKeepBeingOriginalIfFilesDiffer() throws IOException {
        Config config = mockConfig();
        Model model = mockModel();
        Recording rec = mock(Recording.class);
        when(rec.getModel()).thenReturn(model);
        when(rec.getAbsoluteFile()).thenReturn(original);
        when(rec.getPostProcessedFile()).thenReturn(postProcessed);
        when(rec.getStartDate()).thenReturn(now);
        doThrow(new RuntimeException("Unexpected call of setAbsoluteFile")).when(rec).setAbsoluteFile(any());
        Rename pp = new Rename();
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));
    }

    @Test
    void testToString() {
        Rename pp = new Rename();
        assertEquals("rename", pp.toString());

        pp.getConfig().put(Rename.FILE_NAME_TEMPLATE, Rename.DEFAULT);
        assertEquals("rename [" + Rename.DEFAULT + "]", pp.toString());
    }
}

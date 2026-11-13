package ctbrec.recorder.postprocessing;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MoveSingleFileTest extends AbstractPpTest {

    @Test
    void testOriginalFileReplacement() throws IOException {
        Config config = mockConfig();
        Recording rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(original);
        rec.setStartDate(now);
        rec.setSingleFile(true);

        Move pp = new Move();
        pp.getConfig().put(Move.PATH_TEMPLATE, new File(baseDir.toFile(), Move.DEFAULT).getAbsolutePath());
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));

        String fileSepator = Pattern.quote(File.separator);
        String regex = Pattern.quote(baseDir.toString()) + fileSepator + "Mockita_Boobilicious" + fileSepator + "\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}" + fileSepator + "original\\.ts";
        Matcher m = Pattern.compile(regex).matcher(rec.getAbsoluteFile().getCanonicalPath());
        assertTrue(m.matches());
        assertEquals(rec.getAbsoluteFile(), rec.getPostProcessedFile());
        assertNotEquals(rec.getAbsoluteFile(), original);
    }

    @Test
    void testEarlyExit() throws IOException {
        Model model = mockModel();
        Recording rec = mock(Recording.class);
        when(rec.getModel()).thenReturn(model);
        when(rec.getAbsoluteFile()).thenReturn(original);
        when(rec.getPostProcessedFile()).thenReturn(original);
        when(rec.getStartDate()).thenReturn(now);
        doThrow(new RuntimeException("Unexpected call of setAbsoluteFile")).when(rec).setAbsoluteFile(any());
        Move pp = new Move();
        Config config = mockConfig();
        pp.getConfig().put(Move.PATH_TEMPLATE, original.getParentFile().getCanonicalPath());
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));
    }

    @Test
    void absoluteFileShouldKeepBeingOriginalIfFilesDiffer() throws IOException {
        Model model = mockModel();
        Recording rec = mock(Recording.class);
        when(rec.getModel()).thenReturn(model);
        when(rec.getAbsoluteFile()).thenReturn(original);
        when(rec.getPostProcessedFile()).thenReturn(postProcessed);
        when(rec.getStartDate()).thenReturn(now);
        doThrow(new RuntimeException("Unexpected call of setAbsoluteFile")).when(rec).setAbsoluteFile(any());
        Move pp = new Move();
        Config config = mockConfig();
        pp.getConfig().put(Move.PATH_TEMPLATE, new File(baseDir.toFile(), Move.DEFAULT).getAbsolutePath());
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));
    }

    @Test
    void testToString() {
        Move pp = new Move();
        assertEquals("move", pp.toString());

        pp.getConfig().put(Move.PATH_TEMPLATE, Move.DEFAULT);
        assertEquals("move [" + Move.DEFAULT + "]", pp.toString());
    }
}

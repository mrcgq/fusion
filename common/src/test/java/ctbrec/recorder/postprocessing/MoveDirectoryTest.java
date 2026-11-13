package ctbrec.recorder.postprocessing;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MoveDirectoryTest extends AbstractPpTest {

    @Test
    void testOriginalFileReplacement() throws IOException {
        Config config = mockConfig();
        Recording rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(originalDir);
        rec.setStartDate(now);
        rec.setSingleFile(false);
        Move pp = new Move();
        pp.getConfig().put(Move.PATH_TEMPLATE, new File(baseDir.toFile(), Move.DEFAULT).getAbsolutePath());
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));

        String fileSepator = Pattern.quote(File.separator);
        String regex = Pattern.quote(baseDir.toString()) + fileSepator + "Mockita_Boobilicious" + fileSepator + "\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}" + fileSepator + "original";
        Matcher m = Pattern.compile(regex).matcher(rec.getAbsoluteFile().getCanonicalPath());
        assertTrue(m.matches());
        assertEquals(rec.getAbsoluteFile(), rec.getPostProcessedFile());
        assertNotEquals(rec.getAbsoluteFile(), original);
    }

    @Test
    void absoluteFileShouldKeepBeingOriginalIfFilesDiffer() throws IOException {
        Model model = mockModel();
        Recording rec = mock(Recording.class);
        when(rec.getModel()).thenReturn(model);
        when(rec.getAbsoluteFile()).thenReturn(originalDir);
        when(rec.getPostProcessedFile()).thenReturn(postProcessedDir);
        when(rec.getStartDate()).thenReturn(now);
        doThrow(new RuntimeException("Unexpected call of setAbsoluteFile")).when(rec).setAbsoluteFile(any());

        Files.createDirectories(postProcessedDir.toPath());
        Move pp = new Move();
        Config config = mockConfig();
        pp.getConfig().put(Move.PATH_TEMPLATE, new File(baseDir.toFile(), Move.DEFAULT).getAbsolutePath());
        assertDoesNotThrow(() -> pp.postprocess(createPostProcessingContext(rec, recordingManager, config)));
    }
}

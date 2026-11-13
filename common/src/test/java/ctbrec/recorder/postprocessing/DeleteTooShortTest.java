package ctbrec.recorder.postprocessing;

import ctbrec.Config;
import ctbrec.Recording;
import ctbrec.recorder.RecordingManager;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.VideoLengthDetector;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeleteTooShortTest extends AbstractPpTest {

    @Test
    void tooShortSingleFileRecShouldBeDeleted() throws IOException {
        testProcess(original);
    }

    @Test
    void tooShortDirectoryRecShouldBeDeleted() throws IOException {
        testProcess(originalDir);
    }

    private void testProcess(File original) throws IOException {
        Recording rec = createRec(original);
        Config config = mockConfig();
        MockedStatic<VideoLengthDetector> videoLengthDetectorMock = mockStatic(VideoLengthDetector.class);
        when(VideoLengthDetector.getLength(any())).thenReturn(Duration.ofSeconds(5));
        RecordingManager recordingManager = new RecordingManager(config, Collections.emptyList());
        recordingManager.add(rec);

        assertEquals(1, recordingManager.getAll().size());

        DeleteTooShort pp = new DeleteTooShort();
        pp.getConfig().put(DeleteTooShort.MIN_LEN_IN_SECS, "10");
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));

        assertFalse(rec.getAbsoluteFile().exists());
        assertFalse(original.exists());
        assertEquals(0, recordingManager.getAll().size());

        videoLengthDetectorMock.close();
    }

    @Test
    void testGetName() {
        assertEquals("delete too short", new DeleteTooShort().getName());
    }

    @Test
    void testDisabledWithSingleFile() throws IOException {
        Recording rec = createRec(original);
        Config config = mockConfig();
        RecordingManager recordingManager = new RecordingManager(config, Collections.emptyList());
        recordingManager.add(rec);
        assertEquals(1, recordingManager.getAll().size());

        DeleteTooShort pp = new DeleteTooShort();
        pp.getConfig().put(DeleteTooShort.MIN_LEN_IN_SECS, "0");
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));

        assertTrue(rec.getAbsoluteFile().exists());
        assertTrue(original.exists());
        assertEquals(1, recordingManager.getAll().size());
    }

    @Test
    void longEnoughVideoShouldStay() throws IOException {
        Recording rec = createRec(original);
        Config config = mockConfig();
        RecordingManager recordingManager = new RecordingManager(config, Collections.emptyList());
        recordingManager.add(rec);
        assertEquals(1, recordingManager.getAll().size());

        DeleteTooShort pp = new DeleteTooShort();
        pp.getConfig().put(DeleteTooShort.MIN_LEN_IN_SECS, "1");
        pp.postprocess(createPostProcessingContext(rec, recordingManager, config));

        assertTrue(rec.getAbsoluteFile().exists());
        assertTrue(original.exists());
        assertEquals(1, recordingManager.getAll().size());
    }

    private Recording createRec(File original) {
        RecordingProcess download = mock(RecordingProcess.class);
        Recording rec = new Recording();
        rec.setModel(mockModel());
        rec.setAbsoluteFile(original);
        rec.setPostProcessedFile(original);
        rec.setStartDate(now);
        rec.setSingleFile(true);
        rec.setRecordingProcess(download);
        return rec;
    }
}

package ctbrec.ui.tasks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ctbrec.Model;
import ctbrec.recorder.Recorder;

class StartRecordingTaskTest {

    private Recorder recorder;
    private Model model;

    @BeforeEach
    void setup() {
        recorder = mock(Recorder.class);
        model = mock(Model.class);
    }

    @Test
    void testExecuteSyncHappyPath() {
        CompletableFuture<Model> future = new StartRecordingTask(recorder).executeSync(model);
        try {
            assertEquals(future.get(), model);
        } catch (InterruptedException | ExecutionException e) {
            fail("happy path should not throw an " + e.getClass().getSimpleName());
        }
    }

    @Test
    void testExecuteSyncWithException() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        String exMsg = "recorder not available";
        doThrow(new IOException(exMsg)).when(recorder).addModel(model);
        CompletableFuture<Model> future = new StartRecordingTask(recorder).executeSync(model);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(getRootCause(ex) instanceof IOException);
        assertEquals(exMsg, getRootCause(ex).getMessage());
    }

    @Test
    void testExecuteHappyPath() {
        CompletableFuture<Model> future = new StartRecordingTask(recorder).execute(model);
        try {
            assertEquals(future.get(), model);
        } catch (InterruptedException | ExecutionException e) {
            fail("happy path should not throw an " + e.getClass().getSimpleName());
        }
    }

    @Test
    void testExecuteWithException() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        String exMsg = "recorder not available";
        doThrow(new IOException(exMsg)).when(recorder).addModel(model);
        CompletableFuture<Model> future = new StartRecordingTask(recorder).execute(model);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause() instanceof TaskExecutionException);
        assertTrue(getRootCause(ex) instanceof IOException);
        assertEquals(exMsg, getRootCause(ex).getMessage());
    }

    @Test
    void markedForLaterShouldGetStarted() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        when(model.isMarkedForLaterRecording()).thenReturn(true);
        new StartRecordingTask(recorder).executeSync(model);
        verify(model).setMarkedForLaterRecording(false);
        verify(recorder).addModel(model);
    }

    private Throwable getRootCause(Throwable t) {
        if (t.getCause() != null) {
            return getRootCause(t.getCause());
        } else {
            return t;
        }
    }
}

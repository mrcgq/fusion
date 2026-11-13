package ctbrec.recorder;

import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.Recording;
import ctbrec.io.HttpClient;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public interface Recorder {
    void addModel(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    void stopRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    void stopRecordingAt(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    void switchStreamSource(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    /**
     * Returns true, if a model is in the list of models to record. This does not reflect, if there currently is a recording running. The model might be offline
     * aswell.
     */
    boolean isTracked(Model model);

    /**
     * Get the list of all models, which are tracked by ctbrec
     *
     * @return a List of Model objects, which might be empty
     */
    List<Model> getModels();

    List<Recording> getRecordings() throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    void delete(Recording recording) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    /**
     * Pins a recording. A pinned recording cannot be deleted.
     *
     * @param recording
     * @throws IOException
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     */
    void pin(Recording recording) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    /**
     * Unpins a previously pinned recording. A pinned recording cannot be deleted.
     *
     * @param recording
     * @throws IOException
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     */
    void unpin(Recording recording) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    void shutdown(boolean immediately);

    void suspendRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    void resumeRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    boolean isSuspended(Model model);

    /**
     * Returns true, if a model is in the list of models to ignore priorities and immediately record.
     */
    public boolean isForcePriority(Model model);

    public void forcePriorityRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException;
    public void resumePriorityRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    boolean isMarkedForLaterRecording(Model model);

    void markForLaterRecording(Model model, boolean mark) throws InvalidKeyException, NoSuchAlgorithmException, IOException;

    /**
     * Returns only the models from getModels(), which are online
     *
     * @return
     */
    List<Model> getOnlineModels();

    /**
     * Returns only the models from getModels(), which are actually recorded right now (a recording process is currently running).
     *
     * @return
     * @throws IOException
     * @throws IllegalStateException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    default List<Model> getCurrentlyRecording() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        List<Recording> recordings = getRecordings();
        return getModels().stream().filter(m -> {
            for (Recording recording : recordings) {
                if (recording.getStatus() == Recording.State.RECORDING && recording.getModel().equals(m)) {
                    return true;
                }
            }
            return false;
        }).collect(Collectors.toList());
    }

    HttpClient getHttpClient();

    /**
     * Get the total size of the filesystem we are recording to
     *
     * @return the total size in bytes
     * @throws IOException
     */
    long getTotalSpaceBytes() throws IOException;

    /**
     * Get the free space left on the filesystem we are recording to
     *
     * @return the free space in bytes
     * @throws IOException
     */
    long getFreeSpaceBytes() throws IOException;

    /**
     * Regenerate the playlist for a recording. This is helpful, if the
     * playlist is corrupt or hasn't been generated for whatever reason
     *
     * @param recording
     * @throws IllegalStateException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws IOException
     */
    void rerunPostProcessing(Recording recording) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    /**
     * Tells the recorder, that the recording priority for the given model has changed
     *
     * @param model the model with changed priority. The new value can be retrieved with {@link Model#getPriority()}
     * @throws IOException
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     */
    void priorityChanged(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    void setNote(Recording rec, String note) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    /**
     * Pauses the recording of models entirely. The state of which models should be recorded and which are paused
     * is kept.
     *
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    void pause() throws InvalidKeyException, NoSuchAlgorithmException, IOException;

    /**
     * Resumes recording
     *
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    void resume() throws InvalidKeyException, NoSuchAlgorithmException, IOException;

    /**
     * Returns the number of models, which are on the recording list and not marked for later recording
     *
     * @return
     */
    int getModelCount();
    
    /**
     * Returns the number of model groups + ungrouped models
     *
     * @return
     */
    int getGroupedModelCount();

    Set<ModelGroup> getModelGroups();

    /**
     * Saves a model group. If the group already exists, it will be overwritten. Otherwise it will
     * be saved as a new group.
     *
     * @param group
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    void saveModelGroup(ModelGroup group) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    void deleteModelGroup(ModelGroup group) throws IOException, InvalidKeyException, NoSuchAlgorithmException;

    default Optional<ModelGroup> getModelGroup(Model model) {
        return getModelGroups().stream()
                .filter(mg -> mg.getModelUrls().contains(model.getUrl()))
                .findFirst();
    }
}

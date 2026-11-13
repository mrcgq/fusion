package ctbrec.notes;

import ctbrec.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class LocalModelNotesService implements ModelNotesService {

    private final Config config;

    @Override
    public Optional<String> loadModelNotes(String modelUrl) {
        return Optional.ofNullable(config.getSettings().modelNotes.get(modelUrl));
    }

    @Override
    public Map<String, String> loadAllModelNotes() throws IOException {
        return Collections.unmodifiableMap(config.getSettings().modelNotes);
    }

    @Override
    public void writeModelNotes(String modelUrl, String notes) {
        config.getSettings().modelNotes.put(modelUrl, notes);
        save();
    }

    @Override
    public void removeModelNotes(String modelUrl) {
        config.getSettings().modelNotes.remove(modelUrl);
        save();
    }

    private void save() {
        try {
            config.save();
        } catch (IOException e) {
            log.warn("Could not save the settings", e);
        }
    }
}

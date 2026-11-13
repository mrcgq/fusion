package ctbrec.notes;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public interface ModelNotesService {

    Optional<String> loadModelNotes(String modelUrl) throws IOException;

    Map<String, String> loadAllModelNotes() throws IOException;

    void writeModelNotes(String modelUrl, String notes) throws IOException;

    void removeModelNotes(String modelUrl) throws IOException;
}

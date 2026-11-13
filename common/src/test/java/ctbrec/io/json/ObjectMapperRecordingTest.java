package ctbrec.io.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ctbrec.Recording;
import ctbrec.io.json.dto.RecordingDto;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectMapperRecordingTest {

    @Test
    void recordingToJson() throws IOException {
        RecordingDto rec = prepareRecording();

        ObjectMapper mapper = ObjectMapperFactory.getMapper();
        String json = mapper.writeValueAsString(rec);

        JSONObject j = new JSONObject(json);
        assertEquals(rec.getId(), j.getString("id"));
        assertEquals(rec.getStartDate().toEpochMilli(), j.getLong("startDate"));
        assertEquals(rec.getStatus().name(), j.getString("status"));
        assertEquals(rec.getProgress(), j.getInt("progress"));
        assertEquals(rec.getSizeInByte(), j.getLong("sizeInByte"));
        assertEquals(rec.getMetaDataFile(), j.getString("metaDataFile"));
        assertEquals(rec.isSingleFile(), j.getBoolean("singleFile"));
        assertEquals(rec.isPinned(), j.getBoolean("pinned"));
        assertEquals(rec.getNote(), j.getString("note"));
        assertTrue(j.getJSONArray("associatedFiles").toList().containsAll(Set.of("a", "b", "c")));
        assertEquals(rec.getAbsoluteFile().getCanonicalPath(), j.getString("absoluteFile"));
        assertEquals(rec.getPostProcessedFile().toString(), j.getString("postProcessedFile"));
        assertEquals(rec.getSelectedResolution(), j.getInt("selectedResolution"));
    }

    @Test
    void jsonToRecording() throws JsonProcessingException {
        RecordingDto rec = prepareRecording();
        ObjectMapper mapper = ObjectMapperFactory.getMapper();
        String json = mapper.writeValueAsString(rec);

        RecordingDto deserialized = mapper.readValue(json, RecordingDto.class);
        assertEquals(rec, deserialized);
    }

    private RecordingDto prepareRecording() {
        String root = FileSystems.getDefault().getRootDirectories().iterator().next().toString();
        RecordingDto recording = new RecordingDto();
        recording.setId(UUID.randomUUID().toString());
        recording.setStartDate(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        recording.setStatus(Recording.State.RECORDING);
        recording.setProgress(42);
        recording.setSizeInByte(23456789);
        recording.setMetaDataFile(FileSystems.getDefault().getPath(root, "tmp", "meta.json").toString());
        recording.setSingleFile(true);
        recording.setPinned(true);
        recording.setNote("note");
        recording.setAssociatedFiles(Set.of("a", "b", "c"));
        recording.setAbsoluteFile(FileSystems.getDefault().getPath(root, "tmp", "test.rec.ts").toFile());
        recording.setPostProcessedFile(FileSystems.getDefault().getPath(root, "tmp", "pp.ts").toFile());
        recording.setSelectedResolution(2);
        return recording;
    }
}

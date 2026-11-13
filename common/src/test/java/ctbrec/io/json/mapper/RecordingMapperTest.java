package ctbrec.io.json.mapper;

import ctbrec.Recording;
import ctbrec.UnknownModel;
import ctbrec.io.json.dto.ModelDto;
import ctbrec.io.json.dto.RecordingDto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.io.File;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordingMapperTest {

    @Test
    void mapsAllFieldsFromRecordingToDto() {
        var model = new UnknownModel();
        model.setName("unknown");
        model.setUrl("https://site/model/unknown");

        var recording = new Recording();
        recording.setModel(model);
        recording.setId(UUID.randomUUID().toString());
        recording.setAbsoluteFile(new File("/tmp/recording"));
        recording.setStatus(Recording.State.RECORDING);
        recording.setSelectedResolution(1080);
        recording.setProgress(23);
        recording.setStartDate(Instant.now());
        recording.setNote("note");
        recording.setPinned(true);
        recording.setSingleFile(true);
        recording.setSizeInByte(87346587);
        recording.setPostProcessedFile(new File("/tmp/pp"));
        recording.setMetaDataFile("/tmp/meta");
        recording.setAssociatedFiles(Set.of("a", "b", "c"));

        var mapper = Mappers.getMapper(RecordingMapper.class);
        var mapped = mapper.toDto(recording);

        assertEquals(recording.getId(), mapped.getId());
        assertEquals(recording.getAbsoluteFile(), mapped.getAbsoluteFile());
        assertEquals(recording.getStatus(), mapped.getStatus());
        assertEquals(recording.getSelectedResolution(), mapped.getSelectedResolution());
        assertEquals(recording.getProgress(), mapped.getProgress());
        assertEquals(recording.getStartDate(), mapped.getStartDate());
        assertEquals(recording.getNote(), mapped.getNote());
        assertEquals(recording.isPinned(), mapped.isPinned());
        assertEquals(recording.isSingleFile(), mapped.isSingleFile());
        assertEquals(recording.getSizeInByte(), mapped.getSizeInByte());
        assertEquals(recording.getPostProcessedFile(), mapped.getPostProcessedFile());
        assertEquals(recording.getMetaDataFile(), mapped.getMetaDataFile());
        assertEquals(recording.getAssociatedFiles(), mapped.getAssociatedFiles());
        assertEquals(recording.getModel().getName(), mapped.getModel().getName());
        assertEquals(recording.getModel().getUrl(), mapped.getModel().getUrl().toString());

    }

    @Test
    void mapsAllFieldsFromDtoToRecording() {
        ModelDto model = new ModelDto();
        model.setType(UnknownModel.class.getName());
        model.setName("unknown");
        model.setUrl(URI.create("http://site/model/unknown"));

        var dto = new RecordingDto();
        dto.setId(UUID.randomUUID().toString());
        dto.setModel(model);
        dto.setAbsoluteFile(new File("/tmp/recording"));
        dto.setStatus(Recording.State.FINISHED);
        dto.setSelectedResolution(1080);
        dto.setProgress(23);
        dto.setStartDate(Instant.now());
        dto.setNote("note");
        dto.setPinned(true);
        dto.setSingleFile(true);
        dto.setSizeInByte(87346587);
        dto.setPostProcessedFile(new File("/tmp/pp"));
        dto.setMetaDataFile("/tmp/meta");
        dto.setAssociatedFiles(Set.of("a", "b", "c"));

        var mapper = Mappers.getMapper(RecordingMapper.class);
        var mapped = mapper.toRecording(dto);

        assertEquals(dto.getId(), mapped.getId());
        assertEquals(dto.getAbsoluteFile(), mapped.getAbsoluteFile());
        assertEquals(dto.getStatus(), mapped.getStatus());
        assertEquals(dto.getSelectedResolution(), mapped.getSelectedResolution());
        assertEquals(dto.getProgress(), mapped.getProgress());
        assertEquals(dto.getStartDate(), mapped.getStartDate());
        assertEquals(dto.getNote(), mapped.getNote());
        assertEquals(dto.isPinned(), mapped.isPinned());
        assertEquals(dto.isSingleFile(), mapped.isSingleFile());
        assertEquals(dto.getSizeInByte(), mapped.getSizeInByte());
        assertEquals(dto.getPostProcessedFile(), mapped.getPostProcessedFile());
        assertEquals(dto.getMetaDataFile(), mapped.getMetaDataFile());
        assertEquals(dto.getAssociatedFiles(), mapped.getAssociatedFiles());
        assertEquals(dto.getModel().getName(), mapped.getModel().getName());
        assertEquals(dto.getModel().getUrl().toString(), mapped.getModel().getUrl());

    }
}

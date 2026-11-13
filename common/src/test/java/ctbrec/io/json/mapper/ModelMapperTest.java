package ctbrec.io.json.mapper;

import ctbrec.SubsequentAction;
import ctbrec.io.json.dto.ModelDto;
import ctbrec.sites.chaturbate.Chaturbate;
import ctbrec.sites.chaturbate.ChaturbateModel;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelMapperTest {

    @Test
    void mapsAllFieldsFromModelToDto() {
        var model = new Chaturbate().createModel("foobarina");
        model.setDescription("description");
        model.setAddedTimestamp(Instant.now());
        model.setRecordUntil(Instant.now().plus(1, ChronoUnit.DAYS));
        model.setLastRecorded(Instant.now().minusSeconds(3600));
        model.setLastSeen(Instant.now().minusSeconds(60));
        model.setPriority(51);
        model.setSuspended(true);
        model.setForcePriority(false);
        model.setMarkedForLaterRecording(true);
        model.setRecordUntilSubsequentAction(SubsequentAction.REMOVE);
        model.setDisplayName("whatever");

        var mapper = Mappers.getMapper(ModelMapper.class);
        var mapped = mapper.toDto(model);

        assertEquals(model.getName(), mapped.getName());
        assertEquals(model.getUrl(), mapped.getUrl().toString());
        assertEquals(model.getDescription(), mapped.getDescription());
        assertEquals(model.getAddedTimestamp(), mapped.getAddedAt());
        assertEquals(model.getLastSeen(), mapped.getLastSeen());
        assertEquals(model.getLastRecorded(), mapped.getLastRecorded());
        assertEquals(model.getRecordUntil(), mapped.getRecordUntil());
        assertEquals(model.getRecordUntilSubsequentAction(), mapped.getRecordUntilSubsequentAction());
        assertEquals(model.getDisplayName(), mapped.getDisplayName());
        assertEquals(model.getPriority(), mapped.getPriority());
        assertEquals(model.getPreview(), mapped.getPreview().toString());
        assertEquals(model.isMarkedForLaterRecording(), mapped.isBookmarked());
        assertEquals(model.isSuspended(), mapped.isSuspended());
        assertEquals(model.isForcePriority(), mapped.isForcePriority());
    }

    @Test
    void mapsAllFieldsFromDtoToModel() {
        var dto = new ModelDto();
        dto.setType(ChaturbateModel.class.getName());
        dto.setName("foobarina");
        dto.setUrl(URI.create("https://foobarina.com"));
        dto.setPreview(URI.create("https://foobarina.com/portrait.jpg"));
        dto.setDescription("description");
        dto.setAddedAt(Instant.now());
        dto.setRecordUntil(Instant.now().plus(1, ChronoUnit.DAYS));
        dto.setLastRecorded(Instant.now().minusSeconds(3600));
        dto.setLastSeen(Instant.now().minusSeconds(60));
        dto.setPriority(51);
        dto.setSuspended(true);
        dto.setForcePriority(false);
        dto.setBookmarked(true);
        dto.setRecordUntilSubsequentAction(SubsequentAction.REMOVE);
        dto.setDisplayName("whatever");

        var mapper = Mappers.getMapper(ModelMapper.class);
        var mapped = mapper.toModel(dto);

        assertEquals(dto.getName(), mapped.getName());
        assertEquals(dto.getUrl().toString(), mapped.getUrl());
        assertEquals(dto.getDescription(), mapped.getDescription());
        assertEquals(dto.getAddedAt(), mapped.getAddedTimestamp());
        assertEquals(dto.getLastSeen(), mapped.getLastSeen());
        assertEquals(dto.getLastRecorded(), mapped.getLastRecorded());
        assertEquals(dto.getRecordUntil(), mapped.getRecordUntil());
        assertEquals(dto.getRecordUntilSubsequentAction(), mapped.getRecordUntilSubsequentAction());
        assertEquals(dto.getDisplayName(), mapped.getDisplayName());
        assertEquals(dto.getPriority(), mapped.getPriority());
        assertEquals(dto.getPreview().toString(), mapped.getPreview());
        assertEquals(dto.isBookmarked(), mapped.isMarkedForLaterRecording());
        assertEquals(dto.isSuspended(), mapped.isSuspended());
        assertEquals(dto.isForcePriority(), mapped.isForcePriority());
    }
}

package ctbrec.recorder.server.io.json.dto;

import ctbrec.ModelGroup;
import ctbrec.io.json.dto.ModelDto;
import ctbrec.io.json.dto.RecordingDto;
import lombok.Data;

@Data
public class RequestDto {
    private String action;
    private ModelDto model;
    private RecordingDto recording;
    private ModelGroup modelGroup;
}

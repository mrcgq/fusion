package ctbrec.io.json.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PostProcessorDto {
    private String type;
    private boolean enabled;
    private Map<String, String> config;
}

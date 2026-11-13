package ctbrec.io;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.ToString;

public class FlaresolverrResponse {
    @Getter
    protected String status;
    @Getter
    protected String message;
    
    public FlaresolverrResponse(JsonNode response) {
        status = response.get("status").asText();
        message = response.get("message").asText();
    }
    
    @Override
    public String toString() {
        return String.format("{status: %s, message: %s}", status, message);
    }
}

package ctbrec.sites.manyvids.wsmsg;

import java.util.UUID;
import java.util.function.BiConsumer;

import org.json.JSONObject;

public class JoinChat extends SendMessage {

    private String roomId;
    private String showId;

    public JoinChat(String roomId, String showId, BiConsumer<Message, JSONObject> responseConsumer) {
        super(responseConsumer);
        this.roomId = roomId;
        this.showId = showId;
        address = "api/ChatService";
        action = "join";
    }

    @Override
    public String toString() {
        JSONObject msg = build();
        JSONObject body = new JSONObject();
        body.put("connectionId", "");
        body.put("telemetryId", UUID.randomUUID().toString());
        body.put("roomId", roomId);
        body.put("showId", showId);
        body.put("joinPrivateShow", false);
        body.put("deviceSourceType", "DESKTOP");
        msg.put("body", body);
        return msg.toString();
    }
}

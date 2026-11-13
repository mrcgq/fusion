package ctbrec.sites.manyvids.wsmsg;

import java.util.Optional;
import java.util.function.BiConsumer;

import org.json.JSONObject;

public abstract class Message extends JSONObject {

    private BiConsumer<Message, JSONObject> responseConsumer;

    public Message(BiConsumer<Message, JSONObject> responseConsumer) {
        this.responseConsumer = responseConsumer;
    }

    public void handleResponse(JSONObject response) {
        Optional.ofNullable(responseConsumer).ifPresent(consumer -> consumer.accept(this, response));
    }
}

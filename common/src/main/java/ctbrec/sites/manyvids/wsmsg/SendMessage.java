package ctbrec.sites.manyvids.wsmsg;

import java.util.UUID;
import java.util.function.BiConsumer;

import org.json.JSONObject;

public class SendMessage extends Message {

    protected String address;
    protected String action;
    protected String replyAddress;

    public SendMessage(BiConsumer<Message, JSONObject> responseConsumer) {
        super(responseConsumer);
        replyAddress = UUID.randomUUID().toString();
    }

    public String getReplyAddress() {
        return replyAddress;
    }

    protected JSONObject build() {
        JSONObject msg = new JSONObject();
        msg.put("type", "send");
        msg.put("address", address);
        msg.put("replyAddress", replyAddress);
        JSONObject headers = new JSONObject();
        headers.put("action", action);
        msg.put("headers", headers);
        return msg;
    }
}

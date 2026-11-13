package ctbrec.sites.manyvids.wsmsg;

import java.util.function.BiConsumer;

import org.json.JSONObject;

public class RegisterMessage extends Message {

    protected String address;

    public RegisterMessage(String address, BiConsumer<Message, JSONObject> responseConsumer) {
        super(responseConsumer);
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public String toString() {
        JSONObject json = new JSONObject();
        json.put("type", "register");
        json.put("address", address);
        json.put("headers", new JSONObject());
        return json.toString();
    }
}

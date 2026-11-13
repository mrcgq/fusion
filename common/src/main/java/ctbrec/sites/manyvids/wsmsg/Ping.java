package ctbrec.sites.manyvids.wsmsg;

public class Ping extends Message {

    public Ping() {
        super(null);
        put("type", "ping");
    }
}

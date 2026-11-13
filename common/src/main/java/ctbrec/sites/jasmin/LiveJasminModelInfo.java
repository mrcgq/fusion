package ctbrec.sites.jasmin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LiveJasminModelInfo {
    private String websocketUrl;
    private String sbIp;
    private String sbHash;
    private String sessionId;
    private String jsm2session;
    private String performerId;
    private String displayName;
    private String clientInstanceId;
    private int status;
}

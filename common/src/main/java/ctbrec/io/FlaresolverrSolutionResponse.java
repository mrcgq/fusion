package ctbrec.io;

import com.fasterxml.jackson.databind.JsonNode;

import ctbrec.StringUtil;
import lombok.Getter;
import okhttp3.Cookie;

import java.time.Instant;
import java.util.*;

public class FlaresolverrSolutionResponse extends FlaresolverrResponse {
    @Getter
    protected String userAgent;
    @Getter
    protected Instant startTimestamp;
    @Getter
    protected Instant endTimestamp;
    @Getter
    protected String version;
    @Getter
    protected List<Cookie> cookies;

    FlaresolverrSolutionResponse(JsonNode response) {
        super(response);

        startTimestamp = Instant.ofEpochMilli(response.get("startTimestamp").asLong());
        endTimestamp = Instant.ofEpochMilli(response.get("endTimestamp").asLong());
        version = response.get("version").asText();
        
        var solution = response.get("solution");
        userAgent = solution.get("userAgent").asText();
        
        cookies = new ArrayList<Cookie>();

        for (var c : solution.get("cookies")) {
            //     "domain": c["domain"].lstrip('.'),
            //     "expiresAt": c["expiry"],
            //     "hostOnly": c["sameSite"].lower() == "strict",
            //     "httpOnly": c["httpOnly"],
            //     "name": c["name"],
            //     "path": c["path"],
            //     "persistent": False,
            //     "secure": c["secure"],
            //     "value": c["value"]

            var cb = new Cookie.Builder()
                .expiresAt(Optional.ofNullable(c.get("expires")).orElse(c.get("expiry")).asLong() * 1000) // seconds -> millis
                .name(c.get("name").asText())
                .path(c.get("path").asText())
                .value(c.get("value").asText())
                ;

            var domain = c.get("domain").asText().replaceFirst("\\.", "");

            // FIXME: is this correct?
            if (c.path("sameSite").asText("").equalsIgnoreCase("strict")) {
                cb.hostOnlyDomain(domain);
            } else {
                cb.domain(domain);
            }

            if (c.path("httpOnly").asBoolean(false))
                cb.httpOnly();

            if (c.path("secure").asBoolean(false))
                cb.secure();

            cookies.add(cb.build());
        }
    }

}

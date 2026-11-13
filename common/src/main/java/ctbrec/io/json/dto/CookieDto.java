package ctbrec.io.json.dto;

import lombok.Data;

@Data
public class CookieDto {
    private String domain;
    private long expiresAt;
    private boolean hostOnly;
    private boolean httpOnly;
    private String name;
    private String path;
    private boolean persistent;
    private boolean secure;
    private String value;
}

package ctbrec.io;

import java.io.IOException;

public class HttpException extends IOException {

    private final String url;
    private final int code;
    private final String msg;

    public HttpException(int code, String msg) {
        super(code + " - " + msg);
        this.url = "";
        this.code = code;
        this.msg = msg;
    }

    public HttpException(String url, int code, String msg) {
        super(code + " - " + msg + " - " + url);
        this.code = code;
        this.msg = msg;
        this.url = url;
    }

    public int getResponseCode() {
        return code;
    }

    public String getResponseMessage() {
        return msg;
    }

    public String getUrl() {
        return url;
    }
}

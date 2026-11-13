package ctbrec;

import okhttp3.Request;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RemoteService {

    protected void addHmacIfNeeded(byte[] msg, Request.Builder builder) throws InvalidKeyException, NoSuchAlgorithmException {
        if (Config.getInstance().getSettings().requireAuthentication) {
            byte[] key = Config.getInstance().getSettings().key;
            String hmac = Hmac.calculate(msg, key);
            builder.addHeader("CTBREC-HMAC", hmac);
        }
    }

    protected void addHmacIfNeeded(String msg, Request.Builder builder) throws InvalidKeyException, NoSuchAlgorithmException {
        addHmacIfNeeded(msg.getBytes(UTF_8), builder);
    }
}

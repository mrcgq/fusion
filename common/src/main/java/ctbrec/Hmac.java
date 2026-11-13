package ctbrec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Hmac {

    private Hmac() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(Hmac.class);

    public static byte[] generateKey() {
        LOG.debug("Generating HMAC key");
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[32];
        random.nextBytes(key);
        return Base64.getEncoder().encode(key);
    }

    public static String calculate(String msg, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException, IllegalStateException {
        return calculate(msg.getBytes(UTF_8), key);
    }

    public static String calculate(byte[] msg, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException, IllegalStateException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(keySpec);
        byte[] result = mac.doFinal(msg);
        String hmac = bytesToHex(result);
        return hmac;
    }

    public static boolean validate(String msg, byte[] key, String hmacToCheck) throws InvalidKeyException, NoSuchAlgorithmException, IllegalStateException {
        return Hmac.calculate(msg, key).equals(hmacToCheck);
    }

    public static boolean validate(byte[] msg, byte[] key, String hmacToCheck) throws InvalidKeyException, NoSuchAlgorithmException, IllegalStateException {
        return Hmac.calculate(msg, key).equals(hmacToCheck);
    }

    /**
     * Converts a byte array to a string
     *
     * @param bytes the byte array to convert to a hex string
     * @return string
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

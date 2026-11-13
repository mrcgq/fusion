package ctbrec.recorder.download.hls;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import okhttp3.Request;
import okhttp3.Response;

public class Crypto {

    private byte[] iv = new byte[16];
    private byte[] key = new byte[16];
    private Cipher cipher;
    private HttpClient client;

    public Crypto(String encryptionKeyUrl, HttpClient client) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        this.client = client;
        loadKey(encryptionKeyUrl);
        initCypher();
    }


    private void initCypher() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException {
        cipher = Cipher.getInstance("AES/CBC/NoPadding");
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
    }


    private void loadKey(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.execute(request)) {
            if(response.isSuccessful()) {
                key = response.body().bytes();
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    public byte[] decrypt(byte[] input) throws IllegalBlockSizeException, BadPaddingException {
        return cipher.doFinal(input);
    }


    public CipherInputStream wrap(InputStream in) {
        return new CipherInputStream(in, cipher);
    }
}

package ctbrec.io;

import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamRedirector implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(StreamRedirector.class);

    private InputStream in;
    private OutputStream out;

    public StreamRedirector(InputStream in, OutputStream out) {
        super();
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        try {
            int length = -1;
            byte[] buffer = new byte[1024*1024];
            while(in != null && (length = in.read(buffer)) >= 0) {
                out.write(buffer, 0, length);
            }
            LOG.trace("Stream redirect thread ended");
        } catch(Exception e) {
            LOG.warn("Couldn't redirect stream: {}", e.getLocalizedMessage());
        }
    }
}

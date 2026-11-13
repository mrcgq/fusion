package ctbrec.io;

import static java.util.concurrent.TimeUnit.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessStreamRedirector implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessStreamRedirector.class);

    private InputStream in;
    private OutputStream out;
    private boolean keepGoing = true;
    private ScheduledExecutorService executor;

    public ProcessStreamRedirector(ScheduledExecutorService executor, InputStream in, OutputStream out) {
        super();
        this.executor = executor;
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        try {
            int available = 0;
            while ((available = in.available()) > 0) {
                byte[] buffer = new byte[available];
                int length = in.read(buffer);
                out.write(buffer, 0, length);
            }
            if (keepGoing) {
                executor.schedule(this, 100, MILLISECONDS);
            }
        } catch (Exception e) {
            if (!Objects.equals(e.getMessage(), "Stream Closed")) {
                LOG.debug("Error while reading from process output stream: {}", e.getLocalizedMessage(), e);
            }
            keepGoing = false;
        }
    }

    public boolean keepGoing() {
        return keepGoing;
    }

    public void setKeepGoing(boolean keepGoing) {
        this.keepGoing = keepGoing;
    }
}

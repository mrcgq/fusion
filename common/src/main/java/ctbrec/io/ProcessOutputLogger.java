package ctbrec.io;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessOutputLogger implements Runnable {

    private final Logger log;
    private InputStream in;

    public ProcessOutputLogger(InputStream in, String loggerName) {
        this.in = in;
        log = LoggerFactory.getLogger(loggerName);
    }

    @Override
    public void run() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ( (line = r.readLine()) != null) {
                log.trace(line);
            }
            log.trace("Stream redirect thread ended");
        } catch(Exception e) {
            log.warn("Couldn't redirect stream: {}", e.getLocalizedMessage());
        }
    }
}

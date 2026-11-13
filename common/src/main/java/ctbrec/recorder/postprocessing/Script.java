package ctbrec.recorder.postprocessing;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.OS;
import ctbrec.Recording;
import ctbrec.io.StreamRedirector;
import ctbrec.recorder.download.ProcessExitedUncleanException;

public class Script extends AbstractPlaceholderAwarePostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Script.class);
    public static final String SCRIPT_EXECUTABLE = "script.executable";
    public static final String SCRIPT_PARAMS = "script.params";

    @Override
    public String getName() {
        return "execute script";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException, InterruptedException {
        Recording rec = ctx.getRecording();
        List<String> cmdline = buildCommandLine(ctx);
        Runtime rt = Runtime.getRuntime();
        String[] args = cmdline.toArray(new String[0]);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Running {}", Arrays.toString(args));
        }
        Process process = rt.exec(args, OS.getEnvironment());
        File logFile = File.createTempFile("execute_script_" + rec.getId() + "_", ".log");
        logFile.deleteOnExit();
        try (FileOutputStream logStream = new FileOutputStream(logFile)) {
            startLogging(process, logStream);
            int exitCode = process.waitFor();
            LOG.debug("Process finished with exit code {}", exitCode);
            if (exitCode != 0) {
                throw new ProcessExitedUncleanException("Script finished with exit code " + exitCode);
            }
        }
        return true;
    }

    private List<String> buildCommandLine(PostProcessingContext ctx) {
        String script = getConfig().getOrDefault(SCRIPT_EXECUTABLE, "somescript");
        String params = getConfig().getOrDefault(SCRIPT_PARAMS, "${absolutePath}");
        List<String> cmdline = new ArrayList<>();
        cmdline.add(script);
        String replacedParams = fillInPlaceHolders(params, ctx);
        Arrays.stream(replacedParams.split(" ")).forEach(cmdline::add);
        return cmdline;
    }

    private void startLogging(Process process, FileOutputStream logStream) {
        Thread std = new Thread(new StreamRedirector(process.getInputStream(), logStream));
        std.setName("Process stdout pipe");
        std.setDaemon(true);
        std.start();
        Thread err = new Thread(new StreamRedirector(process.getErrorStream(), logStream));
        err.setName("Process stderr pipe");
        err.setDaemon(true);
        err.start();
    }

    @Override
    public String toString() {
        return (getName() + " " + getConfig().getOrDefault(Script.SCRIPT_EXECUTABLE, "")).trim();
    }
}


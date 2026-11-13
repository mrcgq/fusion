package ctbrec.variableexpansion;

import ctbrec.Recording;
import ctbrec.StringUtil;
import ctbrec.variableexpansion.functions.AntlrSyntacErrorAdapter;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class RecordingVariableExpander extends AbstractVariableExpander {

    public RecordingVariableExpander(Recording rec, AntlrSyntacErrorAdapter errorListener) {
        super(errorListener);
        placeholderValueSuppliers.put("recordingNotes", getSanitizedRecordingNotes(rec));
        placeholderValueSuppliers.put("fileSuffix", getFileSuffix(rec));
        placeholderValueSuppliers.put("absolutePath", Optional.of(rec).map(Recording::getPostProcessedFile).map(File::getAbsolutePath));
        placeholderValueSuppliers.put("absoluteParentPath", Optional.of(rec).map(Recording::getPostProcessedFile).map(File::getParentFile).map(File::getAbsolutePath));
        placeholderValueSuppliers.put("epochSecond", ofNullable(rec.getStartDate()).map(Instant::getEpochSecond).map(l -> Long.toString(l))); // NOSONAR
        placeholderValueSuppliers.put("utcDateTime", getUtcDateTime(rec));
        placeholderValueSuppliers.put("localDateTime", getLocalDateTime(rec));
    }

    public String expand(String input) {
        return fillInPlaceHolders(input, placeholderValueSuppliers);
    }

    protected Optional<Object> getUtcDateTime(Recording rec) {
        return Optional.ofNullable(rec)
                .map(Recording::getStartDate)
                .map(i -> i.atZone(ZoneOffset.UTC));
    }

    protected Optional<Object> getLocalDateTime(Recording rec) {
        return Optional.ofNullable(rec)
                .map(Recording::getStartDate)
                .map(i -> i.atZone(ZoneId.systemDefault()));
    }

    private Optional<Object> getFileSuffix(Recording rec) {
        if (rec.isSingleFile()) {
            String filename = rec.getPostProcessedFile().getName();
            return Optional.of(filename.substring(filename.lastIndexOf('.') + 1));
        } else {
            return Optional.empty();
        }
    }

    private Optional<Object> getSanitizedRecordingNotes(Recording rec) {
        Optional<String> notes = ofNullable(rec.getNote());
        return notes.map(StringUtil::sanitize);
    }

}

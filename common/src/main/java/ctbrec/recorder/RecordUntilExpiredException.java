package ctbrec.recorder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class RecordUntilExpiredException extends PreconditionNotMetException {
    private final Instant until;

    public RecordUntilExpiredException(Instant until) {
        this.until = until;
    }

    @Override
    public String getMessage() {
        LocalDateTime dateTime = LocalDateTime.ofInstant(until, ZoneId.systemDefault());
        String date = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(dateTime);
        return "Recording expired at " + date;
    }
}

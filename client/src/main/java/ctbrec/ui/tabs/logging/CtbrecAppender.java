package ctbrec.ui.tabs.logging;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ctbrec.event.EventBusHolder;

public class CtbrecAppender extends ConsoleAppender<LoggingEvent> {

    @Override
    protected void append(LoggingEvent event) {
        EventBusHolder.BUS.post(event);
    }
}

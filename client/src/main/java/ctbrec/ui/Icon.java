package ctbrec.ui;

public enum Icon {

    BLANK_16(Icon.class.getResource("/16/blank.png").toExternalForm()),
    BOOKMARK_16(Icon.class.getResource("/16/bookmark-new.png").toExternalForm()),
    CHECK_16(Icon.class.getResource("/16/check-small.png").toExternalForm()),
    CLOCK_16(Icon.class.getResource("/16/clock.png").toExternalForm()),
    GROUP_16(Icon.class.getResource("/16/users.png").toExternalForm()),
    MEDIA_PLAYBACK_PAUSE_16(Icon.class.getResource("/16/media-playback-pause.png").toExternalForm()),
    MEDIA_RECORD_16(Icon.class.getResource("/16/media-record.png").toExternalForm()),
    MEDIA_FORCE_RECORD_16(Icon.class.getResource("/16/media-force-record.png").toExternalForm());

    private String url;

    private Icon(String url) {
        this.url = url;
    }

    public String url() {
        return url;
    }
}

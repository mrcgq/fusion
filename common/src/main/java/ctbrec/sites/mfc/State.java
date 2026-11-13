package ctbrec.sites.mfc;

import java.util.Optional;

public enum State {
    ONLINE("online"),
    CAMOFF("online - cam off"),
    RECORDING("recording"),
    INCLUDE("include"),
    EXCLUDE("exclude"),
    DELETE("delete"),
    AWAY("away"),
    PRIVATE("private"),
    GROUP_SHOW("group_show"),
    OFFLINE("offline"),
    UNKNOWN("unknown");

    final String literal;

    State(String literal) {
        this.literal = literal;
    }

    public static State of(Integer vs) {
        Integer s = Optional.ofNullable(vs).orElse(Integer.MAX_VALUE);
        return switch (s) {
            case 0 -> ONLINE;
            case 90 -> CAMOFF;
            case -4 -> RECORDING;
            case -3 -> INCLUDE;
            case -2 -> EXCLUDE;
            case -1 -> DELETE;
            case 2 -> AWAY;
            case 12, 91 -> PRIVATE;
            case 13 -> GROUP_SHOW;
            case 127 -> OFFLINE;
            default -> UNKNOWN;
        };
    }

    @Override
    public String toString() {
        return literal;
    }
}

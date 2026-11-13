package ctbrec.event;

public class NoSpaceLeftEvent extends Event {

    @Override
    public Type getType() {
        return Type.NO_SPACE_LEFT;
    }

    @Override
    public String getName() {
        return "No space left on device";
    }

    @Override
    public String getDescription() {
        return "No space left on device";
    }

    @Override
    public String[] getExecutionParams() {
        return new String[0];
    }

}

package ctbrec;

public class NotImplementedExcetion extends RuntimeException {

    public NotImplementedExcetion() {
        super("Not implemented");
    }

    public NotImplementedExcetion(String mesg) {
        super(mesg);
    }

}

package ctbrec;

public class NotLoggedInExcetion extends RuntimeException {

    public NotLoggedInExcetion() {
        super();
    }

    public NotLoggedInExcetion(String mesg) {
        super(mesg);
    }

}

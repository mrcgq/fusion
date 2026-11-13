package ctbrec;

public class Java {

    private Java() {}

    public static int version() {
        String specVersion = System.getProperty("java.specification.version");
        switch(specVersion) {
        case "1.7":
            return 7;
        case "1.8":
            return 8;
        default:
            try {
                return Integer.parseInt(specVersion);
            } catch (NumberFormatException nfe) {
                return 0;
            }
        }
    }
}

package ctbrec;

public class ModelNotFoundException extends RuntimeException {

    public ModelNotFoundException(String modelName) {
        super(modelName);
    }

    public ModelNotFoundException(String modelName, Exception e) {
        super(modelName, e);
    }
}

package ctbrec;

public class ModelIsIgnoredException extends RuntimeException {
    private final Model model;

    public ModelIsIgnoredException(Model model) {
        super("Model " + model.getDisplayName() + " [" + model.getUrl() + "] is ignored");
        this.model = model;
    }

    public Model getModel() {
        return model;
    }
}

package ctbrec.sites;

import ctbrec.Model;

public class ModelOfflineException extends RuntimeException {

    public ModelOfflineException(Model model) {
        super("Model " + model + " is offline");
    }
}

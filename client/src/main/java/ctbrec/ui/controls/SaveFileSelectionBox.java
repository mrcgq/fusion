package ctbrec.ui.controls;

import javafx.beans.value.ChangeListener;

import java.io.File;

public class SaveFileSelectionBox extends FileSelectionBox {

    public SaveFileSelectionBox() {
        disableValidation();
        useSaveDialog();
    }

    @Override
    protected void setFile(File file) {
        // nothing to do
    }

    @Override
    protected ChangeListener<String> textListener() {
        return (obs, o, n) -> {};
    }

    @Override
    protected String validate(File file) {
        return null;
    }
}

package ctbrec.ui.controls;

import java.io.File;

public class FileSelectionBox extends AbstractFileSelectionBox {
    public FileSelectionBox() {
    }

    public FileSelectionBox(String initialValue) {
        super(initialValue);
    }

    @Override
    protected String validate(File file) {
        if (isDisabled() || validationDisabled) {
            return null;
        }

        String msg = super.validate(file);
        if (msg != null) {
            return msg;
        } else if (!file.isFile()) {
            return "This is not a regular file";
        } else {
            return null;
        }
    }
}

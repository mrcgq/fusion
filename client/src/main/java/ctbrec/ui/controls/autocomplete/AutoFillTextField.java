package ctbrec.ui.controls.autocomplete;

import java.util.Optional;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class AutoFillTextField extends TextField {

    private EventHandler<ActionEvent> handler;
    private Suggester suggester;

    public AutoFillTextField(Suggester suggester) {
        this.suggester = suggester;
        addEventHandler(KeyEvent.KEY_RELEASED, evt -> {
            if (evt.getCode().isLetterKey() || evt.getCode().isDigitKey()) {
                autocomplete(false);
            } else if (evt.getCode() == KeyCode.ENTER) {
                if (getSelection().getLength() > 0) {
                    selectRange(0, 0);
                    insertText(lengthProperty().get(), ":");
                    positionCaret(lengthProperty().get());
                    evt.consume();
                } else {
                    handler.handle(new ActionEvent(this, null));
                }
            } else if (evt.getCode() == KeyCode.SPACE && evt.isControlDown()) {
                autocomplete(true);
            }
        });
    }

    private void autocomplete(boolean fulltextSearch) {
        String oldtext = getOldText();
        if (oldtext.isEmpty()) {
            return;
        }

        Optional<String> match;
        if (fulltextSearch) {
            match = suggester.fulltext(oldtext);
        } else {
            match = suggester.startsWith(oldtext);
        }

        if (match.isPresent()) {
            setText(match.get());
            int pos = oldtext.length();
            positionCaret(pos);
            selectRange(pos, match.get().length());
        }
    }

    private String getOldText() {
        if (getSelection().getLength() > 0) {
            return getText().substring(0, getSelection().getStart());
        } else {
            return getText();
        }
    }

    public void onActionHandler(EventHandler<ActionEvent> handler) {
        this.handler = handler;
    }
}

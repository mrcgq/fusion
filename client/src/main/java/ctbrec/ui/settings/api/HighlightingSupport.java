package ctbrec.ui.settings.api;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;

import static java.util.Optional.ofNullable;

class HighlightingSupport {

    private static final String CSS_HIGHLIGHT_CLASS = "setting-highlighted";

    private HighlightingSupport() {
    }

    static void highlightMatches(Category cat, String filter) {
        var node = cat.getGuiOrElse(() -> {
            throw new IllegalStateException(cat + " has not been loaded");
        });
        highlightMatches(node, filter);
        if (cat.hasSubCategories()) {
            for (Category sub : cat.getSubCategories()) {
                highlightMatches(sub, filter);
            }
        }
    }

    static void highlightMatches(Node node, String filter) {
        var contains = false;
        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                highlightMatches(child, filter);
            }
        }

        if (node instanceof Label lbl) {
            contains |= lbl.getText().toLowerCase().contains(filter);
            contains |= ofNullable(lbl.getTooltip()).map(Tooltip::getText).orElse("").toLowerCase().contains(filter);
            contains |= labelControlContains(lbl, filter);

            if (contains) {
                if (!node.getStyleClass().contains(CSS_HIGHLIGHT_CLASS)) {
                    node.getStyleClass().add(CSS_HIGHLIGHT_CLASS);
                }
            } else {
                node.getStyleClass().remove(CSS_HIGHLIGHT_CLASS);
            }
        }
    }

    private static boolean labelControlContains(Label lbl, String filter) {
        var contains = false;
        if (lbl.labelForProperty().get() != null) {
            var labeledNode = lbl.labelForProperty().get();
            contains |= labeledNode.toString().toLowerCase().contains(filter);
            if (labeledNode instanceof Control control) {
                contains |= ofNullable(control.getTooltip()).map(Tooltip::getText).orElse("").toLowerCase().contains(filter);
            }
            if (labeledNode instanceof TextInputControl textInput) {
                contains |= ofNullable(textInput.getText()).orElse("").toLowerCase().contains(filter);
            }
        }
        return contains;
    }

    static void removeHighlights(Category cat) {
        var node = cat.getGuiOrElse(Label::new);
        removeHighlights(node);
        if (cat.hasSubCategories()) {
            for (Category sub : cat.getSubCategories()) {
                removeHighlights(sub);
            }
        }
    }

    static void removeHighlights(Node gui) {
        if (gui != null) {
            if (gui instanceof Pane p) {
                for (Node n : p.getChildren()) {
                    removeHighlights(n);
                }
            }
            gui.getStyleClass().remove(CSS_HIGHLIGHT_CLASS);
        }
    }
}

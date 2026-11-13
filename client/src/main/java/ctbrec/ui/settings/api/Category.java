package ctbrec.ui.settings.api;

import static java.util.Optional.*;

import java.util.function.Supplier;

import ctbrec.StringUtil;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;

public class Category {

    protected String name;
    protected Group[] groups;
    protected Category[] subCategories;
    protected Node gui;

    private Category(String name, Group... groups) {
        this.name = name;
        this.groups = groups;
    }

    public Category(String name, Category[] subCategories) {
        this.name = name;
        this.subCategories = subCategories;
        groups = new Group[0];
    }

    public Category(String name, Node gui) {
        this.name = name;
        this.gui = gui;
        groups = new Group[0];
    }

    public static Category of(String name, Setting... settings) {
        return new Category(name, Group.of(settings));
    }

    public static Category of(String name, Group... groups) {
        return new Category(name, groups);
    }

    public static Category of(String name, Category... subCategories) {
        return new Category(name, subCategories);
    }

    public static Category of(String name, Node gui) {
        return new Category(name, gui);
    }

    String getName() {
        return name;
    }

    Group[] getGroups() {
        return groups;
    }

    Category[] getSubCategories() {
        return subCategories;
    }

    boolean hasGroups() {
        return groups != null && groups.length > 0 && !groups[0].isDefault();
    }

    boolean hasSubCategories() {
        return subCategories != null && subCategories.length > 0;
    }

    Node getGuiOrElse(Supplier<Node> guiFactory) {
        if (gui == null) {
            gui = guiFactory.get();
        }
        return gui;
    }

    @Override
    public String toString() {
        return name;
    }

    public boolean contains(String filter) {
        if (StringUtil.isBlank(filter)) {
            return true;
        }
        String q = filter.toLowerCase().trim();
        if(hasGroups() || hasSubCategories()) {
            return name.toLowerCase().contains(q)
                    || groupsContains(q)
                    || subCategoriesContains(q);
        } else {
            return name.toLowerCase().contains(q)
                    || guiContains(q);
        }

    }

    private boolean subCategoriesContains(String filter) {
        var contains = false;
        if (subCategories != null) {
            for (Category category : subCategories) {
                if (category.contains(filter)) {
                    contains = true;
                }
            }
        }
        return contains;
    }

    private boolean groupsContains(String filter) {
        var contains = false;
        if (groups != null) {
            for (Group group : groups) {
                if (group.contains(filter)) {
                    contains = true;
                }
            }
        }
        return contains;
    }

    private boolean guiContains(String filter) {
        if (gui != null) {
            return nodeContains(gui, filter);
        }
        return false;
    }

    private boolean nodeContains(Node node, String filter) {
        var contains = false;
        if (node instanceof Pane) {
            var pane = (Pane) node;
            for (Node child : pane.getChildren()) {
                contains |= nodeContains(child, filter);
            }
        }

        if (node instanceof Label) {
            Label lbl = (Label) node;
            contains |= lbl.getText().toLowerCase().contains(filter);
            contains |= ofNullable(lbl.getTooltip()).map(Tooltip::getText).orElse("").toLowerCase().contains(filter);
        }
        if (node instanceof Control) {
            contains |= ofNullable(((Control) node).getTooltip()).map(Tooltip::getText).orElse("").toLowerCase().contains(filter);
        }
        contains |= node.toString().toLowerCase().contains(filter);
        return contains;
    }
}

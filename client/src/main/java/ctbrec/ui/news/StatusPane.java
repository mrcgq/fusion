package ctbrec.ui.news;

import ctbrec.io.HtmlParser;
import ctbrec.ui.DesktopIntegration;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class StatusPane extends StackPane {

    TextArea content;
    Button reply;

    public StatusPane(Status status, DateTimeFormatter formatter) {
        String text = HtmlParser.getText("<div>" + status.getContent() + "</div>", "div");

        content = new TextArea(text);
        content.setMaxHeight(130);
        content.setEditable(false);
        content.setWrapText(true);
        getChildren().add(content);

        ZonedDateTime createdAt = status.getCreationTime();
        String creationTime = formatter.format(createdAt);
        var time = new Label(creationTime);
        time.setStyle("-fx-background-color: -fx-base");
        time.setOpacity(.7);
        time.setPadding(new Insets(3));
        time.setOnMouseEntered(evt -> time.setOpacity(1));
        time.setOnMouseExited(evt -> time.setOpacity(.7));
        getChildren().add(time);
        StackPane.setMargin(time, new Insets(5, 5, 5, 10));
        StackPane.setAlignment(time, Pos.BOTTOM_LEFT);
        var clip = new Rectangle(time.getWidth(), time.getHeight());
        clip.heightProperty().bind(time.heightProperty());
        clip.widthProperty().bind(time.widthProperty());
        clip.setArcHeight(5);
        clip.arcWidthProperty().bind(clip.arcHeightProperty());
        time.setClip(clip);

        reply = new Button("Reply");
        reply.setOnAction(evt -> DesktopIntegration.open(status.getUrl()));
        getChildren().add(reply);
        StackPane.setMargin(reply, new Insets(5, 5, 5, 5));
        StackPane.setAlignment(reply, Pos.BOTTOM_RIGHT);
    }

    @Override
    protected void layoutChildren() {
        ObservableList<Node> childrenUnmodifiable = content.getChildrenUnmodifiable();
        for (Node node : childrenUnmodifiable) {
            if (node instanceof ScrollPane scrollPane) {
                Set<Node> nodes = scrollPane.lookupAll(".scroll-bar");
                for (final Node child : nodes) {
                    if (child instanceof ScrollBar sb && sb.getOrientation() == Orientation.VERTICAL) {
                        if (sb.isVisible()) {
                            StackPane.setMargin(reply, new Insets(5, 22, 5, 5));
                        } else {
                            StackPane.setMargin(reply, new Insets(5, 5, 5, 5));
                        }
                    }
                }
            }
        }
        super.layoutChildren();
    }
}

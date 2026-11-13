package ctbrec.ui.action;

import static ctbrec.ui.controls.Dialogs.*;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Model;
import ctbrec.event.EventBusHolder;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.TipDialog;
import javafx.scene.Cursor;
import javafx.scene.Node;

public class TipAction {

    private static final Logger LOG = LoggerFactory.getLogger(TipAction.class);

    private Model model;
    private Node source;

    public TipAction(Model model, Node source) {
        this.model = model;
        this.source = source;
    }

    public void execute() {
        source.setCursor(Cursor.WAIT);
        try {
            var site = model.getSite();
            var tipDialog = new TipDialog(source.getScene(), site);
            tipDialog.showAndWait();
            String tipText = tipDialog.getResult();
            if (tipText != null) {
                var df = new DecimalFormat("0.##");
                try {
                    Number tokens = df.parse(tipText);
                    SiteUiFactory.getUi(site).login();
                    model.receiveTip(tokens.doubleValue());
                    Map<String, Object> event = new HashMap<>();
                    event.put("event", "tokens.sent");
                    event.put("amount", tokens.doubleValue());
                    EventBusHolder.BUS.post(event);
                } catch (IOException ex) {
                    LOG.error("An error occurred while sending tip", ex);
                    showError(source.getScene(), "Couldn't send tip", "An error occurred while sending tip:", ex);
                } catch (Exception ex) {
                    showError(source.getScene(), "Couldn't send tip", "You entered an invalid amount of tokens", ex);
                }
            }
        } finally {
            source.setCursor(Cursor.DEFAULT);
        }
    }
}

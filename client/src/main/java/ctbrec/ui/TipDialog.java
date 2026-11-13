package ctbrec.ui;

import java.text.DecimalFormat;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.GlobalThreadPool;
import ctbrec.sites.Site;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

public class TipDialog extends TextInputDialog {

    private static final Logger LOG = LoggerFactory.getLogger(TipDialog.class);
    private Site site;
    private Scene parent;

    public TipDialog(Scene parent, Site site) {
        this.parent = parent;
        this.site = site;
        setTitle("Send Tip");
        loadTokenBalance();
        setHeaderText("Loading token balance…");
        setContentText("Amount of tokens to tip:");
        setResizable(true);
        getEditor().setDisable(true);
        if (parent != null) {
            Stage stage = (Stage) getDialogPane().getScene().getWindow();
            stage.getScene().getStylesheets().addAll(parent.getStylesheets());
        }
    }

    private void loadTokenBalance() {
        Task<Double> task = new Task<Double>() {
            @Override
            protected Double call() throws Exception {
                if (!Objects.equals(System.getenv("CTBREC_DEV"), "1")) {
                    SiteUiFactory.getUi(site).login();
                    return site.getTokenBalance();
                } else {
                    return 1_000_000d;
                }
            }

            @Override
            protected void done() {
                try {
                    double tokens = get();
                    Platform.runLater(() -> {
                        if (tokens <= 0) {
                            String msg = "Do you want to buy tokens now?\n\nIf you agree, " + site.getName() + " will open in a browser. "
                                    + "The used address is an affiliate link, which supports me, but doesn't cost you anything more.";
                            Alert buyTokens = new AutosizeAlert(Alert.AlertType.CONFIRMATION, msg, parent, ButtonType.NO, ButtonType.YES);
                            buyTokens.setTitle("No tokens");
                            buyTokens.setHeaderText("You don't have any tokens");
                            buyTokens.showAndWait();
                            TipDialog.this.close();
                            if (buyTokens.getResult() == ButtonType.YES) {
                                DesktopIntegration.open(site.getAffiliateLink());
                            }
                        } else {
                            getEditor().setDisable(false);
                            DecimalFormat df = new DecimalFormat("0.##");
                            setHeaderText("Current token balance: " + df.format(tokens));
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    handleExcetion(e);
                } catch (ExecutionException e) {
                    handleExcetion(e);
                }
            }
        };
        GlobalThreadPool.submit(task);
    }

    private void handleExcetion(Exception e) {
        LOG.error("Couldn't retrieve account balance", e);
        showErrorDialog(e);
    }

    private void showErrorDialog(Throwable throwable) {
        Platform.runLater(() -> {
            Alert alert = new AutosizeAlert(Alert.AlertType.ERROR, parent);
            alert.setTitle("Error");
            alert.setHeaderText("Couldn't retrieve token balance");
            alert.setContentText("Error while loading your token balance: " + throwable.getLocalizedMessage());
            alert.showAndWait();
            TipDialog.this.close();
        });
    }

}

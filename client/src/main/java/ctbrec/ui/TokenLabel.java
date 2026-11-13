package ctbrec.ui;

import com.google.common.eventbus.Subscribe;
import ctbrec.GlobalThreadPool;
import ctbrec.event.EventBusHolder;
import ctbrec.sites.Site;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class TokenLabel extends Label {

    private static final Logger LOG = LoggerFactory.getLogger(TokenLabel.class);
    private double tokens = -1;
    private final Site site;

    public TokenLabel(Site site) {
        this.site = site;
        setText("Tokens: loading…");
        EventBusHolder.BUS.register(new Object() {
            @Subscribe
            public void tokensUpdates(Map<String, Object> e) {
                if (Objects.equals("tokens", e.get("event"))) {
                    tokens = (double) e.get("amount");
                    updateText();
                } else if (Objects.equals("tokens.sent", e.get("event"))) {
                    tokens -= (double) e.get("amount");
                    updateText();
                }
            }
        });
    }

    public void decrease(int tokens) {
        this.tokens -= tokens;
        updateText();
    }

    public void update(double tokens) {
        this.tokens = tokens;
        updateText();
    }

    private void updateText() {
        Platform.runLater(() -> {
            DecimalFormat df = new DecimalFormat("0.##");
            setText("Tokens: " + df.format(tokens));
        });
    }

    public void loadBalance() {
        if (tokens != -1) {
            return;
        }
        Task<Double> task = new Task<>() {
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
                    tokens = get();
                    update(tokens);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    handleException(e);
                } catch (ExecutionException e) {
                    handleException(e);
                }
            }

            private void handleException(Exception e) {
                LOG.error("Couldn't retrieve account balance", e);
                Platform.runLater(() -> {
                    setText("Tokens: error");
                    setTooltip(new Tooltip(e.getMessage()));
                });
            }
        };
        GlobalThreadPool.submit(task);
    }
}

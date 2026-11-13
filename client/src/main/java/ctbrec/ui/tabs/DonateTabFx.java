package ctbrec.ui.tabs;



import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public class DonateTabFx extends Tab {

    public DonateTabFx() {
        setClosable(false);
        setText("Donate");
        var container = new BorderPane();
        container.setPadding(new Insets(10));
        setContent(container);

        var headerVbox = new VBox(10);
        headerVbox.setAlignment(Pos.CENTER);
        var beer = new Label("Buy me a drink?!");
        beer.setFont(new Font(36));
        var desc = new Label("If you like this software and want to buy me some MtDew or Energy Drinks(boost ctbrec dev productivity! :), Beer or Pizza, here are some possibilities!");
        desc.setFont(new Font(24));
        headerVbox.getChildren().addAll(beer, desc);
        var header = new HBox();
        header.setAlignment(Pos.CENTER);
        header.getChildren().add(headerVbox);
        header.setPadding(new Insets(20, 0, 0, 0));
        container.setTop(header);

        var prefWidth = 360;
        var bitcoinAddress = new TextField("bc1q7fvtkx8wklvd4zttsec7sfgxqh9zadk0x236lt");
        bitcoinAddress.setEditable(false);
        bitcoinAddress.setPrefWidth(prefWidth);
        var bitcoinQrCode = new ImageView(getClass().getResource("/html/bitcoin-address.png").toString());
        var bitcoinLabel = new Label("Bitcoin");
        bitcoinLabel.setGraphic(new ImageView(getClass().getResource("/html/bitcoin.png").toString()));
        var bitcoinBox = new VBox(5);
        bitcoinBox.setAlignment(Pos.TOP_CENTER);
        bitcoinBox.getChildren().addAll(bitcoinLabel, bitcoinAddress, bitcoinQrCode);

        var ethereumAddress = new TextField("0x2e687A5628ff16c8f9624A914C1f727000089C3A");
        ethereumAddress.setEditable(false);
        ethereumAddress.setPrefWidth(prefWidth);
        var ethereumQrCode = new ImageView(getClass().getResource("/html/ethereum-address.png").toString());
        var ethereumLabel = new Label("Ethereum");
        ethereumLabel.setGraphic(new ImageView(getClass().getResource("/html/ethereum.png").toString()));
        var ethereumBox = new VBox(5);
        ethereumBox.setAlignment(Pos.TOP_CENTER);
        ethereumBox.getChildren().addAll(ethereumLabel, ethereumAddress, ethereumQrCode);

        var moneroAddress = new TextField("47tjD1z63wu3FEnDCvWnFaRAZbpDKc3Ys1WCbgzvB2Gg8XbqU8bARpcCC37mWzuWBAeZPu2UGY4TAcYGhb6fptoTR8X9vjc");
        moneroAddress.setEditable(false);
        moneroAddress.setPrefWidth(prefWidth);
        var moneroQrCode = new ImageView(getClass().getResource("/html/monero-address.png").toString());
        var moneroLabel = new Label("Monero");
        moneroLabel.setGraphic(new ImageView(getClass().getResource("/html/monero.png").toString()));
        var moneroBox = new VBox(5);
        moneroBox.setAlignment(Pos.TOP_CENTER);
        moneroBox.getChildren().addAll(moneroLabel, moneroAddress, moneroQrCode);

        var bottomBox = new HBox(5);
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setSpacing(50);
        bottomBox.getChildren().addAll(bitcoinBox, ethereumBox, moneroBox);
        VBox.setMargin(bottomBox, new Insets(20, 0, 0, 0));

        var centerBox = new VBox(50);
        centerBox.getChildren().addAll(bottomBox);
        container.setCenter(centerBox);
    }
}

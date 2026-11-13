package ctbrec.ui.event;

import ctbrec.event.Action;
import ctbrec.event.Event;
import ctbrec.event.EventHandlerConfiguration.ActionConfiguration;
import javafx.scene.media.AudioClip;

import java.io.File;
import java.net.URL;

public class PlaySound extends Action {

    private URL url;
    private double volume = 1.0d;

    public PlaySound() {
        name = "play sound";
    }

    public PlaySound(URL url, double volume) {
        this();
        this.url = url;
        this.volume = volume;
    }

    @Override
    public void accept(Event evt) {
        var clip = new AudioClip(url.toString());
        clip.setVolume(volume);
        clip.play();
    }

    @Override
    public void configure(ActionConfiguration config) throws Exception {
        var file = new File((String) config.getConfiguration().get("file"));
        volume = Double.parseDouble(String.valueOf(config.getConfiguration().getOrDefault("volume", "1.0")));
        url = file.toURI().toURL();
    }
}

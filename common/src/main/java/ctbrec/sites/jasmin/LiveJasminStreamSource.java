package ctbrec.sites.jasmin;

import ctbrec.recorder.download.StreamSource;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LiveJasminStreamSource extends StreamSource {
    private String rtmpUrl;
    private String streamName;
    private String streamId;
    private LiveJasminStreamRegistration streamRegistration;
}

package ctbrec.recorder.download;

import lombok.Getter;
import lombok.Setter;

import java.text.DecimalFormat;

@Getter
@Setter
public class StreamSource implements Comparable<StreamSource> {
    public static final int ORIGIN = Integer.MAX_VALUE - 1;
    public static final int UNKNOWN = Integer.MAX_VALUE;

    private int bandwidth;
    private int width;
    private int height;
    private String mediaPlaylistUrl;

    @Override
    public String toString() {
        DecimalFormat df = new DecimalFormat("0.00");
        float mbit = bandwidth / 1024.0f / 1024.0f;
        if (height == UNKNOWN) {
            return "unknown resolution (" + df.format(mbit) + " Mbit/s)";
        } else if (height == ORIGIN) {
            return "Origin";
        } else {
            if (height * width > 0) {
                return height + "p (" + df.format(mbit) + " Mbit/s) [" + df.format((float) bandwidth / height / width) + " bit/pix]";
            }
            return height + "p (" + df.format(mbit) + " Mbit/s)";
        }
    }

    /**
     * First compares the sources by height, if the heights are the same
     * it compares the bandwidth values
     */
    @Override
    public int compareTo(StreamSource o) {
        int heightDiff = height - o.height;
        if (heightDiff != 0 && height != UNKNOWN && o.height != UNKNOWN) {
            return heightDiff;
        } else {
            return bandwidth - o.bandwidth;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + bandwidth;
        result = prime * result + height;
        result = prime * result + ((mediaPlaylistUrl == null) ? 0 : mediaPlaylistUrl.hashCode());
        result = prime * result + width;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        StreamSource other = (StreamSource) obj;
        if (bandwidth != other.bandwidth)
            return false;
        if (height != other.height)
            return false;
        if (mediaPlaylistUrl == null) {
            if (other.mediaPlaylistUrl != null)
                return false;
        } else if (!mediaPlaylistUrl.equals(other.mediaPlaylistUrl))
            return false;
        return width == other.width;
    }
}

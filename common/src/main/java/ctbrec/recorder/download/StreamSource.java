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
     * Comparable pixel count of this source. Vertical streams (e.g. 720x960) must not be ranked
     * below a horizontal one with a bigger height but fewer pixels, so the ranking uses the pixel
     * count and falls back to a height based scale for sources without width.
     */
    public long getQualityScore() {
        if (height == UNKNOWN || height == ORIGIN) {
            // sentinel values, only meaningful for the historic comparison path
            return height;
        }
        if (width > 0 && height > 0) {
            return (long) width * (long) height;
        }
        // no width available: approximate with the height, kept below any real pixel count
        return (long) height * 1000L;
    }

    /**
     * Rank two real resolutions by pixel count first, then height, then bandwidth, so that the
     * last element of a sorted list is always the highest quality variant the stream offers.
     * Sources carrying the UNKNOWN/ORIGIN sentinel keep the historic comparison untouched, so the
     * sites that hand out those sentinels are not affected by the pixel based ranking.
     */
    @Override
    public int compareTo(StreamSource o) {
        boolean bothReal = height != UNKNOWN && o.height != UNKNOWN
                && height != ORIGIN && o.height != ORIGIN;
        if (bothReal) {
            long diff = getQualityScore() - o.getQualityScore();
            if (diff != 0) {
                return diff < 0 ? -1 : 1;
            }
            int heightDiff = Integer.compare(height, o.height);
            if (heightDiff != 0) {
                return heightDiff;
            }
            return Integer.compare(bandwidth, o.bandwidth);
        }
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

package ctbrec.sites.stripchat;

import ctbrec.io.HttpClient;
import ctbrec.recorder.download.hls.MergedFfmpegHlsDownload;

/**
 * Stripchat 下载器：继承 ffmpeg 合并下载；MOUFLON 播放列表重写与 psch/pkey 继承
 * 已在 AbstractHlsDownload + StripchatPlaylist 中统一处理，这里只做类型占位，
 * 便于日后站点特化（如重试策略/带宽参数）而不改公共通路。
 */
public class StripchatFfmpegHlsDownload extends MergedFfmpegHlsDownload {

    public StripchatFfmpegHlsDownload(HttpClient client) {
        super(client);
    }
}

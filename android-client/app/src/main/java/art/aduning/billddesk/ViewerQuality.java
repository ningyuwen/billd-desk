package art.aduning.billddesk;

import android.content.Context;
import android.content.SharedPreferences;

/** Viewer requests; the host and network determine the actual delivered picture. */
final class ViewerQuality {
    static final int[] HEIGHTS = {720, 1080, 1440, 2160};
    static final int[] FRAME_RATES = {30, 60};
    static final int[] BITRATES_KBPS = {6000, 12000, 20000, 30000};
    final int height, fps, bitrateKbps;

    ViewerQuality(int height, int fps, int bitrateKbps) {
        this.height = supported(HEIGHTS, height, 1440);
        this.fps = supported(FRAME_RATES, fps, 60);
        this.bitrateKbps = supported(BITRATES_KBPS, bitrateKbps, 12000);
    }
    private static int supported(int[] options, int value, int fallback) {
        for (int option : options) if (option == value) return value;
        return fallback;
    }
    static ViewerQuality load(Context context) {
        SharedPreferences p = context.getSharedPreferences("viewer_quality", Context.MODE_PRIVATE);
        return new ViewerQuality(p.getInt("height", 1440), p.getInt("fps", 60), p.getInt("bitrate_kbps", 12000));
    }
    void save(Context context) {
        context.getSharedPreferences("viewer_quality", Context.MODE_PRIVATE).edit()
                .putInt("height", height).putInt("fps", fps).putInt("bitrate_kbps", bitrateKbps).apply();
    }
    String label() { return height + "P · " + fps + " 帧/秒 · 最高 " + (bitrateKbps / 1000) + " Mbps"; }
}

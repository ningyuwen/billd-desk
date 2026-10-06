package art.aduning.billddesk;

import android.app.Application;

public final class DeskApplication extends Application {
    private DeskEngine engine;
    @Override public void onCreate() {
        super.onCreate();
        engine = new DeskEngine(this);
    }
    public DeskEngine engine() { return engine; }
}

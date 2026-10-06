package art.aduning.billddesk;

import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.webrtc.SurfaceViewRenderer;

/** Two fingers navigate the local picture; one finger controls the remote device. */
final class RemoteViewport implements View.OnTouchListener {
    interface RemoteInput {
        int frameWidth();
        int frameHeight();
        void send(int type, float x, float y);
    }
    private final FrameLayout viewport;
    private final SurfaceViewRenderer renderer;
    private final TextView zoomLabel;
    private final RemoteInput remote;
    private final float touchSlop;
    private final RectF picture = new RectF();
    private float zoom = 1, offsetX, offsetY, scaleStep = 1;
    private float focusX, focusY, span, downX, downY, remoteX, remoteY;
    private boolean localGesture, singleTouch, remotePressed;
    private final Runnable hold = () -> {
        if (singleTouch && !localGesture) pressRemote();
    };

    RemoteViewport(FrameLayout viewport, SurfaceViewRenderer renderer, TextView zoomLabel, RemoteInput remote) {
        this.viewport = viewport; this.renderer = renderer; this.zoomLabel = zoomLabel; this.remote = remote;
        touchSlop = ViewConfiguration.get(viewport.getContext()).getScaledTouchSlop();
        renderer.setPivotX(0); renderer.setPivotY(0);
        viewport.setOnTouchListener(this);
        viewport.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (r - l != oldR - oldL || b - t != oldB - oldT) {
                cancel(); applyTransform();
            }
        });
        renderer.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> applyTransform());
        applyTransform();
    }

    void reset() {
        cancel(); zoom = 1; offsetX = offsetY = 0; applyTransform();
    }
    void cancel() {
        viewport.removeCallbacks(hold);
        releaseRemote(); singleTouch = localGesture = false;
        span = 0;
    }
    private boolean updatePicture() {
        int frameWidth = remote.frameWidth(), frameHeight = remote.frameHeight();
        if (frameWidth <= 0 || frameHeight <= 0 || renderer.getWidth() == 0 || renderer.getHeight() == 0) return false;
        float fit = Math.min((float) renderer.getWidth() / frameWidth, (float) renderer.getHeight() / frameHeight);
        float width = frameWidth * fit, height = frameHeight * fit;
        picture.set((renderer.getWidth() - width) / 2, (renderer.getHeight() - height) / 2,
                (renderer.getWidth() + width) / 2, (renderer.getHeight() + height) / 2);
        return true;
    }
    private void applyTransform() {
        if (updatePicture()) {
            offsetX = clampOffset(offsetX, renderer.getLeft(), picture.left, picture.width(), viewport.getWidth());
            offsetY = clampOffset(offsetY, renderer.getTop(), picture.top, picture.height(), viewport.getHeight());
        }
        renderer.setScaleX(zoom); renderer.setScaleY(zoom);
        renderer.setTranslationX(offsetX); renderer.setTranslationY(offsetY);
        int percent = Math.round(zoom * 100);
        zoomLabel.setText(percent + "% · 双指缩放/移动");
        viewport.setContentDescription("远程画面，缩放 " + percent + "%");
    }
    private float clampOffset(float offset, float viewStart, float pictureStart, float pictureSize, float viewportSize) {
        float size = pictureSize * zoom;
        float start = viewStart + offset + pictureStart * zoom;
        start = size <= viewportSize ? (viewportSize - size) / 2 : Math.max(viewportSize - size, Math.min(0, start));
        return start - viewStart - pictureStart * zoom;
    }
    private void movePicture(float x, float y) {
        float nextZoom = Math.max(1, Math.min(4, zoom * scaleStep));
        float ratio = nextZoom / zoom;
        offsetX = x - renderer.getLeft() - (focusX - renderer.getLeft() - offsetX) * ratio;
        offsetY = y - renderer.getTop() - (focusY - renderer.getTop() - offsetY) * ratio;
        zoom = nextZoom; focusX = x; focusY = y; applyTransform();
    }
    private float focus(MotionEvent event, boolean horizontal, int skip) {
        float total = 0; int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) if (i != skip) {
            total += horizontal ? event.getX(i) : event.getY(i); count++;
        }
        return count == 0 ? 0 : total / count;
    }
    private float span(MotionEvent event, int skip) {
        float x = focus(event, true, skip), y = focus(event, false, skip), distance = 0; int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) if (i != skip) {
            distance += (float) Math.hypot(event.getX(i) - x, event.getY(i) - y); count++;
        }
        return count == 0 ? 0 : distance * 2 / count;
    }
    private boolean remotePoint(float x, float y, boolean clamp) {
        if (!updatePicture()) return false;
        float normalizedX = ((x - renderer.getLeft() - offsetX) / zoom - picture.left) / picture.width() * 1000;
        float normalizedY = ((y - renderer.getTop() - offsetY) / zoom - picture.top) / picture.height() * 1000;
        if (!clamp && (normalizedX < 0 || normalizedX > 1000 || normalizedY < 0 || normalizedY > 1000)) return false;
        remoteX = Math.max(0, Math.min(1000, normalizedX)); remoteY = Math.max(0, Math.min(1000, normalizedY));
        return true;
    }
    private void pressRemote() {
        if (remotePressed) return;
        viewport.removeCallbacks(hold);
        remote.send(6, remoteX, remoteY); remote.send(2, remoteX, remoteY); remotePressed = true;
    }
    private void releaseRemote() {
        if (remotePressed) remote.send(4, remoteX, remoteY);
        remotePressed = false;
    }
    @Override public boolean onTouch(View view, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            viewport.removeCallbacks(hold); releaseRemote(); localGesture = false;
            downX = event.getX(); downY = event.getY();
            singleTouch = remotePoint(downX, downY, false);
            if (singleTouch) viewport.postDelayed(hold, ViewConfiguration.getLongPressTimeout());
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            viewport.removeCallbacks(hold); releaseRemote(); singleTouch = false; localGesture = true;
            focusX = focus(event, true, -1); focusY = focus(event, false, -1);
            span = span(event, -1);
        }
        if (localGesture) {
            if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2) {
                float nextSpan = span(event, -1);
                // Use the two-finger distance directly so dense displays can shrink all the way to 1x.
                scaleStep = span > touchSlop && nextSpan > touchSlop ? nextSpan / span : 1;
                movePicture(focus(event, true, -1), focus(event, false, -1));
                span = nextSpan;
            }
            if (action == MotionEvent.ACTION_POINTER_UP) {
                focusX = focus(event, true, event.getActionIndex()); focusY = focus(event, false, event.getActionIndex());
                span = span(event, event.getActionIndex());
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) cancel();
            return true;
        }
        if (!singleTouch) return true;
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - downX, dy = event.getY() - downY;
            if (!remotePressed && dx * dx + dy * dy > touchSlop * touchSlop) pressRemote();
            if (remotePoint(event.getX(), event.getY(), true) && remotePressed) remote.send(0, remoteX, remoteY);
        } else if (action == MotionEvent.ACTION_UP) {
            viewport.removeCallbacks(hold);
            if (!remotePressed) { remotePoint(event.getX(), event.getY(), true); pressRemote(); }
            releaseRemote(); singleTouch = false;
            view.performClick();
        } else if (action == MotionEvent.ACTION_CANCEL) cancel();
        return true;
    }
}

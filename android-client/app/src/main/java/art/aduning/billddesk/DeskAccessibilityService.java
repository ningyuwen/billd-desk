package art.aduning.billddesk;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DeskAccessibilityService extends AccessibilityService {
    static volatile DeskAccessibilityService current;
    private final android.os.Handler inputHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private GestureDescription.StrokeDescription dragStroke;
    private boolean pointerDown, dragInFlight, dragDirty, firstDragMovement;
    private int dragGeneration, wheelGeneration;
    private float dragX, dragY;
    private float wheelX, wheelY, wheelWidth, wheelHeight;
    private boolean wheelInFlight;
    private final Runnable releaseTimeout = this::resetInput;
    private long pressedAt;
    private float pointerX, pointerY;
    private final java.util.Set<Integer> pressedKeys = new java.util.HashSet<>();
    private boolean capsLock;
    @Override protected void onServiceConnected() { current = this; ((DeskApplication) getApplication()).engine().changed(); }
    @Override public void onDestroy() { resetInput(); current = null; ((DeskApplication) getApplication()).engine().changed(); super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { resetInput(); }

    void command(String message, JSONObject data) {
        if (!((DeskApplication) getApplication()).engine().sharing) return;
        if (message.equals("androidAction")) {
            endDrag();
            switch (data.optString("action")) {
                case "back": performGlobalAction(GLOBAL_ACTION_BACK); break;
                case "home": performGlobalAction(GLOBAL_ACTION_HOME); break;
                case "recents": performGlobalAction(GLOBAL_ACTION_RECENTS); break;
                case "notifications": performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break;
                case "quickSettings": performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS); break;
                case "dismissShade":
                    // Some OEM control centers ignore DISMISS_NOTIFICATION_SHADE.
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root != null && "com.android.systemui".equals(root.getPackageName()))
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    else if (android.os.Build.VERSION.SDK_INT >= 31)
                        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
                    break;
            }
            return;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);
        float x = (float) Math.max(0, Math.min(metrics.widthPixels - 1, data.optDouble("x") * metrics.widthPixels / 1000));
        float y = (float) Math.max(0, Math.min(metrics.heightPixels - 1, data.optDouble("y") * metrics.heightPixels / 1000));
        switch (data.optInt("type", -1)) {
            case 0: case 1: case 6:
                pointerX = x; pointerY = y;
                if (pointerDown) {
                    dragDirty = true;
                    inputHandler.removeCallbacks(releaseTimeout);
                    inputHandler.postDelayed(releaseTimeout, 5000);
                    pumpDrag();
                }
                break;
            case 2:
                beginDrag(x, y); break;
            case 4:
                pointerX = x; pointerY = y;
                endDrag();
                break;
            case 8: tap(x, y); break;
            case 7:
                tap(pointerX, pointerY);
                new android.os.Handler(getMainLooper()).postDelayed(() -> {
                    if (((DeskApplication) getApplication()).engine().sharing) tap(pointerX, pointerY);
                }, 160);
                break;
            case 9: performGlobalAction(GLOBAL_ACTION_BACK); break;
            case 10: case 11: case 12: case 13:
                int type = data.optInt("type");
                if (pointerDown || dragStroke != null) break;
                wheelWidth = metrics.widthPixels; wheelHeight = metrics.heightPixels;
                float amount = (float) Math.max(1, Math.min(300, data.optDouble("amount", 80))) * metrics.density;
                // Wheel down means the finger moves up. Coalesce arrivals while one
                // gesture runs; dispatching each event would cancel the previous swipe.
                wheelX = clampWheel(wheelX + (type == 12 ? amount : type == 13 ? -amount : 0), wheelWidth);
                wheelY = clampWheel(wheelY + (type == 10 ? -amount : type == 11 ? amount : 0), wheelHeight);
                pumpWheel(); break;
            case 14:
                JSONArray text = data.optJSONArray("key");
                if (text != null && text.length() > 0) edit(text.optString(0), false);
                break;
            case 15:
                JSONArray keys = data.optJSONArray("key");
                if (keys != null) for (int i = 0; i < keys.length(); i++) {
                    int key = keys.optInt(i, -1);
                    pressedKeys.add(key); key(key);
                }
                break;
            case 16:
                JSONArray released = data.optJSONArray("key");
                if (released != null) for (int i = 0; i < released.length(); i++) pressedKeys.remove(released.optInt(i, -1));
                break;
        }
    }
    private void beginDrag(float x, float y) {
        wheelGeneration++; wheelInFlight = false; wheelX = wheelY = 0;
        dragGeneration++; dragInFlight = false; dragStroke = null;
        pointerDown = true; dragDirty = true; firstDragMovement = false;
        pointerX = dragX = x; pointerY = dragY = y;
        pressedAt = android.os.SystemClock.uptimeMillis();
        inputHandler.removeCallbacks(releaseTimeout);
        inputHandler.postDelayed(releaseTimeout, 5000);
        pumpDrag();
    }
    private void endDrag() {
        inputHandler.removeCallbacks(releaseTimeout);
        if (!pointerDown && dragStroke == null) return;
        pointerDown = false; dragDirty = true;
        pumpDrag();
    }
    private void pumpDrag() {
        if (dragInFlight || !dragDirty) return;
        Path path = new Path(); path.moveTo(dragX, dragY);
        boolean moved = dragX != pointerX || dragY != pointerY;
        if (moved) path.lineTo(pointerX, pointerY);
        // Keep the pointer down between short strokes, rather than replaying the
        // complete path only after mouse-up. Pending movements use the latest point.
        GestureDescription.StrokeDescription next = dragStroke == null
                ? new GestureDescription.StrokeDescription(path, 0, 16, pointerDown)
                : dragStroke.continueStroke(path, 0, 16, pointerDown);
        if (moved && !firstDragMovement) {
            firstDragMovement = true;
            android.util.Log.d("BilldDeskInput", "First drag movement: "
                    + (android.os.SystemClock.uptimeMillis() - pressedAt) + " ms after press; released=" + !pointerDown);
        }
        dragX = pointerX; dragY = pointerY; dragStroke = next;
        dragDirty = false; dragInFlight = true;
        final int generation = dragGeneration;
        boolean accepted = dispatchGesture(new GestureDescription.Builder().addStroke(next).build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                if (generation != dragGeneration) return;
                dragInFlight = false;
                if (!next.willContinue()) { dragStroke = null; dragDirty = false; }
                else pumpDrag();
            }
            @Override public void onCancelled(GestureDescription description) {
                if (generation != dragGeneration) return;
                clearDrag();
                android.util.Log.w("BilldDeskInput", "Remote drag cancelled");
            }
        }, inputHandler);
        if (!accepted) clearDrag();
    }
    private void clearDrag() {
        pointerDown = false; dragInFlight = false; dragDirty = false; dragStroke = null;
        inputHandler.removeCallbacks(releaseTimeout);
    }
    private float clampWheel(float value, float size) { return Math.max(-size * .6f, Math.min(size * .6f, value)); }
    private void pumpWheel() {
        if (wheelInFlight || pointerDown || dragStroke != null || (wheelX == 0 && wheelY == 0)) return;
        float dx = wheelX, dy = wheelY; wheelX = wheelY = 0;
        float cx = wheelWidth / 2, cy = wheelHeight / 2;
        Path path = new Path(); path.moveTo(cx - dx / 2, cy - dy / 2); path.lineTo(cx + dx / 2, cy + dy / 2);
        wheelInFlight = true;
        final int generation = wheelGeneration;
        boolean accepted = dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                if (generation != wheelGeneration) return;
                wheelInFlight = false; pumpWheel();
            }
            @Override public void onCancelled(GestureDescription description) {
                if (generation != wheelGeneration) return;
                wheelInFlight = false; wheelX = wheelY = 0;
                android.util.Log.w("BilldDeskInput", "Remote wheel gesture cancelled");
            }
        }, inputHandler);
        if (!accepted) { wheelInFlight = false; wheelX = wheelY = 0; }
    }
    private void tap(float x, float y) { Path path = new Path(); path.moveTo(x, y); dispatch(path, 70); }
    private void dispatch(Path path, long duration) {
        dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, duration)).build(), null, null);
    }
    private void key(int nutKey) {
        if (nutKey == 0) performGlobalAction(GLOBAL_ACTION_BACK);
        else if (nutKey == 43) performGlobalAction(GLOBAL_ACTION_HOME);
        else if (nutKey == 49) performGlobalAction(GLOBAL_ACTION_RECENTS);
        else if (nutKey == 41 || nutKey == 63) edit("", true);
        else if (nutKey == 102 || nutKey == 82) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            AccessibilityNodeInfo input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (input != null && android.os.Build.VERSION.SDK_INT >= 30) input.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
        } else if (nutKey == 70) capsLock = !capsLock;
        else if (!pressedKeys.contains(103) && !pressedKeys.contains(114)
                && !pressedKeys.contains(104) && !pressedKeys.contains(106) && !pressedKeys.contains(112)
                && !pressedKeys.contains(107) && !pressedKeys.contains(109)) {
            String value = null;
            if (nutKey >= 50 && nutKey <= 59) value = "qwertyuiop".substring(nutKey - 50, nutKey - 49);
            else if (nutKey >= 71 && nutKey <= 79) value = "asdfghjkl".substring(nutKey - 71, nutKey - 70);
            else if (nutKey >= 87 && nutKey <= 93) value = "zxcvbnm".substring(nutKey - 87, nutKey - 86);
            boolean shift = pressedKeys.contains(86) || pressedKeys.contains(97);
            if (value != null) { if (shift != capsLock) value = value.toUpperCase(java.util.Locale.ROOT); }
            else {
                String plain = "`1234567890-=\\[];' ,./";
                String shifted = "~!@#$%^&*()_+|{}:\" <>?";
                int[] codes = {28,29,30,31,32,33,34,35,36,37,38,39,40,62,60,61,80,81,108,94,95,96};
                for (int i = 0; i < codes.length; i++) if (codes[i] == nutKey)
                    value = String.valueOf((shift ? shifted : plain).charAt(i));
            }
            if (value != null) edit(value, false);
        }
    }
    void resetInput() {
        endDrag(); wheelGeneration++; wheelInFlight = false; wheelX = wheelY = 0;
        pressedKeys.clear(); capsLock = false;
    }
    private void edit(String insertion, boolean delete) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (input == null || !input.isEditable() || input.isPassword()) return;
        String old = input.isShowingHintText() || input.getText() == null ? "" : input.getText().toString();
        int start = input.getTextSelectionStart(), end = input.getTextSelectionEnd();
        if (start < 0 || end < 0) start = end = old.length();
        int lo = Math.max(0, Math.min(old.length(), Math.min(start, end)));
        int hi = Math.max(lo, Math.min(old.length(), Math.max(start, end)));
        if (delete && lo == hi && lo > 0) lo = old.offsetByCodePoints(lo, -1);
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                old.substring(0, lo) + insertion + old.substring(hi));
        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        Bundle selection = new Bundle();
        selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, lo + insertion.length());
        selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, lo + insertion.length());
        input.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection);
    }
}

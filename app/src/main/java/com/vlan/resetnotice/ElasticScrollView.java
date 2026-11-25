package com.vlan.resetnotice;

import android.content.Context;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/** Content-only edge motion: the viewport is stationary, no Android stretch
 * effect and no scaling of text or cards. The resistance has no hard travel cap. */
final class ElasticScrollView extends ScrollView {
    private final int slop;
    private final float density;
    private int activePointer = -1;
    private float downX, downY, lastY, rawOffset, nativeOffset;
    private boolean dragging;
    private boolean touching, flingArmed;
    private int flingDirection;
    private long flingFrame, flingMotion;
    private final EdgeMotion.Tracker touchMotion = new EdgeMotion.Tracker(), flingTrack = new EdgeMotion.Tracker();
    private boolean springActive;
    private long springStart;
    private float springX, springV, springSign;
    private final Choreographer.FrameCallback springFrame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long nanos) {
            if (!springActive || touching) return;
            View child = getChildAt(0);
            if (child == null) { springActive = false; return; }
            double t = Math.max(0, nanos - springStart) / 1_000_000_000.0;
            float x = EdgeMotion.position(springX, springV, t);
            float v = EdgeMotion.velocity(springX, springV, t);
            if (x * springSign < 0 || t > 1.65 ||
                    (t > 0.045 && Math.abs(x) < .35f * density && Math.abs(v) < 3f * density)) {
                child.setTranslationY(0); springActive = false; return;
            }
            child.setTranslationY(x);
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    ElasticScrollView(Context context) {
        super(context);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        density = getResources().getDisplayMetrics().density;
    }

    private void start(MotionEvent event) {
        activePointer = event.getPointerId(0);
        downX = event.getX(0); downY = lastY = event.getY(0);
        dragging = false; rawOffset = nativeOffset = 0; touching = true; flingArmed = false;
        cancelSpring();
        touchMotion.seed(0, event.getEventTime());
        View child = getChildAt(0);
        if (child != null) {
            child.animate().cancel();
            float offset = child.getTranslationY();
            if (offset != 0f) rawOffset = EdgeMotion.inverseRubber(offset, density);
        }
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) start(event);
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex();
            activePointer = event.getPointerId(index);
            lastY = event.getY(index);
        }
        boolean intercepted = super.onInterceptTouchEvent(event);
        if (action == MotionEvent.ACTION_MOVE && !dragging) {
            int index = event.findPointerIndex(activePointer);
            if (index >= 0) {
                float dy = event.getY(index) - downY, dx = event.getX(index) - downX;
                if (intercepted || (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)
                        && (!canScrollVertically(-1) || !canScrollVertically(1)))) {
                    dragging = true; lastY = event.getY(index);
                    return true;
                }
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) release();
        return intercepted;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) start(event);
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = event.getActionIndex();
            activePointer = event.getPointerId(index);
            lastY = event.getY(index);
        }
        if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == activePointer) {
            int replacement = event.getActionIndex() == 0 ? 1 : 0;
            if (replacement < event.getPointerCount()) {
                activePointer = event.getPointerId(replacement);
                lastY = event.getY(replacement);
            }
        }
        int index = event.findPointerIndex(activePointer);
        float y = index >= 0 ? event.getY(index) : lastY;
        float delta = action == MotionEvent.ACTION_MOVE && index >= 0 ? y - lastY : 0f;
        // On reversal, unwind the translated content before allowing native
        // scrolling away from the edge; keep ScrollView's coordinate stream
        // continuous so a large pull does not snap straight into list movement.
        float taken = 0f;
        if (dragging && rawOffset * delta < 0f) {
            taken = Math.copySign(Math.min(Math.abs(rawOffset), Math.abs(delta)), delta);
            rawOffset += taken;
            nativeOffset -= taken;
        }
        int before = getScrollY();
        MotionEvent nativeEvent = event;
        if (nativeOffset != 0f) {
            nativeEvent = MotionEvent.obtain(event);
            nativeEvent.offsetLocation(0, nativeOffset);
        }
        boolean handled = super.onTouchEvent(nativeEvent);
        if (nativeEvent != event) nativeEvent.recycle();
        if (action == MotionEvent.ACTION_MOVE) {
            if (index >= 0) {
                if (!dragging && Math.abs(y - downY) > slop && Math.abs(y - downY) > Math.abs(event.getX(index) - downX))
                    dragging = true;
                if (dragging) {
                    int consumed = getScrollY() - before;
                    if (consumed != 0 && rawOffset != 0f) rawOffset = 0f;
                    float remainder = delta - taken + consumed;
                    if (rawOffset != 0 && consumed == 0) {
                        float next = rawOffset + remainder;
                        rawOffset = next * rawOffset <= 0 ? 0f : next;
                    } else if (remainder > 0 && getScrollY() <= 0) rawOffset += remainder;
                    else if (remainder < 0 && !canScrollVertically(1)) rawOffset += remainder;
                    else rawOffset = 0f;
                    View child = getChildAt(0);
                    if (child != null) {
                        float offset = EdgeMotion.rubber(rawOffset, density);
                        touchMotion.sample(offset - child.getTranslationY(), event.getEventTime());
                        child.setTranslationY(offset);
                    }
                }
                lastY = y;
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) release();
        return handled || dragging;
    }

    private void release() {
        if (!touching) return;
        activePointer = -1; rawOffset = nativeOffset = 0f; dragging = false; touching = false;
        View child = getChildAt(0);
        if (child != null && child.getTranslationY() != 0f)
            startSpring(child, child.getTranslationY(), touchMotion.predict(SystemClock.uptimeMillis(), 0.012f));
    }

    private void cancelSpring() {
        springActive = false;
        Choreographer.getInstance().removeFrameCallback(springFrame);
    }
    private void startSpring(View child, float initialPosition, float initialVelocity) {
        cancelSpring();
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) { child.setTranslationY(0f); return; }
        // Bound pathological multi-pointer/clock samples, not the rebound
        // distance: normal travel and duration follow incident energy.
        springX = initialPosition;
        springV = Math.max(-4500f * density, Math.min(4500f * density, initialVelocity));
        springSign = Math.signum(springX != 0 ? springX : springV);
        if (springSign == 0) { child.setTranslationY(0); return; }
        springStart = System.nanoTime();
        springActive = true;
        Choreographer.getInstance().postFrameCallback(springFrame);
    }

    @Override public void fling(int velocityY) {
        int direction = Integer.signum(velocityY);
        flingArmed = direction != 0 && Math.abs(rawOffset) < 1f && Math.abs(velocityY) >= 65f * density;
        flingDirection = direction;
        flingFrame = flingMotion = SystemClock.uptimeMillis();
        flingTrack.seed(velocityY, flingFrame);
        super.fling(velocityY);
        if (flingArmed && !canScrollVertically(direction)) postInvalidateOnAnimation();
    }

    @Override public void computeScroll() {
        int before = getScrollY();
        super.computeScroll();
        if (!flingArmed || touching) return;
        long now = SystemClock.uptimeMillis();
        int movement = getScrollY() - before;
        long frame = Math.max(1L, now - flingFrame);
        flingFrame = now;
        if (!canScrollVertically(flingDirection)) {
            flingArmed = false;
            float predicted = flingTrack.predict(now, Math.min(.016f, frame / 2000f));
            float incidentSpeed = Math.max(Math.abs(movement) * 1000f / frame, Math.max(0, predicted * flingDirection));
            if ((movement != 0 || now - flingMotion <= 100) && incidentSpeed >= 65f * density) {
                View child = getChildAt(0);
                if (child == null) return;
                startSpring(child, child.getTranslationY(), -flingDirection * incidentSpeed * .43f);
            }
        } else if (movement != 0) {
            flingTrack.sample(movement, now);
            flingMotion = now;
        } else if (now - flingMotion > 140) flingArmed = false;
    }
    @Override protected void onDetachedFromWindow() {
        View child = getChildAt(0);
        cancelSpring();
        if (child != null) { child.animate().cancel(); child.setTranslationY(0f); }
        flingArmed = false; touching = false;
        super.onDetachedFromWindow();
    }
}

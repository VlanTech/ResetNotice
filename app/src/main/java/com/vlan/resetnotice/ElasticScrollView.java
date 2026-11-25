package com.vlan.resetnotice;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
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
    private float flingSpeed;
    private long flingFrame, flingMotion;

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
        View child = getChildAt(0);
        if (child != null) {
            child.animate().cancel();
            float offset = child.getTranslationY();
            if (offset != 0f) rawOffset = (float)(Math.signum(offset) * 350f * density *
                    Math.expm1(Math.abs(offset) / (170f * density)));
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
                        // Logarithmic resistance approaches no artificial limit,
                        // while long pulls remain subtle and reversible.
                        float offset = (float)(Math.signum(rawOffset) * 170f * density *
                                Math.log1p(Math.abs(rawOffset) / (350f * density)));
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
        activePointer = -1; rawOffset = nativeOffset = 0f; dragging = false; touching = false;
        View child = getChildAt(0);
        if (child != null && child.getTranslationY() != 0f) settle(child);
    }

    private void settle(View child) {
        child.animate().translationY(0f).setDuration(465)
                .setInterpolator(new PathInterpolator(0.20f, 0.72f, 0.24f, 1f)).start();
    }

    @Override public void fling(int velocityY) {
        int direction = Integer.signum(velocityY);
        flingArmed = direction != 0 && Math.abs(rawOffset) < 1f && Math.abs(velocityY) >= 65f * density;
        flingDirection = direction;
        flingSpeed = Math.abs(velocityY);
        flingFrame = flingMotion = SystemClock.uptimeMillis();
        super.fling(velocityY);
        if (flingArmed && !canScrollVertically(direction)) postInvalidateOnAnimation();
    }

    @Override public void computeScroll() {
        int before = getScrollY();
        super.computeScroll();
        if (!flingArmed || touching) return;
        long now = SystemClock.uptimeMillis();
        int movement = Math.abs(getScrollY() - before);
        long frame = Math.max(1L, now - flingFrame);
        if (movement > 0) {
            flingSpeed = Math.max(movement * 1000f / frame, flingSpeed * 0.66f);
            flingMotion = now;
        }
        flingFrame = now;
        if (!canScrollVertically(flingDirection)) {
            flingArmed = false;
            if (now - flingMotion <= 100 && flingSpeed >= 65f * density) {
                View child = getChildAt(0);
                if (child == null) return;
                // Transform the impact of a single inertial scroll into a
                // short, restrained translation, followed by the same soft
                // return used for a finger pull.
                float amplitude = (float)Math.min(68f * density,
                        density * (10f + 25f * Math.log1p(flingSpeed / (900f * density))));
                child.animate().cancel();
                child.animate().translationY(-flingDirection * amplitude).setDuration(155)
                        .setInterpolator(new DecelerateInterpolator(1.6f))
                        .withEndAction(() -> { if (!touching) settle(child); }).start();
            }
        } else if (now - flingMotion > 140) flingArmed = false;
    }
    @Override protected void onDetachedFromWindow() {
        View child = getChildAt(0);
        if (child != null) { child.animate().cancel(); child.setTranslationY(0f); }
        flingArmed = false; touching = false;
        super.onDetachedFromWindow();
    }
}

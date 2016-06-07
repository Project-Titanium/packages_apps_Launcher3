package com.android.launcher3.allapps;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * One dimensional scroll gesture detector for all apps container pull up interaction.
 */
public class VerticalPullDetector {

    private static final String TAG = "ScrollGesture";
    private static final boolean DBG = false;

    private float mTouchSlop;

    private boolean mAllAppsVisible;
    private boolean mAllAppsScrollAtTop;

    /**
     * The minimum release velocity in pixels per millisecond that triggers fling..
     */
    private static final float RELEASE_VELOCITY_PX_MS = 1.7f;

    /**
     * The time constant used to calculate dampening in the low-pass filter of scroll velocity.
     * Cutoff frequency is set at 10 Hz.
     */
    public static final float SCROLL_VELOCITY_DAMPENING_RC = 1000f / (2f * (float) Math.PI * 10);

    /* Scroll state, this is set to true during dragging and animation. */
    boolean mScrolling;


    float mDownY;
    float mDownMillis;

    float mLastY;
    float mLastMillis;

    float mVelocity;
    float mLastDisplacement;
    float mDisplacement;

    /* scroll started during previous animation */
    boolean mSubtractSlop = true;

    /* Client of this gesture detector can register a callback. */
    Listener mListener;

    public void setListener(Listener l) {
        mListener = l;
    }

    interface Listener{
        /**
         * @param start when should
         */
        void onScrollStart(boolean start);
        boolean onScroll(float displacement, float velocity);
        void onScrollEnd(float velocity, boolean fling);
    }

    public VerticalPullDetector(Context context) {
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setAllAppsState(boolean allAppsVisible, boolean scrollAtTop) {
        mAllAppsVisible = allAppsVisible;
        mAllAppsScrollAtTop = scrollAtTop;
    }

    private boolean shouldScrollStart() {
        if (mAllAppsVisible && mDisplacement > mTouchSlop && mAllAppsScrollAtTop) {
            return true;
        }
        if (!mAllAppsVisible && mDisplacement < -mTouchSlop) {
            return true;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownMillis = ev.getDownTime();
                mDownY = ev.getY();
                mLastDisplacement = 0;
                mVelocity = 0;

                if (mScrolling) {
                    reportScrollStart(true /* recatch */);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                mDisplacement = computeDisplacement(ev);
                mVelocity = computeVelocity(ev, mVelocity);

                if (!mScrolling && shouldScrollStart()) {
                    mScrolling = true;
                    reportScrollStart(false /* recatch */);
                }
                if (mScrolling && mListener != null) {
                    reportScroll();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // These are synthetic events and there is no need to update internal values.
                if (mScrolling && mListener != null) {
                    reportScrollEnd();
                }
                break;
            default:
                //TODO: add multi finger tracking by tracking active pointer.
                break;
        }
        // Do house keeping.
        mLastDisplacement = mDisplacement;

        mLastY = ev.getY();
        mLastMillis = ev.getEventTime();

        return true;
    }

    public void finishedScrolling() {
        mScrolling = false;
    }

    private boolean reportScrollStart(boolean recatch) {
        mListener.onScrollStart(!recatch);
        if (DBG) {
            Log.d(TAG, "onScrollStart recatch:" + recatch);
        }
        return true;
    }

    private boolean reportScroll() {
        float delta = mDisplacement - mLastDisplacement;
        if (delta != 0) {
            if (DBG) {
                Log.d(TAG, String.format("onScroll disp=%.1f, velocity=%.1f",
                        mDisplacement, mVelocity));
            }
            return mListener.onScroll(mDisplacement - (mSubtractSlop? mTouchSlop : 0), mVelocity);
        }
        return true;
    }

    private void reportScrollEnd() {
        if (DBG) {
            Log.d(TAG, String.format("onScrolEnd disp=%.1f, velocity=%.1f",
                    mDisplacement, mVelocity));
        }
        mListener.onScrollEnd(mVelocity, Math.abs(mVelocity) > RELEASE_VELOCITY_PX_MS);
    }
    /**
     * Computes the damped velocity using the two motion events and the previous velocity.
     */
    private float computeVelocity(MotionEvent to, float previousVelocity) {
        float delta = computeDelta(to);

        float deltaTimeMillis = to.getEventTime() - mLastMillis;
        float velocity = (deltaTimeMillis > 0) ? (delta / deltaTimeMillis) : 0;
        if (Math.abs(previousVelocity) < 0.001f) {
            return velocity;
        }

        float alpha = computeDampeningFactor(deltaTimeMillis);
        return interpolate(previousVelocity, velocity, alpha);
    }

    private float computeDisplacement(MotionEvent to) {
        return to.getY() - mDownY;
    }

    private float computeDelta(MotionEvent to) {
        return to.getY() - mLastY;
    }

    /**
     * Returns a time-dependent dampening factor using delta time.
     */
    private static float computeDampeningFactor(float deltaTime) {
        return deltaTime / (SCROLL_VELOCITY_DAMPENING_RC + deltaTime);
    }

    /**
     * Returns the linear interpolation between two values
     */
    private static float interpolate(float from, float to, float alpha) {
        return (1.0f - alpha) * from + alpha * to;
    }
}
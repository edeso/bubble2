package com.nkanaev.comics.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;
import com.nkanaev.comics.managers.Utils;

public class InterceptTouchLayout extends FrameLayout {

    public InterceptTouchLayout(@NonNull Context context) {
        super(context);
    }

    public InterceptTouchLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private ViewPager2 getParentViewpager() {
        ViewParent candidate = getParent();
        while (candidate != null && !(candidate instanceof ViewPager2)) {
            candidate = candidate.getParent();
        }
        return candidate instanceof ViewPager2 ? (ViewPager2) candidate : null;
    }

    float ispullingMenuY = -1;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        // protect reader menu pulldown
        int action = e.getAction();
        int offset = Utils.getGestureOffsetTop(this);
        if (action == MotionEvent.ACTION_DOWN && e.getY() <= offset) {
            ispullingMenuY = e.getY();
        } else if (ispullingMenuY > 0 && action == MotionEvent.ACTION_MOVE && ispullingMenuY <= e.getY()) {
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        } else {
            ispullingMenuY = -1;
        }

        handleInterceptTouchEvent(e);
        onTouchEvent(e);

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        //Log.d("onTouchEvent", e.toString());
        if (mTouch != null) {
            boolean b = mTouch.onTouch(this, e);
        }
        return false;
    }

    private float initialX = 0f;
    private float initialY = 0f;

    private void handleInterceptTouchEvent(MotionEvent e) {
        ViewPager2 vp = getParentViewpager();
        if (vp == null) return;

        View child = getChildAt(0);
        if (child == null) return;

        // default is we handle events
        getParent().requestDisallowInterceptTouchEvent(true);

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            initialX = e.getX();
            initialY = e.getY();
        }

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            int dx = Math.round(initialX - e.getX());
            int dy = Math.round(initialY - e.getY());
            boolean isVpHorizontal = (vp.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL);

            // we can scroll in direction requested
            if ((isVpHorizontal && child.canScrollHorizontally(dx)) ||
                    (!isVpHorizontal && child.canScrollVertically(dy)))
                return;

            // allow only vertical/horizontal strokes to be intercepted
            if ((isVpHorizontal && Math.abs(dx) >= 3 * Math.abs(dy)) ||
                    (!isVpHorizontal && Math.abs(dy) >= 3 * Math.abs(dx)))
                getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    private OnTouchListener mTouch = null;

    @Override
    public void setOnTouchListener(OnTouchListener l) {
        mTouch = l;
    }

}


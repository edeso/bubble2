package com.nkanaev.comics.view;

import android.content.Context;
import android.gesture.Gesture;
import android.gesture.GestureOverlayView;
import android.util.AttributeSet;
import com.nkanaev.comics.BuildConfig;

public class GestureOverlayLayout extends GestureOverlayView {
    public GestureOverlayLayout(Context context) {
        super(context);
        init();
    }

    public GestureOverlayLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init(){
        setGestureVisible(true && BuildConfig.DEBUG);
        setEventsInterceptionEnabled(false);
        // workaround, setting colors only seems to work when a listener was added
        addOnGesturePerformedListener(new GestureOverlayView.OnGesturePerformedListener() {
            @Override
            public void onGesturePerformed(GestureOverlayView overlay, Gesture gesture) {
            }
        });
        setUncertainGestureColor(0x80FF0000); //red
        setGestureColor(0x8000FF00); //green
    }
}

package com.example.rtsp;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;



public class ZoomableTextureView extends TextureView {

    private static final String SUPERSTATE_KEY = "superState";
    private static final String MIN_SCALE_KEY = "minScale";
    private static final String MAX_SCALE_KEY = "maxScale";
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private static final int TIME_DOUBLE_CLICK = 250;
    public static Boolean isZoomEnabled = false;
    public static Boolean isActionMove = false;
    private final Context context;
    private final PointF last = new PointF();
    private final PointF start = new PointF();
    private float minScale = 1f;
    private float maxScale = 5f;
    private float saveScale = 1f;
    private long lastDownMills;
    private GestureDetector gestureDetector;
    private int mode = NONE;
    private Matrix matrix = new Matrix();
    private ScaleGestureDetector mScaleDetector;
    private float[] m;
    private float right, bottom;
    private Boolean isDoubleClick = false;

    public ZoomableTextureView(Context context) {
        super(context);
        this.context = context;
        initView(null);
//        initCallback(context);
    }

    public ZoomableTextureView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        initView(attrs);
//        initCallback(context);
    }

    public ZoomableTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.context = context;
        initView(attrs);
//        initCallback(context);
    }


    public void setMinScale(float scale) {
        if (scale < 1.0f || scale > maxScale)
            throw new RuntimeException("minScale can't be lower than 1 or larger than maxScale(" + maxScale + ")");
        else minScale = scale;
    }

    public void setMaxScale(float scale) {
        if (scale < 1.0f || scale < minScale)
            throw new RuntimeException("maxScale can't be lower than 1 or minScale(" + minScale + ")");
        else minScale = scale;
    }

    public void resetZoom() {
        saveScale = 1.0f;
        matrix = new Matrix();
        ZoomableTextureView.this.setTransform(matrix);
        ZoomableTextureView.this.invalidate();
        isZoomEnabled = false;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(SUPERSTATE_KEY, super.onSaveInstanceState());
        bundle.putFloat(MIN_SCALE_KEY, minScale);
        bundle.putFloat(MAX_SCALE_KEY, maxScale);
        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        try {
            if (state instanceof Bundle bundle) {
                this.minScale = bundle.getFloat(MIN_SCALE_KEY);
                this.maxScale = bundle.getFloat(MAX_SCALE_KEY);
                state = bundle.getParcelable(SUPERSTATE_KEY);
            }
            super.onRestoreInstanceState(state);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initView(AttributeSet attrs) {
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ZoomableTextureView,
                0, 0);
        try {
            minScale = a.getFloat(R.styleable.ZoomableTextureView_minScale, minScale);
            maxScale = a.getFloat(R.styleable.ZoomableTextureView_maxScale, maxScale);
        } finally {
            a.recycle();
        }
        setOnTouchListener(new ZoomOnTouchListeners());
    }

    // Listen touches
    public class ZoomOnTouchListeners implements View.OnTouchListener {

        ZoomOnTouchListeners() {
            super();
            m = new float[9];
            mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
            gestureDetector = new GestureDetector(context, new GestureListener());
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            mScaleDetector.onTouchEvent(event);
            matrix.getValues(m);
            float x = m[Matrix.MTRANS_X];
            float y = m[Matrix.MTRANS_Y];
            try {
                PointF curr = new PointF(event.getX(), event.getY());
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        isActionMove = false;
                        if (SystemClock.uptimeMillis() < (lastDownMills + TIME_DOUBLE_CLICK)) { // Double click in 250ms or less
//                            Log.d("ACTION", "ACTION_DOWN");
                            isDoubleClick = true;
                            saveScale = 1.0f;
                            matrix = new Matrix();
                        } else {
                            isDoubleClick = false;
                            lastDownMills = SystemClock.uptimeMillis();
                            last.set(event.getX(), event.getY());
                            start.set(last);
                            mode = DRAG;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        isActionMove = false;
                        if (saveScale == 1.0f) isZoomEnabled = false;
                        mode = NONE;
                        if (curr.x == start.x && curr.y == start.y) {
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                try {
//                                    if (!isDoubleClick) controllerCallback.onShowCameraController();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }, TIME_DOUBLE_CLICK);
                        }
//                        Log.d("ACTION", "ACTION_UP");
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        last.set(event.getX(), event.getY());
                        start.set(last);
                        mode = ZOOM;
                        isZoomEnabled = true;
//                        Log.d("ACTION", "ACTION_POINTER_DOWN");
                        break;
                    case MotionEvent.ACTION_MOVE:
                        isActionMove = true;
                        if (mode == ZOOM || (mode == DRAG && saveScale > minScale)) {
                            float deltaX = curr.x - last.x;// x difference
                            float deltaY = curr.y - last.y;// y difference
                            if (y + deltaY > 0)
                                deltaY = -y;
                            else if (y + deltaY < -bottom)
                                deltaY = -(y + bottom);
                            if (x + deltaX > 0)
                                deltaX = -x;
                            else if (x + deltaX < -right)
                                deltaX = -(x + right);
                            matrix.postTranslate(deltaX, deltaY);
                            last.set(curr.x, curr.y);
                            mode = ZOOM;
//                            Log.d("ACTION", "ACTION_MOVE");
                        }
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
//                        Log.d("ACTION", "ACTION_POINTER_UP");
                        break;
                }
                ZoomableTextureView.this.setTransform(matrix);
                ZoomableTextureView.this.invalidate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return gestureDetector.onTouchEvent(event);
        }

        public final class GestureListener extends GestureDetector.SimpleOnGestureListener {

            private static final int SWIPE_THRESHOLD = 1200;
            private static final int SWIPE_VELOCITY_THRESHOLD = 1200;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                boolean result = false;
                try {
                    float diffY = e2.getY() - e1.getY();
                    float diffX = e2.getX() - e1.getX();
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
//                                pageCallback.onSwipeRight();
//                            } else {
//                                pageCallback.onSwipeLeft();
                            }
                            result = true;
                        }
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
                return result;
            }
        }

        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                mode = ZOOM;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float mScaleFactor = detector.getScaleFactor();
                float origScale = saveScale;
                saveScale *= mScaleFactor;
                if (minScale < 1.0) {
                    minScale = 1.0f;
                }
                if (saveScale > maxScale) {
                    saveScale = maxScale;
                    mScaleFactor = maxScale / origScale;
                } else if (saveScale < minScale) {
//                    Log.i("ZoomTex", "BelowMin" + "saveScale=" + saveScale);
                    saveScale = minScale;
                    mScaleFactor = minScale / origScale;
                }
//                Log.i("ZoomTex", "minScale=" + minScale + ", mScaleFactor=" + mScaleFactor);
                right = getWidth() * saveScale - getWidth();
                bottom = getHeight() * saveScale - getHeight();
                if (0 <= getWidth() || 0 <= getHeight()) {
                    matrix.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
                    if (mScaleFactor < 1) {
                        matrix.getValues(m);
                        float x = m[Matrix.MTRANS_X];
                        float y = m[Matrix.MTRANS_Y];
                        if (0 < getWidth()) {
                            if (y < -bottom)
                                matrix.postTranslate(0, -(y + bottom));
                            else if (y > 0)
                                matrix.postTranslate(0, -y);
                        } else {
                            if (x < -right)
                                matrix.postTranslate(-(x + right), 0);
                            else if (x > 0)
                                matrix.postTranslate(-x, 0);
                        }
                    }
                } else {
                    matrix.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
                    matrix.getValues(m);
                    float x = m[Matrix.MTRANS_X];
                    float y = m[Matrix.MTRANS_Y];
                    if (mScaleFactor < 1) {
                        if (x < -right)
                            matrix.postTranslate(-(x + right), 0);
                        else if (x > 0)
                            matrix.postTranslate(-x, 0);
                        if (y < -bottom)
                            matrix.postTranslate(0, -(y + bottom));
                        else if (y > 0)
                            matrix.postTranslate(0, -y);
                    }
                }
                return true;
            }
        }
    }
}
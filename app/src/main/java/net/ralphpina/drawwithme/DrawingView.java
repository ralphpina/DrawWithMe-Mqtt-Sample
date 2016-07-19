package net.ralphpina.drawwithme;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

import static net.ralphpina.drawwithme.DrawingMqttClient.DrawingAction.TOUCH_DOWN;
import static net.ralphpina.drawwithme.DrawingMqttClient.DrawingAction.TOUCH_MOVE;
import static net.ralphpina.drawwithme.DrawingMqttClient.DrawingAction.TOUCH_UP;

public class DrawingView extends View implements DrawingMqttClient.MqttDrawerListener {

    private static final String TAG = "DrawingView";

    private final static int[] COLORS          = new int[]{Color.BLACK, Color.BLUE, Color.CYAN, Color.DKGRAY, Color.MAGENTA, Color.RED, Color.YELLOW};
    private static final float TOUCH_TOLERANCE = 4;

    // general
    private Bitmap bitmap;
    private Canvas canvas;
    private Paint  bitmapPaint;

    private int                  userIndex;
    private Map<String, Painter> painters;

    private DrawingMqttClient mqttClient;

    public DrawingView(Context context) {
        super(context);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context,
              attrs);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context,
              attrs,
              defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public DrawingView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context,
              attrs,
              defStyleAttr,
              defStyleRes);
        init();
    }

    private void init() {
        painters = new HashMap<>();
        bitmapPaint = new Paint(Paint.DITHER_FLAG);
    }

    public void setMqttClient(DrawingMqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w,
                            h,
                            oldw,
                            oldh);

        bitmap = Bitmap.createBitmap(w,
                                     h,
                                     Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawBitmap(bitmap,
                          0,
                          0,
                          bitmapPaint);

        Log.e(TAG, "=== onDraw() === painters.values().size() " + painters.values().size());
        for (Painter painter : painters.values()) {
            canvas.drawPath(painter.path,
                            painter.paint);
            canvas.drawPath(painter.circlePath,
                            painter.circlePaint);
        }
    }

    @Override
    public void touchDown(String userId, float x, float y) {
        addToPaintersIfNeeded(userId);
        final Painter painter = painters.get(userId);
        if (painter != null) {
            painter.touchDown(x,
                              y);
            invalidate();
        }
    }

    @Override
    public void touchMove(String userId, float x, float y) {
        final Painter painter = painters.get(userId);
        if (painter != null) {
            painter.touchMove(x,
                              y);
            invalidate();
        }
    }

    @Override
    public void touchUp(String userId) {
        final Painter painter = painters.get(userId);
        if (painter != null) {
            painter.touchUp();
            invalidate();
        }
    }

    @Override
    public void friendDisconnected(String friend) {
        Log.e(TAG,
              "=== friendDisconnected() === friend = " + friend);
        Log.e(TAG,
              "=== friendDisconnected() === painters.containsKey(friend) = " + painters.containsKey(friend));
        if (painters.containsKey(friend)) {
            painters.remove(friend);
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mqttClient.publishDrawingAction(TOUCH_DOWN,
                                                x,
                                                y);
                touchDown(mqttClient.getClientId(),
                          x,
                          y);
                break;
            case MotionEvent.ACTION_MOVE:
                mqttClient.publishDrawingAction(TOUCH_MOVE,
                                                x,
                                                y);
                touchMove(mqttClient.getClientId(),
                          x,
                          y);
                break;
            case MotionEvent.ACTION_UP:
                mqttClient.publishDrawingAction(TOUCH_UP,
                                                -1,
                                                -1);
                touchUp(mqttClient.getClientId());
                break;
        }
        return true;
    }

    private void addToPaintersIfNeeded(String userId) {
        if (!painters.containsKey(userId)) {
            Log.e(TAG, "=== addToPaintersIfNeeded() === userId = " + userId);
            painters.put(userId,
                         new Painter(userId));
        }
    }

    public class Painter {

        private float mX, mY;
        private Path  path;
        private Paint paint;
        private Path  circlePath;
        private Paint circlePaint;

        public Painter(String userId) {
            final boolean self = userId.equals(mqttClient.getClientId());

            circlePath = new Path();
            circlePaint = new Paint();
            circlePaint.setAntiAlias(true);
            circlePaint.setColor(self ? Color.BLUE : COLORS[userIndex + 1]);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeJoin(Paint.Join.MITER);
            circlePaint.setStrokeWidth(4f);

            path = new Path();
            paint = new Paint();
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setColor(self ? Color.GREEN : COLORS[userIndex]);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(12);

            if (!self) {
                bumpIndex();
            }
        }

        private void bumpIndex() {
            if (++userIndex == COLORS.length - 1) {
                userIndex = 0;
            }
        }

        public void touchDown(float x, float y) {
            path.reset();
            path.moveTo(x,
                        y);
            mX = x;
            mY = y;
        }

        public void touchMove(float x, float y) {
            float dx = Math.abs(x - mX);
            float dy = Math.abs(y - mY);
            if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                path.quadTo(mX,
                            mY,
                            (x + mX) / 2,
                            (y + mY) / 2);
                mX = x;
                mY = y;

                circlePath.reset();
                circlePath.addCircle(mX,
                                     mY,
                                     30,
                                     Path.Direction.CW);
            }
        }

        public void touchUp() {
            path.lineTo(mX,
                        mY);
            circlePath.reset();
            // commit the path to our offscreen
            canvas.drawPath(path,
                            paint);
            // kill this so we don't double draw
            path.reset();
        }
    }
}

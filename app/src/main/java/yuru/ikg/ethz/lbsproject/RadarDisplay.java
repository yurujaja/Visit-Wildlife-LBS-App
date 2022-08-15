package yuru.ikg.ethz.lbsproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Location;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class RadarDisplay extends View {

    private int fps = 100;
    private boolean showCircles = true;

    private final int POINT_ARRAY_SIZE = 25;
    Point latestPoint[] = new Point[POINT_ARRAY_SIZE];
    Paint latestPaint[] = new Paint[POINT_ARRAY_SIZE];

    float alpha = 0;

    double offest_i=0;
    double offest_j=0;
    double ratio=0;


    public RadarDisplay(Context context) {
        super(context);
    }

    public RadarDisplay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RadarDisplay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        Paint localPaint = new Paint();
        localPaint.setColor(Color.rgb(138, 57, 225));
        localPaint.setAntiAlias(true);
        localPaint.setStyle(Paint.Style.STROKE);
        localPaint.setStrokeWidth(3.0F);
        localPaint.setAlpha(0);



        int alpha_step = 255 / POINT_ARRAY_SIZE;
        for (int i=0; i < latestPaint.length; i++) {
            latestPaint[i] = new Paint(localPaint);
            latestPaint[i].setAlpha(255 - (i* alpha_step));
        }
    }

    android.os.Handler mHandler = new android.os.Handler();
    Runnable mTick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            mHandler.postDelayed(this, 1000 / fps);
        }
    };

    public void startAnimation() {
        mHandler.removeCallbacks(mTick);
        mHandler.post(mTick);
    }

    public void stopAnimation() {
        mHandler.removeCallbacks(mTick);
    }

    public void setFrameRate(int fps) { this.fps = fps; }
    public int getFrameRate() { return this.fps; };
    public void setShowCircles(boolean showCircles) { this.showCircles = showCircles; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();


        int r = Math.min(width, height);

        int i = r / 2;
        int j = i - 1;

        Paint localPaint = latestPaint[0]; // GREEN

        Paint localPaint2 = new Paint();
        localPaint.setColor(Color.rgb(182, 103, 241));
        localPaint.setAntiAlias(true);
        localPaint.setStyle(Paint.Style.FILL);
        localPaint.setStrokeWidth(3.0F);
        localPaint.setAlpha(128);

        Paint targetPaint = new Paint();
        targetPaint.setColor(Color.rgb(236, 196, 136));
        localPaint.setAntiAlias(true);
        localPaint.setStyle(Paint.Style.FILL);
        localPaint.setStrokeWidth(2.0F);
        localPaint.setAlpha(70);

        if (showCircles) {
            canvas.drawCircle(i, i, j, localPaint);
            canvas.drawCircle(i, i, j, localPaint2);
            canvas.drawCircle(i, i, j, localPaint);
            canvas.drawCircle(i, i, j * 3 / 4, localPaint);
            canvas.drawCircle(i, i, j >> 1, localPaint);
            canvas.drawCircle(i, i, j >> 2, localPaint);
        }

        // draw the location of the target
        // the formular is based on the principle of point location in a circle
        if(offest_i!=0 || offest_j!=0)
            canvas.drawCircle(new Double(i + offest_i*i*ratio).floatValue(),
                    new Double(i+offest_j*i*ratio).floatValue(),20, targetPaint);

        alpha -= 0.5;
        if (alpha < -360) alpha = 0;
        double angle = Math.toRadians(alpha);
        int offsetX =  (int) (i + (float)(i * Math.cos(angle)));
        int offsetY = (int) (i - (float)(i * Math.sin(angle)));

        latestPoint[0]= new Point(offsetX, offsetY);

        for (int x=POINT_ARRAY_SIZE-1; x > 0; x--) {
            latestPoint[x] = latestPoint[x-1];
        }

        for (int x = 0; x < POINT_ARRAY_SIZE; x++) {
            Point point = latestPoint[x];
            if (point != null) {
                canvas.drawLine(i, i, point.x, point.y, latestPaint[x]);
            }
        }

        boolean debug = false;
        if (debug) {
            StringBuilder sb = new StringBuilder(" >> ");
            for (Point p : latestPoint) {
                if (p != null) sb.append(" (" + p.x + "x" + p.y + ")");
            }
        }
    }

    /**
     * This function is called in main activity so current location can be sent to this view.
     * @param i
     * @param j
     * @param rto
     */
    public void setLocation(double i , double  j, double rto){
        offest_i = i;
        offest_j = j;
        ratio = rto;
    }
}

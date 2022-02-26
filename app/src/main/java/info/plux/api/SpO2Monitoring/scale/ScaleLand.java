package info.plux.api.SpO2Monitoring.scale;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import info.plux.api.SpO2Monitoring.R;

public class ScaleLand extends View {

    private Paint outerPaint, middlePaint, innerPaint, limitLinePaint;
    private Paint degreePaint, graduationPaint;

    private static final int GRADUATION_TEXT_SIZE = 14; // in sp
    private int nbGraduations = ScaleConstants.NUMBER_OF_GRADUATIONS;
    private float maxLevel = ScaleConstants.MAX_LEVEL;
    private float minLevel = ScaleConstants.MIN_LEVEL;
    private float rangeOfLevels = ScaleConstants.RANGE_OF_LEVELS;
    private float currentLevel = ScaleConstants.MIN_LEVEL;
    private float limit = 80;
    private float limitPosition;
    private Rect rect = new Rect();

    private int height;
    private int width;
    private int centerY;

    private float outerRectHeight, middleRectHeight, innerRectHeight;
    private float cutoffOuterRect, cutoffMiddleRect;
    private static final float DISTANCE_TO_EDGE = 55;

    private float outerStartX, middleStartX, innerStartX;
    private float outerStartY, middleStartY, innerStartY;
    private float outerEndX, middleEndX, innerEndX;
    private float outerEndY, middleEndY, innerEndY;

    private float innerRectWidth;

    private float lvl;
    private float startGraduation;
    private float inc;


    public ScaleLand(Context context) {
        super(context);
        init(context, null);
    }

    public ScaleLand(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context,attrs);
    }

    public ScaleLand(Context context, AttributeSet attrs, int defStyleAttr) {
        super (context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public void setCurrentLevel(float currentLevel) {
        if (currentLevel > maxLevel) {
            this.currentLevel = maxLevel;
        } else if (currentLevel < minLevel) {
            this.currentLevel = minLevel;
        } else {
            this.currentLevel = currentLevel;
        }

        invalidate();
    }


    public void setLimit(float limit){
        if (limit > maxLevel) {
            this.limit = maxLevel;
        } else if (limit < minLevel) {
            this.limit = minLevel;
        } else {
            this.limit = limit;
        }

        invalidate();
    }

    public void init(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.Scale);
        float scaleDimension = typedArray.getDimension(R.styleable.Scale_dimension, 20f);
        int outerColor = typedArray.getColor(R.styleable.Scale_outerColor, Color.GRAY); // default color
        int middleColor = typedArray.getColor(R.styleable.Scale_middleColor, Color.WHITE);
        int innerColor = typedArray.getColor(R.styleable.Scale_innerColor, Color.RED);

        typedArray.recycle();

        // Defines widths of rectangles
        outerRectHeight = scaleDimension;
        cutoffOuterRect = outerRectHeight / 10; // cutoff on one side
        middleRectHeight = outerRectHeight - 2 * cutoffOuterRect;
        cutoffMiddleRect = middleRectHeight / 20;
        innerRectHeight = middleRectHeight - 2 * cutoffMiddleRect;

        // Defines fill colors
        outerPaint = new Paint();
        outerPaint.setColor(outerColor);
        outerPaint.setStyle(Paint.Style.FILL);

        limitLinePaint = new Paint();
        limitLinePaint.setColor(innerColor);
        limitLinePaint.setStyle(Paint.Style.FILL);
        limitLinePaint.setStrokeWidth(10);

        middlePaint = new Paint();
        middlePaint.setColor(middleColor);
        middlePaint.setStyle(Paint.Style.FILL);

        innerPaint = new Paint();
        innerPaint.setColor(innerColor);
        innerPaint.setStyle(Paint.Style.FILL);

        degreePaint = new Paint();
        degreePaint.setStrokeWidth( cutoffMiddleRect / 2 );
        degreePaint.setColor(outerColor);
        degreePaint.setStyle(Paint.Style.FILL);

        graduationPaint = new Paint();
        graduationPaint.setColor(outerColor);
        graduationPaint.setStyle(Paint.Style.FILL);
        graduationPaint.setAntiAlias(true);
        graduationPaint.setTextSize(Utils.convertDpToPixel(GRADUATION_TEXT_SIZE));




    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        height = getHeight();
        width = getWidth();
        centerY = height / 2;

        // Defines rectangles (nested inside each other) by defining x and y coordinates
        // of left upper corner and right lower corner.

        innerStartX = DISTANCE_TO_EDGE;
        innerStartY = centerY - innerRectHeight / 2;
        innerEndX = width - DISTANCE_TO_EDGE;
        innerEndY = centerY + innerRectHeight / 2;


        middleStartX = innerStartX;
        middleStartY = centerY - middleRectHeight / 2;
        middleEndX = innerEndX;
        middleEndY = centerY + middleRectHeight / 2;


        outerStartX = middleStartX - cutoffOuterRect;
        outerStartY = centerY - outerRectHeight / 2;
        outerEndX = middleEndX + cutoffOuterRect;
        outerEndY = centerY + outerRectHeight / 2;


        innerRectWidth = innerEndX - innerStartX;


        lvl = innerStartX;
        startGraduation = minLevel;
        inc = rangeOfLevels / nbGraduations;

        // Draws labels for each grade on scale except for lowest. The number of grades is nbGraduations - 1.
        while (lvl <= innerEndX) {

            lvl += innerRectWidth / nbGraduations;
            startGraduation += inc;

            canvas.drawLine( lvl, centerY - 6 * outerRectHeight / 10, lvl, centerY - outerRectHeight / 2, degreePaint);

            String txt = ((int) startGraduation) + "%  ";
            graduationPaint.getTextBounds(txt, 0, txt.length(), rect);
            float textWidth = rect.width();

            canvas.drawText(((int) startGraduation) + "%", lvl - textWidth / 2, centerY - 7 * outerRectHeight / 10, graduationPaint);

        }



        RectF outerRect = new RectF();
        outerRect.left = outerStartX;
        outerRect.top = outerStartY;
        outerRect.right = outerEndX;
        outerRect.bottom = outerEndY;

        canvas.drawRect(outerRect, outerPaint);

        RectF middleRect = new RectF();
        middleRect.left = middleStartX;
        middleRect.top = middleStartY;
        middleRect.right = middleEndX;
        middleRect.bottom = middleEndY;

        canvas.drawRect(middleRect, middlePaint);

        // debugging
        // currentLevel = 50;

        // Draws inner rectangle with width depending on current level.
        innerEndX = innerStartX + currentLevel / rangeOfLevels * innerRectWidth;

        RectF innerRect = new RectF();
        innerRect.left = innerStartX;
        innerRect.top = innerStartY;
        innerRect.right = innerEndX;
        innerRect.bottom = innerEndY;

        canvas.drawRect(innerRect, innerPaint);


        float textWidth = rect.width();
        float textHeight = rect.height();
        limitPosition = calculateLimitPosition();

        //canvas.drawLine(centerY - 5 * outerRectHeight / 10, limitPosition + textWidth / 2 - 14, centerY + outerRectHeight / 2, limitPosition + textWidth / 2 - 14, limitLinePaint);
        canvas.drawLine( limitPosition + textWidth / 2 - 14, centerY - 5 * outerRectHeight / 10, limitPosition + textWidth / 2 - 14, centerY + outerRectHeight / 2, limitLinePaint);

        //canvas.drawLine( limitPosition + textWidth / 2 - 14, centerY + outerRectHeight / 2, limitPosition + textWidth / 2 - 14, centerY - 5 * outerRectHeight / 10, limitLinePaint);

    }


    private float calculateLimitPosition()
    {
        float len = middleEndX - middleStartX;
        //float len = middleEndX ;
        float offset = outerEndX - middleEndX;
        //float offset = middleStartX;
        //float offset = middleEndX;

        //return limitPosition;
        return len / 100 * ( limit) + offset;

    }


}

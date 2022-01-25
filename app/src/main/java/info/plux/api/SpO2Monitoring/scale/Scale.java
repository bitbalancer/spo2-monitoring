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

public class Scale extends View {

    private Paint outerPaint, middlePaint, innerPaint;
    private Paint degreePaint, graduationPaint;

    private static final int GRADUATION_TEXT_SIZE = 14; // in sp
    private int nbGraduations = ScaleConstants.NUMBER_OF_GRADUATIONS;
    private float maxLevel = ScaleConstants.MAX_LEVEL;
    private float minLevel = ScaleConstants.MIN_LEVEL;
    private float rangeOfLevels = ScaleConstants.RANGE_OF_LEVELS;
    private float currentLevel = ScaleConstants.MIN_LEVEL;
    private Rect rect = new Rect();

    int height;
    int width;
    int centerX;

    private float outerRectWidth, middleRectWidth, innerRectWidth;
    private float cutoffOuterRect, cutoffMiddleRect;
    private static final float DISTANCE_TO_EDGE = 25f;

    float outerStartX, middleStartX, innerStartX;
    float outerStartY, middleStartY, innerStartY;
    float outerEndX, middleEndX, innerEndX;
    float outerEndY, middleEndY, innerEndY;

    float innerRectHeight;

    float lvl;
    float startGraduation;
    float inc;


    public Scale(Context context) {
        super(context);
        init(context, null);
    }

    public Scale(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context,attrs);
    }

    public Scale(Context context, AttributeSet attrs, int defStyleAttr) {
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


    public void init(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.Scale);
        float scaleDimension = typedArray.getDimension(R.styleable.Scale_dimension, 20f);
        int outerColor = typedArray.getColor(R.styleable.Scale_outerColor, Color.GRAY); // default color
        int middleColor = typedArray.getColor(R.styleable.Scale_middleColor, Color.WHITE);
        int innerColor = typedArray.getColor(R.styleable.Scale_innerColor, Color.RED);

        typedArray.recycle();

        // Defines widths of rectangles
        outerRectWidth = scaleDimension;
        cutoffOuterRect = outerRectWidth / 10; // cutoff on one side
        middleRectWidth = outerRectWidth - 2 * cutoffOuterRect;
        cutoffMiddleRect = middleRectWidth / 20;
        innerRectWidth = middleRectWidth - 2 * cutoffMiddleRect;

        // Defines fill colors
        outerPaint = new Paint();
        outerPaint.setColor(outerColor);
        outerPaint.setStyle(Paint.Style.FILL);

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
        centerX = width / 2;

        // Defines rectangles (nested inside each other) by defining x and y coordinates
        // of left upper corner and right lower corner.

        innerStartX = centerX - innerRectWidth / 2;
        innerStartY = DISTANCE_TO_EDGE;
        innerEndX = centerX + innerRectWidth / 2;
        innerEndY = height - DISTANCE_TO_EDGE;


        middleStartX = centerX - middleRectWidth / 2;
        middleStartY = innerStartY;
        middleEndX = centerX + middleRectWidth / 2;
        middleEndY = innerEndY;


        outerStartX = centerX - outerRectWidth / 2;
        outerStartY = middleStartY - cutoffOuterRect;
        outerEndX = centerX + outerRectWidth / 2;
        outerEndY = middleEndY + cutoffOuterRect;


        innerRectHeight = innerEndY - innerStartY;


        lvl = innerStartY;
        startGraduation = maxLevel;
        inc = rangeOfLevels / nbGraduations;

        // Draws labels for each grade on scale except for lowest. The number of grades is nbGraduations - 1.
        while (lvl <= innerEndY - inc ) {

            // Hint: Although lines may be too thin to be drawn, they are nevertheless visible in layout.
            canvas.drawLine(centerX - 6 * outerRectWidth / 10, lvl, centerX - outerRectWidth / 2, lvl, degreePaint);

            String txt = ((int) startGraduation) + "%  ";
            graduationPaint.getTextBounds(txt, 0, txt.length(), rect);
            float textWidth = rect.width();
            float textHeight = rect.height();

            canvas.drawText(((int) startGraduation) + "%", centerX - 7 * outerRectWidth / 10 - textWidth, lvl + textHeight / 2, graduationPaint);

            lvl += innerRectHeight / nbGraduations;
            startGraduation -= inc;
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

        // Draws inner rectangle with height depending on current level.
        innerStartY = innerStartY + (maxLevel - currentLevel) / rangeOfLevels * innerRectHeight;

        RectF innerRect = new RectF();
        innerRect.left = innerStartX;
        innerRect.top = innerStartY;
        innerRect.right = innerEndX;
        innerRect.bottom = innerEndY;

        canvas.drawRect(innerRect, innerPaint);


    }
}

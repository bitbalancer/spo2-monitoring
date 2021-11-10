package info.plux.api.SpO2Monitoring.thermometer;

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

public class ThermometerHorizontal extends View {

    private float outerCircleRadius, outerRectRadius;
    private Paint outerPaint;
    private float middleCircleRadius, middleRectRadius;
    private Paint middlePaint;
    private float innerCircleRadius, innerRectRadius;
    private Paint innerPaint;
    private Paint degreePaint, graduationPaint;

    private static final int GRADUATION_TEXT_SIZE = 16; // in sp
    private static float DEGREE_WIDTH = 30; //30
    private static final int NB_GRADUATIONS = ThermoConstants.NUMBER_OF_GRADUATIONS;
    public static final float MIN_TEMP = ThermoConstants.MIN_TEMP;
    public static final float MAX_TEMP = ThermoConstants.MAX_TEMP;
    private static final float RANGE_TEMP = ThermoConstants.RANGE_TEMP;
    private static final int NB_GRADUATIONS_F = 8;
    private static final float MAX_TEMP_F = 120, MIN_TEMP_F = -30;
    private static final float RANGE_TEMP_F = 150;
    private int nbGraduations = NB_GRADUATIONS;
    private float maxTemp = MAX_TEMP;
    private float minTemp = MIN_TEMP;
    private float rangeTemp = RANGE_TEMP;
    private float currentTemp = MIN_TEMP;
    private Rect rect = new Rect();

    public ThermometerHorizontal(Context context) {
        super(context);
        init(context, null);
    }

    public ThermometerHorizontal(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context,attrs);
    }

    public ThermometerHorizontal(Context context, AttributeSet attrs, int defStyleAttr) {
        super (context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public void setCurrentTemp(float currentTemp) {
        if (currentTemp > maxTemp) {
            this.currentTemp = maxTemp;
        } else if (currentTemp < minTemp) {
            this.currentTemp = minTemp;
        } else {
            this.currentTemp = currentTemp;
        }

        invalidate();
    }

    public float getMinTemp() {
        return minTemp;
    }

    public void init(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.Thermometer);
        outerCircleRadius = typedArray.getDimension(R.styleable.Thermometer_radius, 20f);
        int outerColor = typedArray.getColor(R.styleable.Thermometer_outerColor, Color.GRAY);
        int middleColor = typedArray.getColor(R.styleable.Thermometer_middleColor, Color.WHITE);
        int innerColor = typedArray.getColor(R.styleable.Thermometer_innerColor, Color.RED);

        typedArray.recycle();

        outerRectRadius = outerCircleRadius / 2;
        outerPaint = new Paint();

        outerPaint.setColor(outerColor);
        outerPaint.setStyle(Paint.Style.FILL);

        middleCircleRadius = outerCircleRadius -5;
        middleRectRadius = outerRectRadius -5;
        middlePaint = new Paint();
        middlePaint.setColor(middleColor);
        middlePaint.setStyle(Paint.Style.FILL);

        innerCircleRadius = middleCircleRadius - middleCircleRadius / 6;
        innerRectRadius = middleRectRadius - middleRectRadius / 6;//
        innerPaint = new Paint();
        innerPaint.setColor(innerColor);
        innerPaint.setStyle(Paint.Style.FILL);

        DEGREE_WIDTH = middleCircleRadius / 8;

        degreePaint = new Paint();
        degreePaint.setStrokeWidth(middleCircleRadius / 16);
        degreePaint.setColor(outerColor);
        degreePaint.setStyle(Paint.Style.FILL);

        graduationPaint = new Paint();
        graduationPaint.setColor(outerColor);
        graduationPaint.setStyle(Paint.Style.FILL);
        graduationPaint.setAntiAlias(true);
        graduationPaint.setTextSize(Utils.convertDpToPixel(GRADUATION_TEXT_SIZE));

    }

    public void changeUnit(boolean isCelsius) {
        if (isCelsius) {
            nbGraduations = NB_GRADUATIONS;
            maxTemp = MAX_TEMP;
            minTemp = MIN_TEMP;
            rangeTemp = RANGE_TEMP;
        } else {
            nbGraduations = NB_GRADUATIONS_F;
            maxTemp = MAX_TEMP_F;
            minTemp = MIN_TEMP_F;
            rangeTemp = RANGE_TEMP_F;
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int height = getHeight();
        int width = getWidth();

        float circleCenterX = outerCircleRadius;
        float circleCenterY = height/2;
        float outerEndX = width-100;
        float middleEndX = outerEndX - 5;


        float innerEffectEndX = middleEndX - middleRectRadius - 10;
        float innerEffectStartX = circleCenterX + outerCircleRadius + 10;
        float innerRectHeight = innerEffectEndX - innerEffectStartX;
        //??? bin mir nicht sicher
        float innerEndX = innerEffectEndX - (maxTemp - currentTemp) / rangeTemp * innerRectHeight;


        RectF outerRect = new RectF();
        outerRect.left = circleCenterX;
        outerRect.top = circleCenterY-outerRectRadius;
        outerRect.right = outerEndX;
        outerRect.bottom = circleCenterY+outerRectRadius;

        canvas.drawRoundRect(outerRect, outerRectRadius, outerRectRadius, outerPaint);
        canvas.drawCircle(circleCenterX, circleCenterY, outerCircleRadius, outerPaint);

        /*
        Paint myPaint = new Paint();

        myPaint.setColor(Color.RED);
        myPaint.setStyle(Paint.Style.FILL);

        RectF myRect = new RectF();
        myRect.left = 0;
        myRect.top = 0;
        myRect.right = width/2;
        myRect.bottom = height/2;

        canvas.drawRoundRect(myRect, 500, 500, myPaint);

        //canvas.drawRect(0,0,width/2,height/2,myPaint) ;
*/



        RectF middleRect = new RectF(); //left, top, right, bottom
        middleRect.left = circleCenterX;
        middleRect.top = circleCenterY-middleRectRadius;
        middleRect.right = middleEndX;
        middleRect.bottom = circleCenterY+middleRectRadius;

        canvas.drawRoundRect(middleRect, middleRectRadius, middleRectRadius, middlePaint);
        canvas.drawCircle(circleCenterX, circleCenterY, middleCircleRadius, middlePaint);

        RectF innerRect = new RectF();
        innerRect.left = circleCenterX;
        innerRect.top = circleCenterY - innerRectRadius;
        innerRect.right = innerEndX;
        innerRect.bottom = circleCenterY + innerRectRadius;

        canvas.drawRect(innerRect, innerPaint);
        canvas.drawCircle(circleCenterX, circleCenterY, innerCircleRadius, innerPaint);

        float tmp = innerEffectStartX;
        float startGraduation = minTemp;
        float endGraduation = maxTemp;
        float inc = rangeTemp / nbGraduations;

        while (tmp <= innerEffectEndX) {
            canvas.drawLine(tmp, circleCenterY-outerRectRadius-DEGREE_WIDTH, tmp, circleCenterY-outerRectRadius, degreePaint);


            String txt = ((int) endGraduation) + "µS"; //"°"
            graduationPaint.getTextBounds(txt, 0, txt.length(), rect);
            float textWidth = rect.width();
            float textHeight = rect.height();

            canvas.drawText(((int) startGraduation) + " µS", tmp ,
                    circleCenterY - outerRectRadius - textHeight,graduationPaint); //"°"
            tmp += (innerEffectEndX - innerEffectStartX) / nbGraduations;
            startGraduation += inc;


        }

        //canvas.drawLine(0,0,width,height, degreePaint);


    }
}

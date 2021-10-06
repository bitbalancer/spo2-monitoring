package info.plux.api.Observant_v32.thermometer;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;


//http://stackoverflow.com/questions/4605527/converting-pixels-to-dp
//The above method results accurate method compared to below methods
//http://stackoverflow.com/questions/8309354/formula-px-to-dp-dp-to-px-android
//http://stackoverflow.com/questions/13751080/converting-pixels-to-dpi-for-mdpi-and-hdpi-screens

public class Utils {
    private static Context context;

    Utils(Context context){
        this.context = context;
    }

    public static float convertPixelsToDp(float px){
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float dp = px / (metrics.densityDpi / 160f);
        return Math.round(dp);
    }

    public static float convertDpToPixel(float dp){
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float px = dp * (metrics.densityDpi / 160f);
        return Math.round(px);
    }

    private int convertDpToPx(int dp){
        return Math.round(dp*(context.getResources().getDisplayMetrics().xdpi/DisplayMetrics.DENSITY_DEFAULT));

    }

    private int convertPxToDp(int px){
        return Math.round(px/(Resources.getSystem().getDisplayMetrics().xdpi/DisplayMetrics.DENSITY_DEFAULT));
    }

    private float dpFromPx(float px)
    {
        return px / context.getResources().getDisplayMetrics().density;
    }

    private float pxFromDp(float dp)
    {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}

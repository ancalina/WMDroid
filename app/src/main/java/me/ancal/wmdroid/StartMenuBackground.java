package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.IOException;

/** shell32 3C824 -> 40340(1) -> 3C2A0: stwater, repeated BitBlt, source + metric12.
 * Keeps the source texture in VGA coordinates instead of stretching it to menu height.
 */
final class StartMenuBackground extends me.ancal.wmdroid.sdk.WmStartBackground {
    StartMenuBackground(Context context,boolean rose) {super(bitmap(context,rose));}
    static Bitmap bitmap(Context context,boolean rose) {
        try {
            return LauncherTheme.wallpaper(context,rose?"start_mugunghwa.gif":"backgrounds/classic.gif",rose);
        } catch(IOException missing){
            Bitmap flat=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);flat.eraseColor(LauncherTheme.base(context,rose));return flat;
        }
    }
}

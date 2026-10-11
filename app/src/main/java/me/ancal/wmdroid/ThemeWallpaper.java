package me.ancal.wmdroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import java.io.IOException;

/** ROM wallpaper fills the display; UI coordinates remain uniformly scaled independently. */
final class ThemeWallpaper extends View {
    private final Bitmap bitmap;
    private final Paint paint = new Paint();
    ThemeWallpaper(Context context, String asset) throws IOException {
        super(context);
        bitmap=LauncherTheme.wallpaper(context,asset,asset.equals("wallpaper_mugunghwa.gif"));
        paint.setFilterBitmap(false);
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(bitmap,null,new Rect(0,0,getWidth(),getHeight()),paint);
    }
}

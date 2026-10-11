package me.ancal.wmdroid;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
/** Host navigation/localization and original sound resources feed the shared body. */
final class StatusPanel extends me.ancal.wmdroid.sdk.WmStatusPanel {
 interface Host {void launch(Intent intent);void changed();void backlight();void hide();void settings();}
 StatusPanel(Context context,Typeface regular,Typeface bold,int kind,Host host){
  super(context,regular,kind,new me.ancal.wmdroid.sdk.WmStatusPanel.Host(){
   private VolumeFeedback feedback;
   public void launch(Intent intent){host.launch(intent);}public void changed(){host.changed();}
   public void backlight(){host.backlight();}public void hide(){host.hide();}public void settings(){host.settings();}
   public String text(String authored){return UiLanguage.t(context,authored);}
   public int color(int original){return LauncherTheme.color(context,original,context.getSharedPreferences("today",Context.MODE_PRIVATE).getBoolean("rose",false));}
   public void attached(){if(kind==1&&feedback==null)feedback=new VolumeFeedback(context);}
   public void feedback(boolean ring,int step){if(feedback==null)feedback=new VolumeFeedback(context);feedback.play(ring,step);}
   public void release(){if(feedback!=null){feedback.release();feedback=null;}}
  });
 }
}

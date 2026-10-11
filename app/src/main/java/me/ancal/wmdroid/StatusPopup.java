package me.ancal.wmdroid;
/** Host theme adapter for the SDK's shared native bubble geometry. */
final class StatusPopup extends me.ancal.wmdroid.sdk.WmBubble {
    StatusPopup(android.content.Context context,int anchor,boolean rose){
        super(anchor,LauncherTheme.color(context,rose?0xffc72c76:0xff15afbc,rose),LauncherTheme.color(context,rose?0xffd25691:0xff149ca7,rose));
    }
}

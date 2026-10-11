package me.ancal.wmdroid;

/** shell32 1F7D4 / 1F8AC / 1F928, default thresholds from 271B8.
 * AC without the charging flag selects the full icon, regardless of remaining percent.
 */
final class BatteryIconState {
    enum State { CHARGE, FULL, HIGH, MEDIUM, LOW, CRITICAL }
    static State state(int percent,boolean externalPower,boolean charging){
        if(externalPower)return charging?State.CHARGE:State.FULL;
        if(percent<0||percent>100)return State.FULL;
        if(percent<21)return State.CRITICAL;
        if(percent<41)return State.LOW;
        if(percent<61)return State.MEDIUM;
        if(percent<81)return State.HIGH;
        return State.FULL;
    }
    static int icon(State state){switch(state){
        case CHARGE:return R.drawable.wm_battery_charge_hd;
        case HIGH:return R.drawable.wm_battery_medium_hd;
        case MEDIUM:return R.drawable.wm_battery_half_hd;
        case LOW:return R.drawable.wm_battery_low_hd;
        case CRITICAL:return R.drawable.wm_battery_critical_hd;
        default:return R.drawable.wm_battery_full_hd;
    }}
}

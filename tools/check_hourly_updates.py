#!/usr/bin/env python3
"""Exercise production update timing and host callbacks with controlled network/UI."""
import argparse
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', type=Path, required=True)
    args = parser.parse_args()
    host = (ROOT / 'app/src/main/java/me/ancal/wmdroid/TodayActivity.java').read_text()
    methods = '\n'.join(method(host, signature) for signature in (
        '    private void checkAutomaticUpdates(){', '    private void showPendingUpdate(){',
        '    private void checkUpdates(boolean automatic){'))
    methods = methods.replace('LauncherUpdates.check(', 'TestNetwork.check(')
    assert 'checkAutomaticUpdates(); refresh();' in host
    assert 'updateWorker.shutdownNow();' in host
    assert 'if(key!=null&&key.startsWith("update_"))return;' in host
    java = r'''package me.ancal.wmdroid;
import java.io.IOException;import java.util.*;
class FrameLayout {} class LinearLayout {}
class TextView {String text;Object parent=new LinearLayout();void setText(String s){text=s;}Object getParent(){return parent;}}
class PackageManager {static class NameNotFoundException extends Exception {}
static class Info {String versionName="0.1.3";}
Info getPackageInfo(String n,int flags)throws NameNotFoundException{return new Info();}}
class Prefs {Map<String,Object> values=new HashMap<>();
long getLong(String k,long d){Object v=values.get(k);return v==null?d:(Long)v;}
String getString(String k,String d){Object v=values.get(k);return v==null?d:(String)v;}
Editor edit(){return new Editor();}class Editor {
Editor putLong(String k,long v){values.put(k,v);return this;}
Editor putString(String k,String v){values.put(k,v);return this;}void apply(){}}}
class Jobs {List<Runnable> jobs=new ArrayList<>();void execute(Runnable r){jobs.add(r);}void post(Runnable r){jobs.add(r);}void run(){jobs.remove(0).run();}}
class TestNetwork {static LauncherUpdates.Result result;static boolean fail;
static LauncherUpdates.Result check(String v)throws IOException {if(fail)throw new IOException("offline");return result;}}
class UiLanguage {static String format(Object c,String s,Object... args){return String.format(s,args);}}
public class HourlyUpdateCheck {
boolean resumed,destroyed,updateCheckBusy,todayForeground;FrameLayout startMenu,updateOverlay;Object currentDialog;
Runnable pendingApplet;TextView updateMessage;LauncherUpdates.Result pendingUpdate;Prefs prefs=new Prefs();
Jobs updateWorker=new Jobs(),handler=new Jobs();int popups,buttons;
String ui(String s){return s;}String getPackageName(){return "me.ancal.wmdroid";}
PackageManager getPackageManager(){return new PackageManager();}
TextView updatePopup(String s,LauncherUpdates.Result r){popups++;startMenu=new FrameLayout();TextView v=new TextView();v.text=s;if(r!=null)buttons++;return v;}
void addReleaseButton(LinearLayout p,LauncherUpdates.Result r){buttons++;}
METHODS
static void expect(boolean b,String why){if(!b)throw new AssertionError(why);}
void finish(){updateWorker.run();handler.run();}
public static void main(String[] args)throws Exception {
long hour=LauncherUpdates.CHECK_INTERVAL,now=1700000000000L;
expect(hour==3600000,"hour unit");
expect(LauncherUpdates.due(now,0),"first launch");
expect(!LauncherUpdates.due(now,now),"no immediate repeat");
expect(!LauncherUpdates.due(now,now-hour+1),"not before hour");
expect(LauncherUpdates.due(now,now-hour),"exact hour");
expect(LauncherUpdates.due(now,now-hour-1),"overdue");
expect(LauncherUpdates.due(now,now+1),"clock rollback");
expect(!LauncherUpdates.shouldNotify(null,null),"missing release");
expect(!LauncherUpdates.shouldNotify(new LauncherUpdates.Result("v0.1.3",false),null),"current release");
LauncherUpdates.Result newer=new LauncherUpdates.Result("v0.1.4",true);
expect(LauncherUpdates.shouldNotify(newer,null),"new version");
expect(!LauncherUpdates.shouldNotify(newer,"0.1.4"),"same version once");
HourlyUpdateCheck h=new HourlyUpdateCheck();h.checkAutomaticUpdates();expect(h.updateWorker.jobs.isEmpty(),"paused no network");
h.resumed=true;h.checkAutomaticUpdates();expect(h.updateCheckBusy&&h.updateWorker.jobs.size()==1&&h.popups==0,"silent automatic request");
h.checkAutomaticUpdates();expect(h.updateWorker.jobs.size()==1,"in-flight dedupe");
h.checkUpdates(false);TextView joined=h.updateMessage;
expect(h.popups==1&&h.updateWorker.jobs.size()==1,"manual joins automatic request");
TestNetwork.result=newer;h.finish();expect(!h.updateCheckBusy&&h.buttons==1&&joined.text.contains("0.1.4"),"manual gets automatic result");
h.startMenu=null;h.pendingUpdate=newer;h.showPendingUpdate();expect(h.popups==1,"already notified not repeated");
h.checkAutomaticUpdates();expect(h.updateWorker.jobs.isEmpty(),"restart deadline retained");
h=new HourlyUpdateCheck();h.resumed=true;h.currentDialog=new Object();h.checkAutomaticUpdates();h.finish();
expect(h.pendingUpdate==newer&&h.popups==0,"applet not interrupted");
h.currentDialog=null;h.startMenu=new FrameLayout();h.showPendingUpdate();expect(h.popups==0,"menu not interrupted");
h.startMenu=null;h.showPendingUpdate();expect(h.popups==1&&h.pendingUpdate==null,"notice when home idle");
h=new HourlyUpdateCheck();h.resumed=true;h.currentDialog=new Object();h.todayForeground=true;h.pendingUpdate=newer;h.showPendingUpdate();expect(h.popups==1,"hidden applet must not block Today notice");
h=new HourlyUpdateCheck();h.resumed=true;h.pendingApplet=()->{};h.pendingUpdate=newer;h.showPendingUpdate();expect(h.popups==0&&h.pendingUpdate==newer,"opening applet must not be interrupted");h.pendingApplet=null;h.showPendingUpdate();expect(h.popups==1,"notice after pending action ends");
h=new HourlyUpdateCheck();h.resumed=true;h.checkAutomaticUpdates();h.resumed=false;h.finish();
expect(h.popups==0&&h.pendingUpdate==newer,"background no popup");h.resumed=true;h.checkAutomaticUpdates();expect(h.popups==1&&h.updateWorker.jobs.isEmpty(),"resume shows queued notice without recheck");
h=new HourlyUpdateCheck();h.resumed=true;TestNetwork.fail=true;h.checkAutomaticUpdates();h.finish();
expect(h.popups==0&&!h.updateCheckBusy,"failure silent and busy released");h.checkAutomaticUpdates();expect(h.updateWorker.jobs.isEmpty(),"failure waits hour");TestNetwork.fail=false;
h=new HourlyUpdateCheck();h.resumed=true;h.checkAutomaticUpdates();h.checkUpdates(false);h.startMenu=null;h.finish();expect(h.popups==1,"dismissed manual popup not reopened");
h=new HourlyUpdateCheck();h.resumed=true;TestNetwork.result=null;h.checkAutomaticUpdates();h.finish();expect(h.popups==0,"missing release silent");
h=new HourlyUpdateCheck();h.resumed=true;TestNetwork.result=newer;h.checkAutomaticUpdates();h.destroyed=true;h.finish();expect(h.popups==0,"destroyed no UI");
expect(LauncherUpdates.compareVersions("v0.1.4","0.1.3")==1,"version comparison retained");
System.out.println("PASS: hourly boundary, clock rollback, saved deadline, worker/dedupe, manual joining, silent failure, idle popup, hidden/opening applets, pause/resume/destroy, one notice per version");
}}
'''.replace('METHODS', methods)
    with tempfile.TemporaryDirectory(prefix='hourly-updates-') as folder:
        work = Path(folder)
        (work / 'HourlyUpdateCheck.java').write_text(java)
        (work / 'LauncherUpdates.java').write_text((ROOT / 'app/src/main/java/me/ancal/wmdroid/LauncherUpdates.java').read_text())
        for name, text in {
            'JSONException': 'public class JSONException extends Exception {}',
            'JSONObject': 'public class JSONObject {public JSONObject(String s)throws JSONException{} public Object get(String key)throws JSONException{return null;}}',
        }.items():
            path = work / 'org/json' / (name + '.java')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('package org.json;\n' + text)
        subprocess.run([str(args.jdk / 'bin/javac'), '-d', str(work),
                        *map(str, work.rglob('*.java'))], check=True)
        subprocess.run([str(args.jdk / 'bin/java'), '-cp', str(work),
                        'me.ancal.wmdroid.HourlyUpdateCheck'], check=True)


if __name__ == '__main__':
    main()

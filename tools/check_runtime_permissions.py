#!/usr/bin/env python3
"""Check the production runtime request against Android version boundaries."""
import argparse
import subprocess
import tempfile
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', type=Path, required=True)
    args = parser.parse_args()
    source = (ROOT / 'app/src/main/java/me/ancal/wmdroid/DeviceApi.java').read_text()
    start = source.index('    static void request(')
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    method = source[start:end]
    manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
    names = {p.get('{http://schemas.android.com/apk/res/android}name')
             for p in manifest.findall('uses-permission')}
    assert {'android.permission.ACCESS_FINE_LOCATION',
            'android.permission.ACCESS_COARSE_LOCATION'} <= names
    java = '''import android.app.Activity;import android.os.Build;
public class PermissionCheck {
METHOD
static void check(boolean ok){if(!ok)throw new AssertionError();}
public static void main(String[] args){
String fine=android.Manifest.permission.ACCESS_FINE_LOCATION;
String coarse=android.Manifest.permission.ACCESS_COARSE_LOCATION;
for(int version:new int[]{14,22,23,30,31,33,35,36}){
Build.VERSION.SDK_INT=version;Activity a=new Activity();String[] original={fine};
request(a,original,11);check(original.length==1&&original[0].equals(fine));
if(version<23){check(a.names==null);continue;}
check(a.code==11&&a.names[0].equals(fine));
check(a.names.length==(version>=31?2:1));
if(version>=31)check(a.names[1].equals(coarse));
request(a,new String[]{fine,coarse},16);check(a.names.length==2&&a.code==16);
request(a,new String[]{"android.permission.BLUETOOTH_CONNECT"},11);
check(a.names.length==1&&a.names[0].equals("android.permission.BLUETOOTH_CONNECT"));
request(a,new String[]{"android.permission.POST_NOTIFICATIONS"},17);
check(a.names.length==1&&a.code==17);
}
System.out.println("PASS API 14–36 request dispatch, location pair, unchanged inputs and request codes");
}}
'''.replace('METHOD', method)
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        files = {
            'PermissionCheck.java': java,
            'android/app/Activity.java': 'package android.app;public class Activity {public String[] names;public int code;public void requestPermissions(String[] n,int c){names=n;code=c;}}',
            'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT;}}',
            'android/Manifest.java': 'package android;public class Manifest {public static class permission {public static final String ACCESS_FINE_LOCATION="android.permission.ACCESS_FINE_LOCATION",ACCESS_COARSE_LOCATION="android.permission.ACCESS_COARSE_LOCATION";}}',
        }
        for name, value in files.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(value)
        subprocess.run([str(args.jdk / 'bin/javac'), '-d', str(root),
                        *[str(root / name) for name in files]], check=True)
        subprocess.run([str(args.jdk / 'bin/java'), '-cp', str(root), 'PermissionCheck'], check=True)


if __name__ == '__main__':
    main()

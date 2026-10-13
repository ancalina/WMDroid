#!/usr/bin/env python3
"""Exercise the production language resolver without changing device settings."""
import argparse
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', type=Path, required=True)
    args = parser.parse_args()
    sources = {
        'android/os/Build.java': '''package android.os;
public class Build {public static class VERSION {public static int SDK_INT;}}''',
        'android/os/LocaleList.java': '''package android.os;
import java.util.Locale;
public class LocaleList {public Locale[] values; public int size(){return values.length;}
public Locale get(int i){return values[i];}}''',
        'android/content/res/Configuration.java': '''package android.content.res;
import java.util.Locale; import android.os.LocaleList;
public class Configuration {public Locale locale; public LocaleList locales=new LocaleList();
public LocaleList getLocales(){return locales;}}''',
        'android/content/res/Resources.java': '''package android.content.res;
public class Resources {public Configuration config=new Configuration();
public Configuration getConfiguration(){return config;}}''',
        'android/content/SharedPreferences.java': '''package android.content;
public class SharedPreferences {public String value;public boolean migrated;
public String getString(String key,String fallback){return value==null?fallback:value;}
public boolean getBoolean(String key,boolean fallback){return migrated;}
public Editor edit(){return new Editor();}
public class Editor {public Editor remove(String key){value=null;return this;}
public Editor putBoolean(String key,boolean v){migrated=v;return this;}
public void apply(){}}}''',
        'android/content/Context.java': '''package android.content;
import android.content.res.Resources;
public class Context {public static final int MODE_PRIVATE=0;
public SharedPreferences prefs=new SharedPreferences();public Resources resources=new Resources();
public SharedPreferences getSharedPreferences(String n,int m){return prefs;}
public Resources getResources(){return resources;}}''',
        'me/ancal/wmdroid/sdk/WmStrings.java': '''package me.ancal.wmdroid.sdk;
import android.content.Context;import java.util.Locale;
public class WmStrings {public static String[] available={"ko","en"};
public static String[] languages(Context c){return available;}
public static String language(Context c,String s){return s;}
public static Locale locale(String s){return Locale.forLanguageTag(s);}
public static String text(Context c,String l,String s){return s;}}''',
        'me/ancal/wmdroid/SystemLanguageCheck.java': '''package me.ancal.wmdroid;
import android.content.Context;import android.os.Build;import java.util.Locale;
import me.ancal.wmdroid.sdk.WmStrings;
public class SystemLanguageCheck {
static void check(Context c,String expected){String actual=UiLanguage.language(c);
if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
public static void main(String[] args){Context c=new Context();
c.prefs.value="ko";UiLanguage.migrate(c);
if(c.prefs.value!=null||!c.prefs.migrated)throw new AssertionError("Legacy preference not reset");
c.prefs.value="en";UiLanguage.migrate(c);
if(!"en".equals(c.prefs.value))throw new AssertionError("Migration reset repeated");
for(int api:new int[]{14,21,23,24,35}){Build.VERSION.SDK_INT=api;
for(String tag:new String[]{"ko-KR","en-US","en-GB","fr-FR"}){
Locale locale=Locale.forLanguageTag(tag);c.resources.config.locale=locale;
c.resources.config.locales.values=new Locale[]{locale};c.prefs.value=null;
check(c,tag.startsWith("ko")?"ko":"en");
c.prefs.value="ko";check(c,"ko");c.prefs.value="en";check(c,"en");
c.prefs.value="system";check(c,tag.startsWith("ko")?"ko":"en");}
}
Build.VERSION.SDK_INT=24;c.prefs.value=null;
c.resources.config.locales.values=new Locale[]{Locale.FRENCH,Locale.KOREAN,Locale.ENGLISH};check(c,"ko");
c.resources.config.locales.values=new Locale[]{Locale.ENGLISH,Locale.KOREAN};check(c,"en");
WmStrings.available=new String[]{"ko","en","en-gb"};
c.resources.config.locales.values=new Locale[]{Locale.UK};check(c,"en-gb");
c.resources.config.locales.values=new Locale[]{};check(c,"en");
Build.VERSION.SDK_INT=14;c.resources.config.locale=null;check(c,"en");
System.out.println("PASS: one-time preference reset, system locales, regional tags, priority, fallback, explicit override, API 14/21/23/24/35");}}
''',
    }
    (ROOT / 'build').mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='system-language-', dir=ROOT / 'build') as folder:
        work = Path(folder)
        sources['me/ancal/wmdroid/UiLanguage.java'] = (ROOT / 'app/src/main/java/me/ancal/wmdroid/UiLanguage.java').read_text()
        for name, text in sources.items():
            path = work / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        subprocess.run([str(args.jdk / 'bin/javac'), '-d', str(work),
                        *map(str, work.rglob('*.java'))], check=True)
        subprocess.run([str(args.jdk / 'bin/java'), '-cp', str(work),
                        'me.ancal.wmdroid.SystemLanguageCheck'], check=True)


if __name__ == '__main__':
    main()

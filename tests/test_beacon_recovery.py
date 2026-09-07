#!/usr/bin/env python3
"""Run the production beacon advertiser/service against deterministic Android fakes.

Requires Python 3 and a JDK. No SDK, phone, network, or third-party libraries.
These tests cover lifecycle/callback behavior, not RF transmission or OEM Doze.
"""
from pathlib import Path
import subprocess
import tempfile
import textwrap

ROOT = Path(__file__).resolve().parents[1]

# Framework fakes expose callbacks without delivering success automatically.
# Handler cancellation is scoped to the owning Handler, just as on Android.
SOURCES = {
    "android.os.Handler": """
        public class Handler {
            static class Job {
                Handler owner; Runnable runnable; long due, sequence;
                Job(Handler h, Runnable r, long t) {
                    owner=h; runnable=r; due=t; sequence=++nextSequence;
                }
            }
            public static long now;
            private static long nextSequence;
            private static final java.util.PriorityQueue<Job> jobs = new java.util.PriorityQueue<>(
                (a,b) -> a.due==b.due ? Long.compare(a.sequence,b.sequence) : Long.compare(a.due,b.due));
            public Handler(Looper looper) {}
            public boolean post(Runnable r) { return postDelayed(r,0); }
            public boolean postDelayed(Runnable r,long delay) {
                jobs.add(new Job(this,r,now+delay)); return true;
            }
            public void removeCallbacks(Runnable r) { jobs.removeIf(j -> j.owner==this && j.runnable==r); }
            public void removeCallbacksAndMessages(Object token) { jobs.removeIf(j -> j.owner==this); }
            public static void advance(long ms) {
                long target=now+ms;
                while (!jobs.isEmpty() && jobs.peek().due<=target) {
                    Job job=jobs.remove(); now=job.due; job.runnable.run();
                }
                now=target;
            }
            public static void reset() { jobs.clear(); now=0; }
        }
    """,
    "android.os.Looper": "public class Looper { public static Looper getMainLooper() { return new Looper(); } }",
    "android.os.SystemClock": "public class SystemClock { public static long elapsedRealtime() { return Handler.now; } }",
    "android.os.Process": "public class Process { public static int myPid() { return 123; } }",
    "android.os.IBinder": "public interface IBinder {}",
    "android.os.Build": """
        public class Build {
            public static class VERSION { public static int SDK_INT=36; }
            public static class VERSION_CODES { public static final int M=23,Q=29,TIRAMISU=33; }
        }
    """,
    "android.os.PowerManager": """
        public class PowerManager {
            public static final String ACTION_DEVICE_IDLE_MODE_CHANGED="idle",
                ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED="light-idle", ACTION_POWER_SAVE_MODE_CHANGED="power-save";
            public boolean interactive=true,idle,lightIdle,powerSave;
            public boolean isInteractive() { return interactive; }
            public boolean isDeviceIdleMode() { return idle; }
            public boolean isDeviceLightIdleMode() { return lightIdle; }
            public boolean isPowerSaveMode() { return powerSave; }
        }
    """,
    "android.content.Context": """
        public class Context {
            public static final String BLUETOOTH_SERVICE="bluetooth",POWER_SERVICE="power",NOTIFICATION_SERVICE="notification";
            public static final int RECEIVER_EXPORTED=2,RECEIVER_NOT_EXPORTED=4;
            public final android.os.PowerManager power=new android.os.PowerManager();
            public final java.util.Map<BroadcastReceiver,IntentFilter> receivers=new java.util.LinkedHashMap<>();
            public Context getApplicationContext() { return this; }
            public String getPackageName() { return "com.jpitsg.sysman"; }
            public android.content.pm.PackageManager getPackageManager() { return new android.content.pm.PackageManager(); }
            public Object getSystemService(String name) {
                if (name.equals(BLUETOOTH_SERVICE)) return new android.bluetooth.BluetoothManager();
                if (name.equals(POWER_SERVICE)) return power;
                return new android.app.NotificationManager();
            }
            public Intent registerReceiver(BroadcastReceiver r,IntentFilter f) {
                receivers.put(r,f);
                if (f.actions.contains(Intent.ACTION_BATTERY_CHANGED)) r.onReceive(this,new Intent(Intent.ACTION_BATTERY_CHANGED));
                return null;
            }
            public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,int flags) { return registerReceiver(r,f); }
            public void unregisterReceiver(BroadcastReceiver r) { receivers.remove(r); }
            public void broadcast(Intent intent) {
                for (java.util.Map.Entry<BroadcastReceiver,IntentFilter> entry:new java.util.ArrayList<>(receivers.entrySet()))
                    if (entry.getValue().actions.contains(intent.getAction())) entry.getKey().onReceive(this,intent);
            }
            public void startForegroundService(Intent i) {}
            public boolean stopService(Intent i) { return true; }
        }
    """,
    "android.content.Intent": """
        public class Intent {
            public static final String ACTION_SCREEN_ON="screen-on",ACTION_SCREEN_OFF="screen-off",
                ACTION_USER_PRESENT="unlock",ACTION_BATTERY_CHANGED="battery";
            private String action;
            private final java.util.Map<String,Object> extras=new java.util.HashMap<>();
            public Intent() {} public Intent(String a) { action=a; } public Intent(Context c,Class<?> type) {}
            public Intent setAction(String a) { action=a; return this; } public String getAction() { return action; }
            public Intent putExtra(String k,String v) { extras.put(k,v); return this; }
            public Intent putExtra(String k,int v) { extras.put(k,v); return this; }
            public String getStringExtra(String k) { return (String)extras.get(k); }
            public int getIntExtra(String k,int fallback) { return extras.containsKey(k)?(Integer)extras.get(k):fallback; }
        }
    """,
    "android.content.IntentFilter": """
        public class IntentFilter {
            public final java.util.Set<String> actions=new java.util.HashSet<>();
            public IntentFilter() {} public IntentFilter(String a) { addAction(a); }
            public void addAction(String a) { actions.add(a); }
        }
    """,
    "android.content.BroadcastReceiver": "public abstract class BroadcastReceiver { public abstract void onReceive(Context c,Intent i); }",
    "android.content.pm.PackageManager": "public class PackageManager { public static final String FEATURE_BLUETOOTH_LE=\"ble\"; public boolean hasSystemFeature(String s) { return true; } }",
    "android.content.pm.ServiceInfo": "public class ServiceInfo { public static final int FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE=16; }",
    "android.app.Service": """
        public class Service extends android.content.Context {
            public static final int START_STICKY=1,START_NOT_STICKY=2;
            public boolean stopped,foreground; public static boolean rejectForeground;
            public void onCreate() {} public void onDestroy() {} public void onTaskRemoved(android.content.Intent i) {}
            public int onStartCommand(android.content.Intent i,int flags,int id) { return START_STICKY; }
            public android.os.IBinder onBind(android.content.Intent i) { return null; }
            public void stopSelf() { stopped=true; } public void stopSelf(int id) { stopSelf(); }
            public void startForeground(int id,Notification n) {
                if (rejectForeground) throw new SecurityException("foreground refused"); foreground=true;
            }
            public void startForeground(int id,Notification n,int type) { startForeground(id,n); }
        }
    """,
    "android.app.PendingIntent": """
        public class PendingIntent {
            public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;
            public static PendingIntent getActivity(android.content.Context c,int id,android.content.Intent i,int f) { return new PendingIntent(); }
        }
    """,
    "android.app.Notification": """
        public class Notification {
            public static final String CATEGORY_SERVICE="service";
            public static class Builder {
                public Builder(android.content.Context c,String channel) {}
                public Builder setSmallIcon(int i) { return this; }
                public Builder setContentTitle(String s) { return this; }
                public Builder setContentText(String s) { return this; }
                public Builder setContentIntent(PendingIntent p) { return this; }
                public Builder setCategory(String s) { return this; }
                public Builder setOngoing(boolean b) { return this; }
                public Builder setShowWhen(boolean b) { return this; }
                public Builder setOnlyAlertOnce(boolean b) { return this; }
                public Builder setLocalOnly(boolean b) { return this; }
                public Notification build() { return new Notification(); }
            }
        }
    """,
    "android.app.NotificationManager": "public class NotificationManager { public static final int IMPORTANCE_MIN=1; public void notify(int id,Notification n) {} }",
    "android.bluetooth.BluetoothManager": "public class BluetoothManager { public BluetoothAdapter getAdapter() { return BluetoothAdapter.current; } }",
    "android.bluetooth.BluetoothAdapter": """
        public class BluetoothAdapter {
            public static BluetoothAdapter current=new BluetoothAdapter();
            public static final String ACTION_STATE_CHANGED="bluetooth-state",EXTRA_STATE="state";
            public static final int ERROR=-1,STATE_ON=12,STATE_OFF=10,STATE_TURNING_OFF=13;
            public boolean enabled=true;
            public final android.bluetooth.le.BluetoothLeAdvertiser radio=new android.bluetooth.le.BluetoothLeAdvertiser();
            public boolean isEnabled() { return enabled; }
            public boolean isMultipleAdvertisementSupported() { return true; }
            public String getAddress() { return "02:00:00:00:00:00"; }
            public android.bluetooth.le.BluetoothLeAdvertiser getBluetoothLeAdvertiser() { return radio; }
        }
    """,
    "android.bluetooth.le.BluetoothLeAdvertiser": """
        public class BluetoothLeAdvertiser {
            public final java.util.List<AdvertisingSetCallback> sets=new java.util.ArrayList<>();
            public final java.util.List<AdvertiseCallback> legacy=new java.util.ArrayList<>();
            public final java.util.List<AdvertisingSetCallback> stoppedSets=new java.util.ArrayList<>();
            public final java.util.List<AdvertiseCallback> stoppedLegacy=new java.util.ArrayList<>();
            public final java.util.List<Integer> intervals=new java.util.ArrayList<>();
            public RuntimeException startError;
            public void startAdvertisingSet(AdvertisingSetParameters p,AdvertiseData d,Object s,Object pp,Object pd,AdvertisingSetCallback cb) {
                if (startError!=null) throw startError; sets.add(cb); intervals.add(p.interval);
            }
            public void stopAdvertisingSet(AdvertisingSetCallback cb) {
                stoppedSets.add(cb); cb.onAdvertisingSetStopped(new AdvertisingSet());
            }
            public void startAdvertising(AdvertiseSettings p,AdvertiseData d,AdvertiseCallback cb) { legacy.add(cb); }
            public void stopAdvertising(AdvertiseCallback cb) { stoppedLegacy.add(cb); }
            public AdvertisingSetCallback last() { return sets.get(sets.size()-1); }
            public void success() { last().onAdvertisingSetStarted(new AdvertisingSet(),-7,0); }
            public void fail() { last().onAdvertisingSetStarted(null,0,4); }
        }
    """,
    "android.bluetooth.le.AdvertisingSet": "public class AdvertisingSet {}",
    "android.bluetooth.le.AdvertisingSetParameters": """
        public class AdvertisingSetParameters {
            public static final int INTERVAL_MIN=160,INTERVAL_MAX=16777215; public int interval;
            public static class Builder {
                private final AdvertisingSetParameters value=new AdvertisingSetParameters();
                public Builder setLegacyMode(boolean b) { return this; }
                public Builder setConnectable(boolean b) { return this; }
                public Builder setScannable(boolean b) { return this; }
                public Builder setInterval(int i) { value.interval=i; return this; }
                public Builder setTxPowerLevel(int i) { return this; }
                public AdvertisingSetParameters build() { return value; }
            }
        }
    """,
    "android.bluetooth.le.AdvertiseData": """
        public class AdvertiseData {
            public static class Builder {
                public Builder setIncludeDeviceName(boolean b) { return this; }
                public Builder setIncludeTxPowerLevel(boolean b) { return this; }
                public Builder addManufacturerData(int id,byte[] bytes) { return this; }
                public AdvertiseData build() { return new AdvertiseData(); }
            }
        }
    """,
    "android.bluetooth.le.AdvertiseSettings": """
        public class AdvertiseSettings {
            public static final int ADVERTISE_MODE_LOW_LATENCY=2,ADVERTISE_MODE_BALANCED=1,ADVERTISE_MODE_LOW_POWER=0;
            public static final int ADVERTISE_TX_POWER_ULTRA_LOW=0,ADVERTISE_TX_POWER_LOW=1,ADVERTISE_TX_POWER_MEDIUM=2,ADVERTISE_TX_POWER_HIGH=3;
            public static class Builder {
                public Builder setAdvertiseMode(int i) { return this; }
                public Builder setTxPowerLevel(int i) { return this; }
                public Builder setConnectable(boolean b) { return this; }
                public Builder setTimeout(int i) { return this; }
                public AdvertiseSettings build() { return new AdvertiseSettings(); }
            }
        }
    """,
    "android.bluetooth.le.AdvertisingSetCallback": """
        public class AdvertisingSetCallback {
            public static final int ADVERTISE_SUCCESS=0,ADVERTISE_FAILED_DATA_TOO_LARGE=1,ADVERTISE_FAILED_TOO_MANY_ADVERTISERS=2,
                ADVERTISE_FAILED_ALREADY_STARTED=3,ADVERTISE_FAILED_INTERNAL_ERROR=4,ADVERTISE_FAILED_FEATURE_UNSUPPORTED=5;
            public void onAdvertisingSetStarted(AdvertisingSet set,int power,int status) {}
            public void onAdvertisingSetStopped(AdvertisingSet set) {}
            public void onAdvertisingEnabled(AdvertisingSet set,boolean enabled,int status) {}
        }
    """,
    "android.bluetooth.le.AdvertiseCallback": """
        public class AdvertiseCallback {
            public static final int ADVERTISE_FAILED_DATA_TOO_LARGE=1,ADVERTISE_FAILED_TOO_MANY_ADVERTISERS=2,
                ADVERTISE_FAILED_ALREADY_STARTED=3,ADVERTISE_FAILED_INTERNAL_ERROR=4,ADVERTISE_FAILED_FEATURE_UNSUPPORTED=5;
            public void onStartSuccess(AdvertiseSettings s) {} public void onStartFailure(int code) {}
        }
    """,
    "com.jpitsg.sysman.Config": """
        final class Config {
            static Config current=new Config(); boolean enabled=true,noRule; int interval=10,major=1;
            static final int BEACON_INTERVAL_OFF=0;
            static Config get(android.content.Context c) { return current; }
            boolean beaconEnabled() { return enabled; }
            java.util.UUID beaconUuid() { return new java.util.UUID(1,2); }
            int beaconMajor() { return major; } int beaconMinor() { return 1; }
            int beaconMeasuredPower() { return -59; } int beaconTxPowerDbm() { return -7; }
            BeaconRule beaconRuleFor(int battery) { return noRule?null:new BeaconRule(battery<33?0:interval); }
            static String beaconIntervalDisplay(int i) { return i+"s"; }
            static class BeaconRule {
                String id="rule"; int intervalSeconds;
                BeaconRule(int i) { intervalSeconds=i; }
                boolean broadcasts() { return intervalSeconds>0; }
                String displayThreshold() { return "33%"; }
                String displayInterval() { return intervalSeconds+"s"; }
            }
        }
    """,
    "com.jpitsg.sysman.PermissionState": """
        final class PermissionState {
            static boolean granted=true;
            static boolean hasBluetoothAdvertise(android.content.Context c) { return granted; }
            static boolean ignoringBatteryOptimizations(android.content.Context c) { return true; }
        }
    """,
    "com.jpitsg.sysman.BatteryReader": "final class BatteryReader { static int battery=80; static int batteryPercent(android.content.Context c) { return battery; } }",
    "com.jpitsg.sysman.BeaconStateStore": """
        final class BeaconStateStore {
            static final String ACTION_STATE_CHANGED="beacon-state",STATE_OFF="OFF",STATE_STARTING="STARTING",STATE_RETRYING="RETRYING",
                STATE_ADVERTISING="ADVERTISING",STATE_ERROR="ERROR",STATE_NO_RULE="NO_RULE",STATE_PAUSED="PAUSED",
                STATE_UNSUPPORTED="UNSUPPORTED",STATE_NO_PERMISSION="NO_PERMISSION",STATE_BLUETOOTH_OFF="BLUETOOTH_OFF";
            static String state="OFF",detail=""; static int interval;
            static void setState(android.content.Context c,String s,String d) { state=s; detail=d; interval=0; }
            static void setError(android.content.Context c,String d) { setState(c,STATE_ERROR,d); }
            static void setAdvertising(android.content.Context c,int i,boolean legacy,int power) { state=STATE_ADVERTISING; detail=""; interval=i; }
            static void setRuleContext(android.content.Context c,int b,String rule,int i) {}
            static String state(android.content.Context c) { return state; }
            static String detail(android.content.Context c) { return detail; }
            static int intervalSeconds(android.content.Context c) { return interval; }
            static String label(String s) { return s; }
        }
    """,
    "com.jpitsg.sysman.LogStore": """
        final class LogStore {
            static final java.util.List<String> logs=new java.util.ArrayList<>();
            static void append(android.content.Context c,String tag,String message) { logs.add(message); }
        }
    """,
    "com.jpitsg.sysman.ServiceNotifications": "final class ServiceNotifications { static void ensureChannel(android.content.Context c,String id,String title,String detail,int importance) {} }",
    "com.jpitsg.sysman.MainActivity": "public class MainActivity {}",
    "com.jpitsg.sysman.R": "final class R { static class drawable { static final int ic_stat_system_manager=1; } }",
}

TEST = r"""
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.os.*;

public class BeaconRecoveryTest {
    static int assertions,scenarios;
    static BeaconService service;
    static class Listener implements BeaconAdvertiser.Listener {
        int started,failed;
        public void onStarted() { started++; }
        public void onFailure(String reason) { failed++; }
    }
    static void check(boolean condition,String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    static void reset() {
        if (service!=null) service.onDestroy();
        service=null; Handler.reset(); Config.current=new Config();
        BluetoothAdapter.current=new BluetoothAdapter(); PermissionState.granted=true;
        BatteryReader.battery=80; BeaconStateStore.state="OFF"; LogStore.logs.clear();
        android.app.Service.rejectForeground=false; scenarios++;
    }
    static BluetoothLeAdvertiser radio() { return BluetoothAdapter.current.radio; }
    static BeaconAdvertiser advertiser(Listener listener) { return new BeaconAdvertiser(new Context(),listener); }
    static void startService() {
        service=new BeaconService(); service.onCreate();
        check(radio().sets.isEmpty(),"sticky battery event must wait for foreground promotion");
        check(service.onStartCommand(new Intent(BeaconManager.ACTION_SYNC),0,1)==android.app.Service.START_STICKY,"sticky service");
        check(service.foreground && radio().sets.size()==1,"foreground advertisement starts");
    }
    static void disableRadio() { radio().last().onAdvertisingEnabled(new AdvertisingSet(),false,0); }
    static void failAllStarts() {
        radio().fail(); Handler.advance(0); radio().fail(); Handler.advance(0);
        radio().legacy.get(radio().legacy.size()-1).onStartFailure(4);
    }
    static boolean logged(String value) { return LogStore.logs.stream().anyMatch(s -> s.contains(value)); }

    public static void main(String[] args) {
        reset(); Listener listener=new Listener(); BeaconAdvertiser advertiser=advertiser(listener);
        advertiser.start(30); AdvertisingSetCallback original=radio().last();
        check(BeaconStateStore.state.equals("STARTING"),"starting status clears old advertising claim");
        radio().fail(); Handler.advance(0);
        check(radio().intervals.get(1)==16384,"clamped fallback retained");
        radio().success();
        original.onAdvertisingSetStarted(new AdvertisingSet(),0,0);
        original.onAdvertisingSetStopped(new AdvertisingSet());
        original.onAdvertisingEnabled(new AdvertisingSet(),false,0);
        check(advertiser.isAdvertising() && listener.started==1 && listener.failed==0,"stale callbacks cannot alter replacement");
        check(radio().stoppedSets.contains(original),"late successful start disposed");
        Handler.advance(60000); check(listener.failed==0,"success cancels start timeout");
        radio().last().onAdvertisingEnabled(new AdvertisingSet(),true,0);
        check(advertiser.isAdvertising(),"enabled callback keeps healthy advertisement");
        disableRadio(); check(!advertiser.isAdvertising() && listener.failed==1,"disable reports failure");
        check(BeaconStateStore.state.equals("ERROR") && BeaconStateStore.interval==0,"disable clears claimed broadcasting state");

        reset(); listener=new Listener(); advertiser=advertiser(listener); advertiser.start(10);
        radio().success(); radio().last().onAdvertisingSetStopped(new AdvertisingSet());
        check(listener.failed==1 && !advertiser.isAdvertising(),"unexpected stop reports failure once despite cleanup callback");

        reset(); listener=new Listener(); advertiser=advertiser(listener); advertiser.start(10);
        original=radio().last(); radio().fail(); advertiser.stop(); Handler.advance(60000);
        original.onAdvertisingSetStarted(new AdvertisingSet(),0,0);
        check(radio().sets.size()==1 && !advertiser.isRunningAt(10),"explicit stop cancels queued fallback and timeout");
        check(listener.started==0 && listener.failed==0,"late success after explicit stop ignored");

        reset(); listener=new Listener(); advertiser=advertiser(listener); advertiser.start(10);
        original=radio().last(); Handler.advance(10000);
        check(radio().sets.size()==2 && radio().stoppedSets.contains(original),"hung requested start cleaned up and retried");
        Handler.advance(10000); check(radio().legacy.size()==1,"hung clamped start falls back to legacy");
        AdvertiseCallback legacy=radio().legacy.get(0); Handler.advance(10000);
        check(listener.failed==1 && !advertiser.isRunningAt(10),"hung legacy start becomes recoverable failure");
        legacy.onStartSuccess(new AdvertiseSettings());
        check(listener.started==0 && radio().stoppedLegacy.contains(legacy),"late legacy success disposed");

        reset(); listener=new Listener(); advertiser=advertiser(listener); advertiser.start(10);
        radio().fail(); Handler.advance(0); radio().fail(); Handler.advance(0);
        legacy=radio().legacy.get(0); legacy.onStartSuccess(new AdvertiseSettings()); Handler.advance(30000);
        check(advertiser.isAdvertising() && advertiser.isLegacyFallbackInUse(),"legacy success cancels its timeout");
        advertiser.stop(); legacy.onStartFailure(4);
        check(listener.failed==0,"stale legacy failure ignored");

        reset(); listener=new Listener(); advertiser=advertiser(listener);
        radio().startError=new SecurityException("revoked"); advertiser.start(10); Handler.advance(60000);
        check(listener.failed==1 && !advertiser.isAdvertising(),"synchronous permission failure ends attempt");

        reset(); startService(); radio().success();
        for (long delay:new long[]{1000,2000,4000,8000,16000,32000,60000,60000}) {
            int starts=radio().sets.size(); disableRadio();
            check(BeaconStateStore.state.equals("RETRYING"),"interruption displays retrying");
            service.broadcast(new Intent(Intent.ACTION_SCREEN_OFF));
            service.broadcast(new Intent(Intent.ACTION_BATTERY_CHANGED));
            Handler.advance(delay-1); check(radio().sets.size()==starts,"events cannot bypass backoff");
            Handler.advance(1); check(radio().sets.size()==starts+1,"retry runs at capped exponential delay");
            radio().success();
        }
        Handler.advance(61000); disableRadio(); int starts=radio().sets.size();
        Handler.advance(1000); check(radio().sets.size()==starts+1,"stable advertising resets recovery backoff");
        radio().success();
        for (String action:new String[]{Intent.ACTION_SCREEN_OFF,Intent.ACTION_SCREEN_ON,Intent.ACTION_USER_PRESENT,
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED,PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED}) {
            service.broadcast(new Intent(action));
            check(logged("device-event:"+action),"power transition is logged");
        }
        check(radio().sets.size()==starts+1,"healthy advertiser not restarted by screen or idle events");
        check(logged("batteryExempt=true") && logged("pid="),"diagnostics record exemption and process");

        reset(); startService(); failAllStarts(); starts=radio().sets.size(); Handler.advance(1000);
        check(radio().sets.size()==starts+1,"exhausted API fallbacks recover without external trigger");

        for (String blocker:new String[]{"disabled","battery","no-rule","permission","bluetooth"}) {
            reset(); startService(); radio().success(); disableRadio(); starts=radio().sets.size();
            String expected;
            switch(blocker) {
                case "disabled": Config.current.enabled=false; expected="OFF"; break;
                case "battery": BatteryReader.battery=20; expected="PAUSED"; break;
                case "no-rule": Config.current.noRule=true; expected="NO_RULE"; break;
                case "permission": PermissionState.granted=false; expected="NO_PERMISSION"; break;
                default: BluetoothAdapter.current.enabled=false; expected="BLUETOOTH_OFF";
            }
            Handler.advance(60000);
            check(radio().sets.size()==starts,"retry respects "+blocker);
            if (blocker.equals("disabled")) {
                check(service.stopped,"disabled feature stops service"); service.onDestroy(); service=null;
            }
            check(BeaconStateStore.state.equals(expected),"blocking state displayed: "+blocker);
        }

        reset(); startService(); radio().success(); disableRadio(); Config.current.interval=30;
        Handler.advance(1000);
        check(radio().intervals.get(1)==48000,"retry uses latest interval");
        radio().success(); disableRadio(); Config.current.major=99;
        service.onStartCommand(new Intent(BeaconManager.ACTION_REFRESH),0,2);
        check(radio().sets.size()==3,"settings refresh replaces pending retry immediately");
        radio().success(); Handler.advance(60000);
        check(radio().sets.size()==3,"old recovery runnable cancelled after settings change");

        reset(); startService(); radio().success(); disableRadio(); original=radio().last();
        service.onDestroy(); service=null; Handler.advance(120000);
        original.onAdvertisingSetStarted(new AdvertisingSet(),0,0);
        check(radio().sets.size()==1 && !BeaconService.isActive(),"destroy cancels recovery and invalidates callbacks");
        check(BeaconStateStore.state.equals("ERROR"),"late callback cannot revive state after destruction");

        reset(); BeaconStateStore.state="ADVERTISING"; service=new BeaconService(); service.onCreate();
        check(BeaconStateStore.state.equals("STARTING"),"service recreation clears stale persisted state");
        check(logged("previousState=ADVERTISING"),"service creation logs prior state");
        service.onStartCommand(null,0,1); check(logged("systemRestart=true"),"sticky restart recorded");
        service.onTaskRemoved(new Intent()); check(logged("task-removed"),"task removal recorded");

        reset(); android.app.Service.rejectForeground=true; service=new BeaconService(); service.onCreate();
        check(service.onStartCommand(new Intent(),0,1)==android.app.Service.START_NOT_STICKY,"foreground refusal stops startup");
        Handler.advance(60000); check(radio().sets.isEmpty(),"no advertising before foreground promotion");
        service.onDestroy(); service=null;
        check(BeaconStateStore.detail.contains("Foreground service refused"),"foreground failure detail survives destruction");
        System.out.println("PASS: "+scenarios+" scenarios, "+assertions+" assertions");
    }
}
"""


def main():
    with tempfile.TemporaryDirectory(prefix="beacon-recovery-") as directory:
        work = Path(directory)
        src = work / "src"
        for name, body in {**SOURCES, "com.jpitsg.sysman.BeaconRecoveryTest": TEST}.items():
            path = src / (name.replace(".", "/") + ".java")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("package " + name.rsplit(".", 1)[0] + ";\n" + textwrap.dedent(body))
        for name in ("BeaconAdvertiser", "BeaconService", "BeaconManager"):
            path = Path("com/jpitsg/sysman") / (name + ".java")
            (src / path).write_text((ROOT / "app/src/main/java" / path).read_text())
        subprocess.run(["javac", "--release", "8", "-d", str(work / "classes"),
                        *map(str, sorted(src.rglob("*.java")))], check=True)
        subprocess.run(["java", "-cp", str(work / "classes"),
                        "com.jpitsg.sysman.BeaconRecoveryTest"], check=True)


if __name__ == "__main__":
    main()

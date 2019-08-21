package com.mobiperf;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;

import com.mobiperf.util.model.Package;
import com.mobiperf.util.utils.NetworkStatsHelper;
import com.mobiperf.util.utils.PackageManagerHelper;
import com.mobiperf.util.utils.TrafficStatsHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NetworkSummaryCollector implements Runnable {
    private static final int READ_PHONE_STATE_REQUEST = 1;
    Context context ;

    NetworkSummaryCollector(){
        context  = SpeedometerApp.getCurrentApp().getApplicationContext();
    }

    @Override
    public void run() {
        List<Package> packageList=getPackagesData();
        JSONArray userSummary = new JSONArray();
        try {
            for (Package pckg : packageList) {
                String packageName = pckg.getPackageName();
                long[] bytes = getBytes(packageName);
                //build json here
                JSONObject appData = new JSONObject();
                appData.put("name", packageName);
                appData.put("Rx", bytes[0]);
                appData.put("Tx", bytes[1]);
                userSummary.put(appData);
            }
            JSONObject blob = new JSONObject();
            blob.put("user_name", SpeedometerApp.getCurrentApp().getSelectedAccount());
            blob.put("Date",System.currentTimeMillis());
            blob.put("user_summary",userSummary);
            System.out.println(blob);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private List<Package> getPackagesData() {
        PackageManager packageManager = SpeedometerApp.getCurrentApp().getPackageManager();//needed from activity
        List<PackageInfo> packageInfoList = packageManager.getInstalledPackages(PackageManager.GET_META_DATA);
        Collections.sort(packageInfoList, new Comparator<PackageInfo>() {
            @Override
            public int compare(PackageInfo o1, PackageInfo o2) {
                return (int) ((o2.lastUpdateTime - o1.lastUpdateTime) / 10);
            }
        });
        List<Package> packageList = new ArrayList<>(packageInfoList.size());
        for (PackageInfo packageInfo : packageInfoList) {
            if (packageManager.checkPermission(Manifest.permission.INTERNET,
                    packageInfo.packageName) == PackageManager.PERMISSION_DENIED) {
                continue;
            }
            Package packageItem = new Package();
            packageItem.setVersion(packageInfo.versionName);
            packageItem.setPackageName(packageInfo.packageName);
            packageList.add(packageItem);
            ApplicationInfo ai = null;
            try {
                ai = packageManager.getApplicationInfo(packageInfo.packageName, PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            if (ai == null) {
                continue;
            }
            CharSequence appName = packageManager.getApplicationLabel(ai);
            if (appName != null) {
                packageItem.setName(appName.toString());
            }
        }
        return packageList;
    }

    private long[] getBytes(String packageName) {
        int uid = PackageManagerHelper.getPackageUid(context, packageName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkStatsManager networkStatsManager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
            NetworkStatsHelper networkStatsHelper = new NetworkStatsHelper(networkStatsManager, uid);
            return fillNetworkStatsPackage(uid, networkStatsHelper);
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void requestReadNetworkHistoryAccess() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    } @TargetApi(Build.VERSION_CODES.M)
    private void fillNetworkStatsAll(NetworkStatsHelper networkStatsHelper) {
        long mobileWifiRx = networkStatsHelper.getAllRxBytesMobile(context) + networkStatsHelper.getAllRxBytesWifi();
        /// do domething ith the number ogf bytes jso download
        long mobileWifiTx = networkStatsHelper.getAllTxBytesMobile(context) + networkStatsHelper.getAllTxBytesWifi();
        //do domething ith the number ogf bytes json upload
    }

    @TargetApi(Build.VERSION_CODES.M)
    private long[] fillNetworkStatsPackage(int uid, NetworkStatsHelper networkStatsHelper) {
        long mobileWifiRx = networkStatsHelper.getPackageRxBytesMobile(context) + networkStatsHelper.getPackageRxBytesWifi();
        long mobileWifiTx = networkStatsHelper.getPackageTxBytesMobile(context) + networkStatsHelper.getPackageTxBytesWifi();
        return new long[]{mobileWifiRx,mobileWifiTx};
    }

    private boolean hasPermissionToReadPhoneStats() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_DENIED) {
            return false;
        } else {
            return true;
        }
    }

    private void requestPhoneStateStats() {
        ActivityCompat.requestPermissions(SpeedometerApp.getCurrentApp(), new String[]{Manifest.permission.READ_PHONE_STATE}, READ_PHONE_STATE_REQUEST);
    }

    private boolean hasPermissionToReadNetworkHistory() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        final AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.getPackageName());
        if (mode == AppOpsManager.MODE_ALLOWED) {
            return true;
        }
        appOps.startWatchingMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
                context.getPackageName(),
                new AppOpsManager.OnOpChangedListener() {
                    @Override
                    @TargetApi(Build.VERSION_CODES.M)
                    public void onOpChanged(String op, String packageName) {
                        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                                android.os.Process.myUid(), context.getPackageName());
                        if (mode != AppOpsManager.MODE_ALLOWED) {
                            return;
                        }
                        appOps.stopWatchingMode(this);
                    }
                });
        requestReadNetworkHistoryAccess();
        return false;
    }
}

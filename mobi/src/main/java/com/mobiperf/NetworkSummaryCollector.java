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

import com.mobiperf.util.Util;
import com.mobiperf.util.model.Package;
import com.mobiperf.util.utils.NetworkStatsHelper;
import com.mobiperf.util.utils.PackageManagerHelper;
import com.mobiperf.util.utils.TrafficStatsHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class NetworkSummaryCollector implements Runnable {
    public static final int READ_PHONE_STATE_REQUEST = 1;
    Context context ;

    NetworkSummaryCollector(){
        context  = SpeedometerApp.getCurrentApp().getApplicationContext();
    }

    @Override
    public void run() {
        Logger.d("Collector Thread has started");
        long endTime = System.currentTimeMillis();
        long startTime = endTime-(24*3600*1000); //minus 24 hrs
        List<Package> packageList=getPackagesData();
        JSONArray userSummary = new JSONArray();
        try {
            for (Package pckg : packageList) {
                String packageName = pckg.getPackageName();
                DataPayload dataPayload = getBytes(packageName,startTime,endTime);
                //build json here
                if(!dataPayload.isEmptyPayload()) {
                    JSONObject appData = new JSONObject();
                    appData.put("name", packageName);
                    appData.put("Rx",dataPayload.getRx());
                    appData.put("Tx",dataPayload.getTx());
                    userSummary.put(appData);
                }
            }
            JSONObject blob = new JSONObject();
            blob.put("request_type","summary");
            blob.put("user_name", SpeedometerApp.getCurrentApp().getSelectedAccount());
            blob.put("Date",endTime);
            blob.put("user_summary",userSummary);
            Logger.d(blob.toString());
            Util.sendResult(blob.toString(),"Network Summary");
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private List<Package> getPackagesData() {
        PackageManager packageManager = SpeedometerApp.getCurrentApp().getPackageManager();
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

    private DataPayload getBytes(String packageName,long start,long end) {
        int uid = PackageManagerHelper.getPackageUid(context, packageName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkStatsManager networkStatsManager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
            NetworkStatsHelper networkStatsHelper = new NetworkStatsHelper(networkStatsManager, uid);
            return fillNetworkStatsPackage(networkStatsHelper,start,end);
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private DataPayload fillNetworkStatsPackage(NetworkStatsHelper networkStatsHelper,long start,long end) {
        long mobileWifiRx = networkStatsHelper.getPackageRxBytesWifi(start,end);
        long mobileWifiTx = networkStatsHelper.getPackageTxBytesWifi(start,end);
        return new DataPayload(mobileWifiRx,mobileWifiTx);
    }

}

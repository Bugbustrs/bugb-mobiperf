/* Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mobiperf;

import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.design.widget.TabLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.TextView;

import com.mobiperf.MeasurementScheduler.SchedulerBinder;

import java.security.Security;

/**
 * The main UI thread that manages different tabs
 */
public class SpeedometerApp extends AppCompatActivity implements TabLayout.OnTabSelectedListener {

    public static final String TAG = "MobiPerf";
    private static final int REQUEST_ACCOUNTS = 23;

    private boolean userConsented = false;
    private String selectedAccount = null;

    private MeasurementScheduler scheduler;
    private TabHost tabHost;
    private boolean isBound = false;
    private boolean isBindingToService = false;
    private BroadcastReceiver receiver;
    TextView statusBar, statsBar;

    private static SpeedometerApp speedometerApp;
    //This is our tablayout
    private TabLayout tabLayout;

    //This is our viewPager
    private ViewPager viewPager;


    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.d("onServiceConnected called");
            // We've bound to LocalService, cast the IBinder and get LocalService
            // instance
            SchedulerBinder binder = (SchedulerBinder) service;
            scheduler = binder.getService();
            isBound = true;
            isBindingToService = false;
            initializeStatusBar();
            SpeedometerApp.this.sendBroadcast(new UpdateIntent("",
                    UpdateIntent.SCHEDULER_CONNECTED_ACTION));
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Logger.d("onServiceDisconnected called");
            isBound = false;
        }
    };

    /**
     * Returns the scheduler singleton instance. Should only be called from the UI thread.
     */
    public MeasurementScheduler getScheduler() {
        if (isBound) {
            return this.scheduler;
        } else {
            bindToService();
            return null;
        }
    }

    /**
     * Returns the tab host. Allows child tabs to request focus changes, etc...
     */
    public TabHost getSpeedomterTabHost() {
        return tabHost;
    }

    private void setPauseIconBasedOnSchedulerState(MenuItem item) {
        if (this.scheduler != null && item != null) {
            if (this.scheduler.isPauseRequested()) {
                item.setIcon(android.R.drawable.ic_media_play);
                item.setTitle(R.string.menuResume);
            } else {
                item.setIcon(android.R.drawable.ic_media_pause);
                item.setTitle(R.string.menumPause);
            }
        }
    }

    /**
     * Populate the application menu. Only called once per onCreate()
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * Adjust menu items depending on system state. Called every time the
     * menu pops up
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        setPauseIconBasedOnSchedulerState(menu.findItem(R.id.menuPauseResume));
        return true;
    }

    /**
     * React to menu item selections
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menuPauseResume:
                if (this.scheduler != null) {
                    if (this.scheduler.isPauseRequested()) {
                        this.scheduler.resume();
                    } else {
                        this.scheduler.pause();
                    }
                }
                return true;
            case R.id.menuQuit: {
                Logger.i("User requests exit. Quitting the app");
                quitApp();
                return true;
            }
            case R.id.menuSettings: {
                Intent settingsActivity = new Intent(getBaseContext(), MobiPerfSettings.class);
                startActivity(settingsActivity);
                return true;
            }
            case R.id.aboutPage: {
                Intent intent = new Intent(getBaseContext(), com.mobiperf.About.class);
                startActivity(intent);
                return true;
            }
            case R.id.menuLog: {
                Intent intent = new Intent(getBaseContext(), SystemConsoleActivity.class);
                startActivity(intent);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.d("onCreate called");
        super.onCreate(savedInstanceState);
        speedometerApp = this;
        setContentView(R.layout.main);
        restoreDefaultAccount();
        if (selectedAccount == null) {
            setUser();
        } else {
            // double check the user consent selection
            consentDialogWrapper();
        }

        /* Set the DNS cache TTL to 0 such that measurements can be more accurate.
         * However, it is known that the current Android OS does not take actions
         * on these properties but may enforce them in future versions.
         */
        System.setProperty("networkaddress.cache.ttl", "0");
        System.setProperty("networkaddress.cache.negative.ttl", "0");
        Security.setProperty("networkaddress.cache.ttl", "0");
        Security.setProperty("networkaddress.cache.negative.ttl", "0");

        Resources res = getResources(); // Resource object to get Drawables

        statusBar = findViewById(R.id.systemStatusBar);
        statsBar = findViewById(R.id.systemStatsBar);

        //Adding toolbar to the activity
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Initializing the tablayout
        tabLayout = (TabLayout) findViewById(R.id.tabLayout);

        //Adding the tabs using addTab() method
        tabLayout.addTab(tabLayout.newTab().setText(MeasurementCreationFragment.TAB_TAG).setIcon(R.drawable.ic_tab_user_measurement));
        tabLayout.addTab(tabLayout.newTab().setText(ResultsConsoleFragment.TAB_TAG).setIcon(R.drawable.ic_tab_results_icon));
        tabLayout.addTab(tabLayout.newTab().setText(MeasurementScheduleConsoleFragment.TAB_TAG).setIcon(R.drawable.ic_tab_schedules));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        //Initializing viewPager
        viewPager = (ViewPager) findViewById(R.id.pager);

        //Creating our pager adapter
        Pager adapter = new Pager(getSupportFragmentManager(), tabLayout.getTabCount());

        //Adding adapter to pager
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i1) {

            }

            @Override
            public void onPageSelected(int i) {
                TabLayout.Tab tab = tabLayout.getTabAt(i);
                if (tab != null) {
                    tab.select();
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });
        //Adding onTabSelectedListener to swipe views
        tabLayout.addOnTabSelectedListener(this);


        // We only need one instance of the scheduler thread
        Intent intent = new Intent(this, MeasurementScheduler.class);
        this.startService(intent);

        this.receiver = new BroadcastReceiver() {
            @Override
            // All onXyz() callbacks are single threaded
            public void onReceive(Context context, Intent intent) {
                // Update the status bar on SYSTEM_STATUS_UPDATE_ACTION intents
                String statusMsg = intent.getStringExtra(UpdateIntent.STATUS_MSG_PAYLOAD);
                if (statusMsg != null) {
                    Log.d("Broadcast information", statusMsg);
                    updateStatusBar(statusMsg);
                } else if (scheduler != null) {
                    initializeStatusBar();
                }

                String statsMsg = intent.getStringExtra(UpdateIntent.STATS_MSG_PAYLOAD);
                if (statsMsg != null) {
                    updateStatsBar(statsMsg);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
        this.registerReceiver(this.receiver, filter);
    }

    private void initializeStatusBar() {
        if (this.scheduler.isPauseRequested()) {
            updateStatusBar(SpeedometerApp.this.getString(R.string.pauseMessage));
        } else if (!scheduler.hasBatteryToScheduleExperiment()) {
            updateStatusBar(SpeedometerApp.this.getString(R.string.powerThreasholdReachedMsg));
        } else {
            MeasurementTask currentTask = scheduler.getCurrentTask();
            if (currentTask != null) {
                if (currentTask.getDescription().priority == MeasurementTask.USER_PRIORITY) {
                    updateStatusBar("User task " + currentTask.getDescriptor() + " is running");
                } else {
                    updateStatusBar("System task " + currentTask.getDescriptor() + " is running");
                }
            } else {
                updateStatusBar(SpeedometerApp.this.getString(R.string.resumeMessage));
            }
        }
    }

    private void updateStatusBar(String statusMsg) {
        if (statusMsg != null) {
            statusBar.setText(statusMsg);
        }
    }

    private void updateStatsBar(String statsMsg) {
        if (statsMsg != null) {
            statsBar.setText(statsMsg);
        }
    }

    private void bindToService() {
        if (!isBindingToService && !isBound) {
            // Bind to the scheduler service if it is not bounded
            Intent intent = new Intent(this, MeasurementScheduler.class);
            bindService(intent, serviceConn, Context.BIND_AUTO_CREATE);
            isBindingToService = true;
        }
    }

    @Override
    protected void onStart() {
        Logger.d("onStart called");
        // Bind to the scheduler service for only once during the lifetime of the activity
        bindToService();
        super.onStart();
    }

    @Override
    protected void onStop() {
        Logger.d("onStop called");
        super.onStop();
        if (isBound) {
            unbindService(serviceConn);
            isBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        Logger.d("onDestroy called");
        super.onDestroy();
        this.unregisterReceiver(this.receiver);
    }

    private void quitApp() {
        Logger.d("quitApp called");
        if (isBound) {
            unbindService(serviceConn);
            isBound = false;
        }
        if (this.scheduler != null) {
            Logger.d("requesting Scheduler stop");
            scheduler.requestStop();
        }
        // Force consent on next restart.
        userConsented = false;
        saveConsentState();
        // Disable auto start on boot.
        setStartOnBoot(false);

        this.finish();
        System.exit(0);
    }

    private void doCheckin() {
        if (scheduler != null) {
            scheduler.handleCheckin(true);
        }
    }

    /**
     * Set preference to indicate whether start on boot is enabled.
     */
    private void setStartOnBoot(boolean startOnBoot) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(getString(R.string.startOnBootPrefKey), startOnBoot);
        editor.commit();
    }

    private void recordUserConsent() {
        userConsented = true;
        saveConsentState();
    }

    /**
     * Save consent state persistent storage.
     */
    private void saveConsentState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Config.PREF_KEY_CONSENTED, userConsented);
        editor.apply();
    }

    /**
     * Restore the last used account
     */
    private void restoreDefaultAccount() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        selectedAccount = prefs.getString(Config.PREF_KEY_SELECTED_ACCOUNT, null);
    }

    /**
     * Restore measurement statistics from persistent storage.
     */
    private void restoreConsentState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext());
        userConsented = prefs.getBoolean(Config.PREF_KEY_CONSENTED, false);
    }

    /**
     * A wrapper function to check user consent selection,
     * and generate one if user haven't agreed on.
     */
    private void consentDialogWrapper() {
        restoreConsentState();
        if (!userConsented) {
            // Show the consent dialog. After user select the content
            showDialog();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ACCOUNTS) {
            // Receiving a result from the AccountPicker
            if (resultCode == RESULT_OK) {
                selectedAccount = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            } else {
                selectedAccount = "Anonymous";
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(Config.PREF_KEY_SELECTED_ACCOUNT, selectedAccount);
            editor.apply();
        }
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        tab.setIcon(getSelectedTabIcon(tab));
        viewPager.setCurrentItem(tab.getPosition());
    }

    private int getSelectedTabIcon(TabLayout.Tab tab) {
        switch ((tab.getPosition())) {
            case 0:
                return R.drawable.ic_tab_user_measurement_selected;
            case 1:
                return R.drawable.ic_tab_results_icon_selected;
            case 2:
                return R.drawable.ic_tab_schedules_selected;
        }
        return -1;
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        tab.setIcon(getUnselectedIcon(tab));
    }

    private int getUnselectedIcon(TabLayout.Tab tab) {
        switch ((tab.getPosition())) {
            case 0:
                return R.drawable.ic_tab_user_measurement_unselected;
            case 1:
                return R.drawable.ic_tab_results_icon_unselected;
            case 2:
                return R.drawable.ic_tab_schedules_unselected;
        }
        return -1;
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        tab.setIcon(getSelectedTabIcon(tab));
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    public static SpeedometerApp getCurrentApp() {
        return speedometerApp;
    }

    void showDialog() {
        DialogFragment newFragment = ConsentAlertDialog.newInstance();
        newFragment.show(getSupportFragmentManager(), "dialog");
    }

    public void doPositiveClick() {
        Log.i("FragmentAlertDialog", "Positive click!");
        recordUserConsent();
        // Enable auto start on boot.
        setStartOnBoot(true);
        // Force a checkin now since the one initiated by the scheduler was likely skipped.
        doCheckin();
    }

    public void doNegativeClick() {
        Log.i("FragmentAlertDialog", "Negative click!");
        quitApp();
    }

    String getSelectedAccount() {
        return selectedAccount;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setUser() {
        Intent intent = AccountManager.newChooseAccountIntent(null, null, new String[]{"com.google", "com.google.android.legacyimap"}, null, null, null, null);
        startActivityForResult(intent, REQUEST_ACCOUNTS);
        consentDialogWrapper();
    }
}

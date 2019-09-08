package com.mobiperf;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CheckInExecutor {

    private final static int INIT_DELAY=1;
    private final static int PERIOD = 2;
    private final static int THREAD_POOL_SIZE = 1;
    private final static TimeUnit TimeUnits = TimeUnit.MINUTES;
    private final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);
    private static boolean isStarted=false;

    public static void startCollector(){
        if(!isStarted) {
            MeasurementScheduler.CheckinTask checkinTask=SpeedometerApp.getCurrentApp().getScheduler().new CheckinTask();
            ScheduledFuture temp = scheduler.scheduleAtFixedRate(checkinTask, INIT_DELAY, PERIOD, TimeUnits);
            System.out.println("CheckIn Exec is a GO!");
            isStarted=true;
        }
    }
}

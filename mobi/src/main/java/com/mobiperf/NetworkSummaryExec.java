package com.mobiperf;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NetworkSummaryExec {
    private final static int INIT_DELAY=1;
    private final static int PERIOD = 24*60;
    private final static int THREAD_POOL_SIZE = 1;
    private final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);
    private static boolean isStarted=false;
    public static void startCollector(){
        if(!isStarted) {
            scheduler.scheduleAtFixedRate(new NetworkSummaryCollector(), INIT_DELAY, PERIOD, TimeUnit.MINUTES);
            System.out.println("Collector Has Started");
            isStarted=true;
        }
    }
}

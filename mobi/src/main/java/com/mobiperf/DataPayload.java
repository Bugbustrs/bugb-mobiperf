package com.mobiperf;

public class DataPayload {
    private long Rx;
    private long Tx;

    public DataPayload(long rx, long tx) {
        Rx = rx;
        Tx = tx;
    }

    public boolean isEmptyPayload(){
        return (Rx == 0 && Tx == 0);
    }

    public long getRx() {
        return Rx;
    }

    public void setRx(long rx) {
        Rx = rx;
    }

    public long getTx() {
        return Tx;
    }

    public void setTx(long tx) {
        Tx = tx;
    }
}

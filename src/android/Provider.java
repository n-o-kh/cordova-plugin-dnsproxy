package com.roqos.cordova.plugin;

import android.os.ParcelFileDescriptor;


public abstract class Provider {
    ParcelFileDescriptor descriptor;
    RoqosVPNService service;
    boolean running = false;
    static long dnsQueryTimes = 0;

    Provider(ParcelFileDescriptor descriptor, RoqosVPNService service) {
        this.descriptor = descriptor;
        this.service = service;
        dnsQueryTimes = 0;
    }

    public final long getDnsQueryTimes() {
        return dnsQueryTimes;
    }

    public abstract void process();

    public final void start() {
        running = true;
    }

    public final void shutdown() {
        running = false;
    }

    public abstract void stop();
}

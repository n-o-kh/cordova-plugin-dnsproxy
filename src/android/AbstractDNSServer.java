package com.roqos.cordova.plugin;

public class AbstractDNSServer {
    public static final int DNS_SERVER_DEFAULT_PORT = 53;

    protected String address;
    protected int port;

    public AbstractDNSServer(String address, int port) {
        this.address = address;
        this.port = port;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return "";
    }

    @Override
    public String toString() {
        return getName();
    }
}

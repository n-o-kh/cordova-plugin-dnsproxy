package com.roqos.cordova.plugin;

public class CustomDNSServer extends AbstractDNSServer{
    private String name;
    private String id;

    public CustomDNSServer(String name, String address, int port) {
        super(address, port);
        this.name = name;
        this.id = String.valueOf(Roqos.configurations.getNextDnsId());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

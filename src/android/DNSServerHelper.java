package com.roqos.cordova.plugin;

import android.content.Context;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

public class DNSServerHelper {
    private static HashMap<String, Integer> portCache = null;

    public static void clearPortCache() {
        portCache = null;
    }

    public static void buildPortCache() {
        portCache = new HashMap<String, Integer>();
        for (DNSServer server : Roqos.DNS_SERVERS) {
            portCache.put(server.getAddress(), server.getPort());
        }

        // for (CustomDNSServer server : Roqos.configurations.getCustomDNSServers()) {
        //     portCache.put(server.getAddress(), server.getPort());
        // }

    }

    public static int getPortOrDefault(InetAddress address, int defaultPort) {
        String hostAddress = address.getHostAddress();

        if (portCache.containsKey(hostAddress)) {
            return portCache.get(hostAddress);
        }

        return defaultPort;
    }

    public static int getPosition(String id) {
        int intId = Integer.parseInt(id);
        if (intId < Roqos.DNS_SERVERS.size()) {
            return intId;
        }

        // for (int i = 0; i < Roqos.configurations.getCustomDNSServers().size(); i++) {
        //     if (Roqos.configurations.getCustomDNSServers().get(i).getId().equals(id)) {
        //         return i + Roqos.DNS_SERVERS.size();
        //     }
        // }
        return 0;
    }

    public static String getPrimary() {
        return String.valueOf(DNSServerHelper.checkServerId(0));
    }

    public static String getSecondary() {
        return String.valueOf(DNSServerHelper.checkServerId(1));
    }

    private static int checkServerId(int id) {
        if (id < Roqos.DNS_SERVERS.size()) {
            return id;
        }
        // for (CustomDNSServer server : Roqos.configurations.getCustomDNSServers()) {
        //     if (server.getId().equals(String.valueOf(id))) {
        //         return id;
        //     }
        // }
        return 0;
    }

    public static String getAddressById(String id) {
        for (DNSServer server : Roqos.DNS_SERVERS) {
            if (server.getId().equals(id)) {
                return server.getAddress();
            }
        }
        // for (CustomDNSServer customDNSServer : Roqos.configurations.getCustomDNSServers()) {
        //     if (customDNSServer.getId().equals(id)) {
        //         return customDNSServer.getAddress();
        //     }
        // }
        return Roqos.DNS_SERVERS.get(Integer.parseInt(id)).getAddress();
    }

    public static String[] getIds() {
        ArrayList<String> servers = new ArrayList<String>(Roqos.DNS_SERVERS.size());
        for (DNSServer server : Roqos.DNS_SERVERS) {
            servers.add(server.getId());
        }
        // for (CustomDNSServer customDNSServer : Roqos.configurations.getCustomDNSServers()) {
        //     servers.add(customDNSServer.getId());
        // }
        String[] stringServers = new String[Roqos.DNS_SERVERS.size()];
        return servers.toArray(stringServers);
    }

    public static String[] getNames(Context context) {
        ArrayList<String> servers = new ArrayList<String>(Roqos.DNS_SERVERS.size());
        for (DNSServer server : Roqos.DNS_SERVERS) {
            servers.add(server.getStringDescription(context));
        }
        // for (CustomDNSServer customDNSServer : Roqos.configurations.getCustomDNSServers()) {
        //     servers.add(customDNSServer.getName());
        // }
        String[] stringServers = new String[Roqos.DNS_SERVERS.size()];
        return servers.toArray(stringServers);
    }

    public static ArrayList<AbstractDNSServer> getAllServers() {
        ArrayList<AbstractDNSServer> servers = new ArrayList<AbstractDNSServer>(Roqos.DNS_SERVERS.size());
        servers.addAll(Roqos.DNS_SERVERS);
        // servers.addAll(Roqos.configurations.getCustomDNSServers());
        return servers;
    }

    public static String getDescription(String id, Context context) {
        for (DNSServer server : Roqos.DNS_SERVERS) {
            if (server.getId().equals(id)) {
                return server.getStringDescription(context);
            }
        }
        // for (CustomDNSServer customDNSServer : Roqos.configurations.getCustomDNSServers()) {
        //     if (customDNSServer.getId().equals(id)) {
        //         return customDNSServer.getName();
        //     }
        // }
        return Roqos.DNS_SERVERS.get(0).getStringDescription(context);
    }

    public static boolean isInUsing(CustomDNSServer server) {
        return RoqosVPNService.isActivated() && (server.getId().equals(getPrimary()) || server.getId().equals(getSecondary()));
    }
}

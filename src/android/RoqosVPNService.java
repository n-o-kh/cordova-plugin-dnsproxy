package com.roqos.cordova.plugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import android.app.NotificationChannel;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.v4.app.NotificationCompat;
import android.system.OsConstants;
import android.util.Log;
import android.content.res.Resources;
import com.roqos.cordova.plugin.Roqos;
import com.roqos.cordova.plugin.StatusBarBroadcastReceiver;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

import android.widget.Toast;

public class RoqosVPNService extends VpnService implements Runnable {

    public static final String SERVICE_META_DATA_SUPPORTS_ALWAYS_ON = "ALWAYS_ON";
    public static final String ACTION_ACTIVATE = "com.roqos.RoqosVpnService.ACTION_ACTIVATE";
    public static final String ACTION_DEACTIVATE = "com.roqos.RoqosVpnService.ACTION_DEACTIVATE";

    private Thread mThread = null;
    public HashMap<String, String> dnsServers;

    private static final int NOTIFICATION_ACTIVATED = 0;

    private static final String CHANNEL_ID = "guardian_channel_1";
    private static final String CHANNEL_NAME = "guardian_channel";

    private static boolean activated = false;

    private NotificationCompat.Builder notification = null;

    public static String primaryServer;
    public static String secondaryServer;

    private ParcelFileDescriptor descriptor;
    private ParcelFileDescriptor localTunnel;

    private Provider provider;

    @Override
    public void onCreate() {
        // Log.d("RoqosVPNService", " onCreate");
        super.onCreate();
    }

    public static boolean isActivated() {
        return activated;
    }

    @TargetApi(Build.VERSION_CODES.O)

    @Override
    public void run() {
        try {
            // Log.d("Roqos    VPNService", " run");
            VpnService.Builder builder = new VpnService.Builder()
                    .setSession(Roqos.VPNSession);

            String format = null;
            for (String prefix : new String[]{"10.0.0", "192.168.50"}) {
                try {
                    // builder.addAddress(prefix + ".1", 24);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                format = prefix + ".%d";
                break;
            }

            InetAddress ipv6 = Inet6Address.getByName("fd00:0000:0000:0000:0000:0000:0000:0001");
            byte[] ipv6Template = ipv6.getAddress();

            if (primaryServer.contains(":") || secondaryServer.contains(":")) {//IPv6
                try {
                    // Log.d("RoqosVPNService", "isIPv6");
                    InetAddress addr = Inet6Address.getByAddress(ipv6Template);
                    // Log.d("RoqosVPNService", "configure: Adding IPv6 address" + addr);
                    // builder.addAddress(addr, 120);
                } catch (Exception e) {
                    e.printStackTrace();
                    ipv6Template = null;
                }
            } else {
                ipv6Template = null;
            }

            try {

                InetAddress aliasPrimary;
                InetAddress aliasSecondary;
                dnsServers = new HashMap<String, String>();
                aliasPrimary = addDnsServer(builder, format, ipv6Template, InetAddress.getByName(primaryServer));
                aliasSecondary = addDnsServer(builder, format, ipv6Template, InetAddress.getByName(secondaryServer));

                InetAddress primaryDNSServer = aliasPrimary;
                InetAddress secondaryDNSServer = aliasSecondary;
                // builder.addDnsServer(primaryDNSServer).addDnsServer(secondaryDNSServer);

                // builder.setBlocking(false);
                // builder.allowFamily(OsConstants.AF_INET);
                // builder.allowFamily(OsConstants.AF_INET6);

                // Log.d("RoqosVPNService", "Roqos VPN service is listening on " + primaryServer + " as " + primaryDNSServer.getHostAddress());
                // Log.d("RoqosVPNService", "Roqos VPN service is listening on " + secondaryServer + " as " + secondaryDNSServer.getHostAddress());

                if (Build.VERSION.SDK_INT >= 21)
			             builder.addDisallowedApplication("app.no.dns");

                // Android 7/8 has an issue with VPN in combination with some google apps - bypass the filter
             		if (Build.VERSION.SDK_INT >= 24 && Build.VERSION.SDK_INT <= 27) { // Android 7/8
                  builder.addDisallowedApplication("com.android.vending");
                  builder.addDisallowedApplication("com.google.android.apps.docs");
                  builder.addDisallowedApplication("com.google.android.apps.photos");
                  builder.addDisallowedApplication("com.google.android.gm");
                  builder.addDisallowedApplication("com.google.android.apps.translate");
             		}

             		if (Build.VERSION.SDK_INT >= 21) {
             			builder.setBlocking(true);
             		}

                if (primaryServer.contains(":") || secondaryServer.contains(":")) {//
                    builder.addAddress("fd00:0000:0000:0000:0000:0000:0000:0001", 120);
                }

                localTunnel = builder
                .addAddress("192.168.0.1", 24)
                .addAddress("10.0.0.1", 24)
                .addAddress("192.168.50.1", 24)
                // .addRoute("0.0.0.0", 0)
                .addDnsServer(primaryServer)
                .addDnsServer(secondaryServer)
                .establish();

                // descriptor = builder.establish();

                // provider = new UdpProvider(descriptor, this);
                // provider.start();
                // provider.process();

            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private InetAddress addDnsServer(Builder builder, String format, byte[] ipv6Template, InetAddress address) throws UnknownHostException {
        int size = dnsServers.size();
        size++;
        if (address instanceof Inet6Address && ipv6Template == null) {
            Log.i("RoqosVPNService", "addDnsServer: Ignoring DNS server " + address);
        } else if (address instanceof Inet4Address) {
            String alias = String.format(format, size + 1);
            dnsServers.put(alias, address.getHostAddress());
            builder.addRoute(alias, 32);
            return InetAddress.getByName(alias);
        } else if (address instanceof Inet6Address) {
            ipv6Template[ipv6Template.length - 1] = (byte) (size + 1);
            InetAddress i6addr = Inet6Address.getByAddress(ipv6Template);
            dnsServers.put(i6addr.getHostAddress(), address.getHostAddress());
            return i6addr;
        }
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if(intent.getAction() == ACTION_ACTIVATE){
                activated = true;
                NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

//                NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

                NotificationCompat.Builder builder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
                    manager.createNotificationChannel(channel);
                    builder = new NotificationCompat.Builder(this, CHANNEL_ID);
                } else {
                    builder = new NotificationCompat.Builder(this);
                }

                final Resources activityRes = this.getResources();
                final int iconResId = activityRes.getIdentifier("icon", "drawable", this.getPackageName());

                Intent deactivateIntent = new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION);
                deactivateIntent.setClass(this, StatusBarBroadcastReceiver.class);

                Intent nIntent = new Intent(this, DnsProxy.class);
                PendingIntent pIntent = PendingIntent.getActivity(this, 0, nIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                builder.setWhen(0)
                        .setContentTitle("noVPN DnsProxy is activated.")
                        .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
//                         .setSmallIcon(iconResId)
                        // .setColor(getResources().getColor(R.color.colorPrimary)) //backward compatibility
                        .setAutoCancel(false)
                        .setOngoing(true)
//                        .setTicker(getResources().getString(R.string.notice_activated))
                        .setContentIntent(pIntent);
//                        .addAction(iconResId, "Deactivate", PendingIntent.getBroadcast(this, 0, deactivateIntent, PendingIntent.FLAG_UPDATE_CURRENT));

                Notification notification = builder.build();

                if (this.mThread == null) {
                    this.mThread = new Thread(this, "RoqosVpn");
//                    this.running = true;
                    this.mThread.start();
                }

//                manager.notify(NOTIFICATION_ACTIVATED, notification);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForeground(1000, notification);
                }
                this.notification = builder;

//                this.notification = builder;
//                if (MainActivity.getInstance() != null) {
//                    MainActivity.getInstance().startActivity(new Intent(getApplicationContext(), MainActivity.class)
//                            .putExtra(MainActivity.LAUNCH_ACTION, MainActivity.LAUNCH_ACTION_SERVICE_DONE));
//                }
                return START_STICKY;
            }
            else {
                stopThread();
                return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }


    @Override
    public void onDestroy() {
        stopThread();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void stopThread() {
//        Log.d(TAG, "stopThread");
        activated = false;
        boolean shouldRefresh = false;
        try {
            if (this.localTunnel != null) {
                this.localTunnel.close();
                this.localTunnel = null;
                // Roqos.deactivateService(Context);
            }
            if (mThread != null) {
                shouldRefresh = true;
                if (provider != null) {
                    provider.shutdown();
                    mThread.interrupt();
                    provider.stop();
                } else {
                    mThread.interrupt();
                }
                mThread = null;
            }
            if (notification != null) {
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(NOTIFICATION_ACTIVATED);
                notification = null;
            }
            dnsServers = null;
        } catch (Exception e) {
          // Log.d("RoqosVPNService", "Roqos VPN service " + e.message());
        }
        stopForeground(true);
        stopSelf();

        // if (shouldRefresh) {
        //     RuleResolver.clear();
        //     DNSServerHelper.clearPortCache();
        // }
    }

    @Override
    public void onRevoke() {
        stopThread();
    }

    public static class VpnNetworkException extends Exception {
        public VpnNetworkException(String s) {
            super(s);
        }

        public VpnNetworkException(String s, Throwable t) {
            super(s, t);
        }

    }

}

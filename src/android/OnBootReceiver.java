package com.roqos.cordova.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import com.roqos.cordova.plugin.Roqos;

import org.apache.cordova.CallbackContext;
import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

public class OnBootReceiver extends BroadcastReceiver {
  public static final String ALWAYS_ON = "alwaysOn";
  private Context mContext;

  @Override
  public void onReceive(Context context, Intent intent) {
    // do startup tasks or start your luncher activity
    this.mContext = context;
    // if(getAlwaysOn(context, OnBootReceiver.ALWAYS_ON) == true) connectDNSVPN();
  }

  private void connectDNSVPN() {
    SharedPreferences prefs_config = this.mContext.getSharedPreferences(DnsProxy.MY_PREFS_CONFIGS, this.mContext.MODE_PRIVATE);
    SharedPreferences prefs_addEDNOption = this.mContext.getSharedPreferences(DnsProxy.MY_PREFS_ADDEDNSOPTION, this.mContext.MODE_PRIVATE);

    String dnsServer = prefs_config.getString("dnsServer", "8.8.8.8");
    String port = prefs_config.getString("port", "53");
    String VPNSessionTitle = prefs_config.getString("VPNSessionTitle", null);
    String secondaryServer = prefs_config.getString("secondaryServer", null);
    String secondaryPort = prefs_config.getString("secondaryPort", null);
    String optionCode = prefs_addEDNOption.getString("optionCode", null);
    String message = prefs_addEDNOption.getString("message", null);

    Roqos.dnsServer = dnsServer;
    Roqos.port = Integer.parseInt(port);
    Roqos.VPNSession = VPNSessionTitle;

    Roqos.DNS_SERVERS.set(0, new DNSServer(dnsServer, 0, Integer.parseInt(port)));
    Roqos.DNS_SERVERS.set(1, new DNSServer(secondaryServer, 1, Integer.parseInt(secondaryPort)));

    Roqos.addEDNSOption(optionCode, message);

    Intent intent = VpnService.prepare(this.mContext);

    if (intent != null) {
        ((Activity)this.mContext).startActivityForResult(intent, 0);
    } else {
        onActivityResult(0, Activity.RESULT_OK, null);
    }
  }

  public void onActivityResult(int request, int result, Intent data) {
    if (result == Activity.RESULT_OK) {
        // Toast.makeText( this.mContext,
        //         "onActivityResult", Toast.LENGTH_LONG).show();
        RoqosVPNService.primaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getPrimary());
        RoqosVPNService.secondaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getSecondary());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          this.mContext.startForegroundService(new Intent(this.mContext, RoqosVPNService.class).setAction(RoqosVPNService.ACTION_ACTIVATE));
        } else {
          this.mContext.startService(new Intent(this.mContext, RoqosVPNService.class).setAction(RoqosVPNService.ACTION_ACTIVATE));
        }
    }
  }

  public static void saveAlwaysOn(Context context, boolean alwaysOn, String key) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    Editor prefsedit = prefs.edit();
    prefsedit.putBoolean(key, alwaysOn);
    prefsedit.apply();
  }

  private boolean getAlwaysOn(Context context, String key) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    boolean useStartOnBoot = prefs.getBoolean(key, false);
    return useStartOnBoot;
  }
}

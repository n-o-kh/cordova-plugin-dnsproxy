package com.roqos.cordova.plugin;
// Cordova-required packages
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import android.os.Build;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import android.widget.Toast;

import java.util.ArrayList;

public class DnsProxy extends CordovaPlugin {
  private static final String DURATION_LONG = "long";
  public static final String MY_PREFS_CONFIGS = "MY_PREFS_CONFIGS";
  public static final String MY_PREFS_ADDEDNSOPTION = "MY_PREFS_ADDEDNSOPTION";

  @Override
  public boolean execute(String action, JSONArray args,
    final CallbackContext callbackContext) {
        // Verify that the user sent a 'show' action
        if (!action.equals("isPageLock") && !action.equals("getTun") && !action.equals("getCurrentDNS") && !action.equals("config") && !action.equals("activate") && !action.equals("isActivated") && !action.equals("removeAllEDNSOption") && !action.equals("addEDNSOption") && !action.equals("deactivate")) {
            callbackContext.error("\"" + action + "\" is not a recognized action.");
            return false;
        }
        if(action.equals("activate")){

            Intent intent = VpnService.prepare(this.cordova.getActivity().getApplicationContext());
            if (intent != null) {
                cordova.setActivityResultCallback(this);
                cordova.getActivity().startActivityForResult(intent, 0);
            } else {
                onActivityResult(0, Activity.RESULT_OK, null);
            }

            // Send a positive result to the callbackContext
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(pluginResult);

            //Save Always-on to boot receiver
//            OnBootReceiver.saveAlwaysOn(this.cordova.getActivity().getApplicationContext(), true, OnBootReceiver.ALWAYS_ON);
            return true;
        }

        if(action.equals("config")){
            try {
            
                JSONObject options = args.getJSONObject(0);
                final String dnsServer = options.getString("dnsServer") != "" ? options.getString("dnsServer") : "8.8.8.8";
                final String port = options.getString("port") != "" ? options.getString("port") : "53";
                final String VPNSessionTitle = options.getString("VPNSessionTitle") != "" ? options.getString("VPNSessionTitle") : "Roqos";
              
                final String secondaryServer = options.getString("secondaryServer") != "" ? options.getString("secondaryServer") : "8.8.8.8";
                final String secondaryPort = options.getString("secondaryPort") != "" ? options.getString("secondaryPort") : "53";

                Roqos.dnsServer = dnsServer;
                Roqos.port = Integer.parseInt(port);
                Roqos.VPNSession = VPNSessionTitle;

                Roqos.DNS_SERVERS.set(0, new DNSServer(dnsServer, 0, Integer.parseInt(port)));
                Roqos.DNS_SERVERS.set(1, new DNSServer(secondaryServer, 1, Integer.parseInt(secondaryPort)));

                Log.d("DNSProxy", secondaryServer);

                SharedPreferences.Editor editor = this.cordova.getActivity().getApplicationContext().getSharedPreferences(MY_PREFS_CONFIGS, this.cordova.getActivity().getApplicationContext().MODE_PRIVATE).edit();
                editor.putString("dnsServer", dnsServer);
                editor.putString("port", port);
                editor.putString("VPNSessionTitle", VPNSessionTitle);
                editor.putString("secondaryServer", secondaryServer);
                editor.putString("secondaryPort", secondaryPort);
                editor.apply();

            } catch (JSONException e) {
                callbackContext.error("Error encountered: " + e.getMessage());
                return false;
            }

            // Send a positive result to the callbackContext
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(pluginResult);
            return true;
        }

        if (action.equals("isPageLock")){
            try {
                JSONObject locks = args.getJSONObject(0);
                final boolean pageLock = locks.getBoolean("pageLock") ? locks.getBoolean("pageLock") : false;

                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
                callbackContext.sendPluginResult(pluginResult);

//                Save Always-on to boot receiver
                OnBootReceiver.saveAlwaysOn(this.cordova.getActivity().getApplicationContext(), pageLock, OnBootReceiver.ALWAYS_ON);
            } catch (JSONException e) {
                callbackContext.error("Error encountered: " + e.getMessage());
                return false;
            }

            return true;
        }

        if(action.equals("deactivate")){

            Roqos.deactivateService(this.cordova.getActivity().getApplicationContext());

            // Send a positive result to the callbackContext
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(pluginResult);
            //Save Always-on to boot receiver
            OnBootReceiver.saveAlwaysOn(this.cordova.getActivity().getApplicationContext(), false, OnBootReceiver.ALWAYS_ON);
            return true;
        }

        if(action.equals("isActivated")){
            // Send a positive result to the callbackContext
            // PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            callbackContext.success(String.valueOf(RoqosVPNService.isActivated()));
            return true;
        }

        if(action.equals("getCurrentDNS")){
            JSONArray jsArray = new JSONArray(Roqos.getCurrentDNS(this.cordova.getActivity().getApplicationContext()));
            Log.d("DNSProxy", "dns = " + Roqos.getCurrentDNS(this.cordova.getActivity().getApplicationContext()));
            callbackContext.success(jsArray);
            return true;
        }

        if(action.equals("getTun")){
            JSONArray jsArray = new JSONArray(Roqos.getTun());
            callbackContext.success(jsArray);
            return true;
        }

        if(action.equals("addEDNSOption")){

            try {
            
                JSONObject options = args.getJSONObject(0);                
                Roqos.addEDNSOption(options.getString("optionCode"), options.getString("message"));

                SharedPreferences.Editor editors = this.cordova.getActivity().getApplicationContext().getSharedPreferences(MY_PREFS_ADDEDNSOPTION, this.cordova.getActivity().getApplicationContext().MODE_PRIVATE).edit();
                editors.putString("optionCode", options.getString("optionCode"));
                editors.putString("message", options.getString("message"));
                editors.apply();

            } catch (JSONException e) {
                callbackContext.error("Error encountered: " + e.getMessage());
                return false;
            }

            // Send a positive result to the callbackContext
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(pluginResult);
            return true;
        }

        if(action.equals("removeAllEDNSOption")){

            Roqos.optionsCode = new ArrayList<String>();
            Roqos.ednsMessage = new ArrayList<String>();

            // Send a positive result to the callbackContext
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(pluginResult);
            return true;

        }

        callbackContext.error("\"" + action + "\" is not a recognized action.");
        return false;
  }

  public void onActivityResult(int request, int result, Intent data) {
        if (result == Activity.RESULT_OK) {
            // Toast.makeText( this.cordova.getActivity().getApplicationContext(),
            //         "onActivityResult", Toast.LENGTH_LONG).show();
            RoqosVPNService.primaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getPrimary());
            RoqosVPNService.secondaryServer = DNSServerHelper.getAddressById(DNSServerHelper.getSecondary());
            this.cordova.getActivity().getApplicationContext().startService(new Intent(this.cordova.getActivity().getApplicationContext(), RoqosVPNService.class).setAction(RoqosVPNService.ACTION_ACTIVATE));
        }
    }

}

package com.andreydymko.nomic;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

import static android.content.Context.WIFI_SERVICE;

public class Utils {
    public static String getWifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endian if needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("WIFIIP", "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    public static String getStateString(Context context, @StreamingService.serviceState int state) {
        switch (state) {
            case StreamingService.SERVICE_STATE.STOPPED:
                return context.getString(R.string.service_state_stopped);
            case StreamingService.SERVICE_STATE.STOPPING:
                return context.getString(R.string.service_state_stopping);
            case StreamingService.SERVICE_STATE.STARTING:
                return context.getString(R.string.service_state_starting);
            case StreamingService.SERVICE_STATE.STARTED:
                return context.getString(R.string.service_state_started) + " (" + getWifiIpAddress(context) + ")";
            case StreamingService.SERVICE_STATE.CONNECTED:
                return context.getString(R.string.service_state_connected);
            case StreamingService.SERVICE_STATE.ERROR:
                return "Error";
            default:
                return "Bad Value";
        }
    }

    public static String trimHostname(@NotNull String hostname) {
        try {
            return hostname.replace("/", "").split(":", 2)[0];
        } catch (IndexOutOfBoundsException e) {
            return hostname.replace("/", "");
        }
    }
}

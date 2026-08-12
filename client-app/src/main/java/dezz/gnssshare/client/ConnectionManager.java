/*
 * Copyright © 2025-2026 Dezz (https://github.com/DezzK)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dezz.gnssshare.client;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.Objects;

public class ConnectionManager {
    private static final String TAG = "ConnectionManager";

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING, // sending HELLO, no RESPONSE seen recently yet
        CONNECTED,  // a RESPONSE arrived within the recency window
    }

    public interface ConnectionListener {
        void onConnectionStateChanged(ConnectionState state, String message, String serverAddress);
    }

    private final ConnectionListener listener;
    private final Context context;

    private ConnectionState currentState = ConnectionState.DISCONNECTED;
    private String serverAddress = null;

    public ConnectionManager(Context context, ConnectionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    /** Resolve the server address from preferences (gateway IP or fixed). May return null. */
    public String resolveServerAddress() {
        if (Preferences.useGatewayIp(context)) {
            serverAddress = getGatewayIpAddress(context);
        } else {
            serverAddress = Preferences.serverAddress(context);
        }
        return serverAddress;
    }

    public ConnectionState getCurrentState() {
        return currentState;
    }

    public boolean isConnected() {
        return currentState == ConnectionState.CONNECTED;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setState(ConnectionState newState, String message, String serverAddress) {
        if (currentState != newState || !Objects.equals(this.serverAddress, serverAddress)) {
            Log.d(TAG, "State change: " + currentState + " -> " + newState + " (" + message + ")");
            currentState = newState;
            this.serverAddress = serverAddress;
            listener.onConnectionStateChanged(newState, message, serverAddress);
        }
    }

    private static String getGatewayIpAddress(Context context) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        if (wifiManager == null) {
            return null;
        }
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        if (dhcpInfo == null || dhcpInfo.gateway == 0) {
            return null;
        }
        return intToIp(dhcpInfo.gateway);
    }

    private static String intToIp(int i) {
        return (i & 0xFF) + "." +
                ((i >> 8) & 0xFF) + "." +
                ((i >> 16) & 0xFF) + "." +
                ((i >> 24) & 0xFF);
    }
}

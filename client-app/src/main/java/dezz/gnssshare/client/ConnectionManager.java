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

    private volatile ConnectionState currentState = ConnectionState.DISCONNECTED;
    private volatile String serverAddress = null;
    private volatile String learnedServerAddress = null;

    public ConnectionManager(Context context, ConnectionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    /** True when in Auto-discover mode (vs Fixed address). */
    public boolean isAutoDiscover() {
        return Preferences.autoDiscover(context);
    }

    /**
     * Address to send HELLO to. In Fixed mode: the configured address. In Auto mode: the learned
     * server address, or null when it hasn't been discovered yet (caller should broadcast).
     */
    public String getSendTarget() {
        if (isAutoDiscover()) {
            return learnedServerAddress;
        }
        return Preferences.serverAddress(context);
    }

    public void setLearnedServerAddress(String addr) {
        learnedServerAddress = addr;
    }

    public void clearLearnedServerAddress() {
        learnedServerAddress = null;
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
}

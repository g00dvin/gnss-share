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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import dezz.gnssshare.proto.LocationProto;
import dezz.gnssshare.shared.Protocol;

public class GNSSClientService extends Service implements ConnectionManager.ConnectionListener {
    private static final String TAG = "GNSSClientService";
    private static final String CHANNEL_ID = "GNSSClientChannel";
    private static final int NOTIFICATION_ID = 1;

    private static GNSSClientService instance = null;

    private ConnectionManager connectionManager;
    private MockLocationManager mockLocationManager;
    private NotificationManager notificationManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private Location lastReceivedLocation;
    private static long lastUpdateTime;
    private long lastLocationTimestamp = 0;
    private int lastBroadcastSatelliteCount = -1;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile long lastResponseTime = 0;

    private DatagramSocket udpSocket;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final long HELLO_INTERVAL_MS = 1000;
    private static final long CONNECTED_TIMEOUT_MS = 3000;
    private final Runnable helloTick = this::sendHelloTick;

    private static final String WIDGET_SATELLITE_STATUS_ACTION = "dezz.gnssshare.action.SATELLITE_STATUS";
    private static final String WIDGET_PACKAGE = "dezz.status.widget";

    public static boolean isServiceEnabled(Context context) {
        return Preferences.serviceEnabled(context);
    }

    public static boolean isServiceRunning() {
        return instance != null;
    }

    public static ConnectionManager.ConnectionState getConnectionState() {
        return instance != null && instance.connectionManager != null ? instance.connectionManager.getCurrentState() : ConnectionManager.ConnectionState.DISCONNECTED;
    }

    public static String getServerAddress() {
        return instance != null && instance.connectionManager != null ? instance.connectionManager.getServerAddress() : null;
    }

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            startTransport();
        }

        @Override
        public void onLost(@NonNull Network network) {
            stopTransport("WiFi disconnected");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = getSystemService(NotificationManager.class);
        mockLocationManager = new MockLocationManager(this);
        connectionManager = new ConnectionManager(this, this);

        registerWiFiStateReceiver();
        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(false),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, createNotification(false));
        }

        instance = this;
    }

    private void registerWiFiStateReceiver() {
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        ConnectivityManager connectivityManager = getSystemService(ConnectivityManager.class);
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;

        super.onDestroy();

        stopTransport("Service destroyed");
        executor.shutdown();
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    // ConnectionManager.ConnectionListener implementation
    @Override
    public void onConnectionStateChanged(ConnectionManager.ConnectionState state, String message, String serverAddress) {
        Log.d(TAG, "Connection state: " + state + " - " + message);

        updateNotification();

        // Notify activity about connection status change
        sendBroadcast(new Intent("dezz.gnssshare.CONNECTION_CHANGED")
                .putExtra("state", state.toString())
                .putExtra("serverAddress", serverAddress));
    }

    private void startTransport() {
        if (running.getAndSet(true)) {
            return;
        }

        String server = connectionManager.resolveServerAddress();
        connectionManager.setState(ConnectionManager.ConnectionState.CONNECTING, "Connecting to server...", server);

        if (!MockLocationManager.isMockLocationEnabled(getContentResolver())) {
            Log.w(TAG, "Mock locations not enabled - please enable in Developer Options");
            broadcastMockLocationStatus(getString(R.string.mock_location_enable_message), true);
        }
        try {
            mockLocationManager.startMockLocationProvider();
        } catch (SecurityException e) {
            broadcastMockLocationStatus(getString(R.string.mock_location_permission_denied), true);
        } catch (Exception e) {
            broadcastMockLocationStatus(String.format(getString(R.string.mock_location_setup_failed), e.getMessage()), true);
        }

        executor.execute(this::receiveLoop);
        mainHandler.post(helloTick);
    }

    private void stopTransport(String reason) {
        if (!running.getAndSet(false)) {
            return;
        }
        Log.i(TAG, "Stopping transport: " + reason);
        mainHandler.removeCallbacks(helloTick);
        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
        }
        lastLocationTimestamp = 0;
        lastBroadcastSatelliteCount = -1;
        broadcastSatelliteStatusToWidget(0);

        if (instance == null) {
            mockLocationManager.shutdown();
        } else {
            mockLocationManager.stopMockLocationProvider(5000);
        }

        connectionManager.setState(ConnectionManager.ConnectionState.DISCONNECTED, reason, null);
        sendBroadcast(new Intent("dezz.gnssshare.CONNECTION_CHANGED")
                .putExtra("state", ConnectionManager.ConnectionState.DISCONNECTED.toString()));
    }

    private void sendHelloTick() {
        if (!running.get()) {
            return;
        }
        String server = connectionManager.getServerAddress();
        if (server == null) {
            server = connectionManager.resolveServerAddress();
        }
        if (server != null && udpSocket != null) {
            final String dest = server;
            executor.execute(() -> {
                try {
                    byte[] hello = Protocol.buildPacket(Protocol.TYPE_HELLO, null);
                    udpSocket.send(new DatagramPacket(hello, hello.length,
                            InetAddress.getByName(dest), Protocol.PORT));
                } catch (IOException e) {
                    Log.w(TAG, "Failed to send HELLO", e);
                }
            });
        }
        // Recency check: drop to CONNECTING if no RESPONSE within the window.
        if (connectionManager.isConnected()
                && System.currentTimeMillis() - lastResponseTime > CONNECTED_TIMEOUT_MS) {
            connectionManager.setState(ConnectionManager.ConnectionState.CONNECTING,
                    "Waiting for server...", connectionManager.getServerAddress());
        }
        mainHandler.postDelayed(helloTick, HELLO_INTERVAL_MS);
    }

    private void receiveLoop() {
        try {
            udpSocket = new DatagramSocket();
        } catch (IOException e) {
            Log.e(TAG, "Failed to open UDP socket", e);
            return;
        }
        byte[] buffer = new byte[Protocol.MAX_PACKET_BYTES];
        while (running.get() && udpSocket != null && !udpSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                handlePacket(packet);
            } catch (IOException e) {
                if (running.get()) {
                    Log.v(TAG, "UDP receive interrupted: " + e.getMessage());
                }
            }
        }
    }

    private void handlePacket(DatagramPacket packet) {
        Protocol.Header header;
        try {
            header = Protocol.parse(packet.getData(), packet.getLength());
        } catch (IllegalArgumentException e) {
            return;
        }

        if (header.type == Protocol.TYPE_VERSION_MISMATCH
                || !Protocol.isSupportedVersion(header.version)) {
            broadcastMockLocationStatus(getString(R.string.version_mismatch), true);
            return;
        }
        if (header.type != Protocol.TYPE_RESPONSE) {
            return;
        }

        try {
            LocationProto.ServerResponse response = LocationProto.ServerResponse.parseFrom(
                    java.util.Arrays.copyOfRange(packet.getData(),
                            header.payloadOffset, header.payloadOffset + header.payloadLength));

            lastResponseTime = System.currentTimeMillis();
            if (!connectionManager.isConnected()) {
                connectionManager.setState(ConnectionManager.ConnectionState.CONNECTED,
                        "Receiving from server", connectionManager.getServerAddress());
            }

            if (response.hasLocationUpdate()) {
                handleLocationUpdate(response);
            } else {
                Log.i(TAG, "Server status: " + response.getStatus());
                Intent intent = new Intent("dezz.gnssshare.LOCATION_UPDATE");
                intent.putExtra("satellites", response.getSatellites());
                sendBroadcast(intent);
            }
            broadcastSatelliteStatusToWidget(response.getSatellites());
        } catch (IOException e) {
            Log.w(TAG, "Failed to parse ServerResponse", e);
        }
    }

    private void handleLocationUpdate(LocationProto.ServerResponse response) {
        try {
            LocationProto.LocationUpdate locationUpdate = response.getLocationUpdate();
            // Create Android Location object
            Location location = new Location(LocationManager.GPS_PROVIDER);
            location.setLatitude(locationUpdate.getLatitude());
            location.setLongitude(locationUpdate.getLongitude());
            location.setTime(locationUpdate.getTimestamp());
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            location.setAltitude(locationUpdate.getAltitude());
            location.setAccuracy(locationUpdate.getAccuracy());
            location.setBearing(locationUpdate.getBearing());
            location.setSpeed(locationUpdate.getSpeed());

            Log.i(TAG, "Received location update: " + location);

            // Update internal state
            lastReceivedLocation = location;
            lastUpdateTime = System.currentTimeMillis();

            // Update notification with new location data
            updateNotification();

            // Broadcast location update to activity
            Intent intent = new Intent("dezz.gnssshare.LOCATION_UPDATE");
            intent.putExtra("location", location);
            intent.putExtra("satellites", response.getSatellites());
            intent.putExtra("provider", locationUpdate.getProvider());
            intent.putExtra("locationAge", locationUpdate.getLocationAge());
            sendBroadcast(intent);

            // Only push to mock locations if GPS timestamp is new
            // (avoids re-pushing stale location when server has no fresh GPS fix)
            long gpsTimestamp = locationUpdate.getTimestamp();
            if (gpsTimestamp != lastLocationTimestamp) {
                lastLocationTimestamp = gpsTimestamp;
                mockLocationManager.setMockLocation(location);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception - mock location permission denied", e);
            broadcastMockLocationStatus(getString(R.string.mock_location_permission_denied), true);
        } catch (Exception e) {
            Log.e(TAG, "Error setting mock location", e);
            broadcastMockLocationStatus(String.format(getString(R.string.mock_location_setup_failed), e.getMessage()), true);
        }
    }

    private void broadcastMockLocationStatus(String message, boolean error) {
        Intent intent = new Intent("dezz.gnssshare.MOCK_LOCATION_STATUS");
        intent.putExtra("message", message);
        intent.putExtra("error", error);
        sendBroadcast(intent);
    }

    /**
     * Broadcasts the satellite count to the external widget app, but only when the count changes.
     * Uses an explicit package target — required for receivers registered with RECEIVER_NOT_EXPORTED.
     */
    private void broadcastSatelliteStatusToWidget(int count) {
        if (count == lastBroadcastSatelliteCount) {
            return;
        }
        lastBroadcastSatelliteCount = count;
        Intent intent = new Intent(WIDGET_SATELLITE_STATUS_ACTION);
        intent.setPackage(WIDGET_PACKAGE);
        intent.putExtra("count", count);
        sendBroadcast(intent);
    }

    public static long getLastUpdateTime() {
        return lastUpdateTime;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(String.format(getString(R.string.notification_channel_description), getString(R.string.app_name)));

        notificationManager.createNotificationChannel(channel);
    }

    private Notification createNotification(boolean isConnected) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = isConnected ?
                String.format(getString(R.string.notification_title_connected), getString(R.string.app_name)) :
                String.format(getString(R.string.notification_title_disconnected), getString(R.string.app_name));

        String text = isConnected ?
                (lastReceivedLocation != null ?
                        String.format(getString(R.string.notification_text_connected),
                                (System.currentTimeMillis() - lastUpdateTime) / 1000.0) :
                        getString(R.string.notification_text_connected_no_age)) :
                getString(R.string.notification_text_disconnected);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification() {
        boolean isConnected = connectionManager != null && connectionManager.isConnected();

        notificationManager.notify(NOTIFICATION_ID, createNotification(isConnected));
    }
}

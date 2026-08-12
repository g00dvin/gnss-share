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

package dezz.gnssshare.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dezz.gnssshare.proto.LocationProto;
import dezz.gnssshare.shared.Protocol;

public class GNSSServerService extends Service {
    private static final String TAG = "GNSSServerService";
    private static final String CHANNEL_ID = "GNSSServerChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREF_IS_SERVICE_ENABLED = "isServiceEnabled";
    private static final long BT_AUTO_STOP_DELAY_MS = 10000; // 10 seconds
    private static final long CLIENT_TIMEOUT_MS = 5000;   // no HELLO for this long => client gone
    private static final long KEEPALIVE_INTERVAL_MS = 1000; // resend latest response at least this often

    private static boolean running = false;
    private static GNSSServerService instance = null;

    private String serverStartError = null;

    private volatile DatagramSocket udpSocket;
    private volatile SocketAddress clientAddr = null;   // the single current client
    private volatile long lastHeard = 0;                // last HELLO time from clientAddr
    private final Runnable keepaliveRunnable = this::keepaliveTick;
    private LocationManager locationManager = null;
    private FusedLocationProviderClient fusedLocationProviderClient = null;
    private final com.google.android.gms.location.LocationListener fusedLocationListener = this::handleLocationUpdate;
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            handleLocationUpdate(location);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            Log.d(TAG, "Provider enabled: " + provider);
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            Log.d(TAG, "Provider disabled: " + provider);
        }
    };

    private NotificationManager notificationManager;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final GnssStatus.Callback gnssStatusCallback = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
            gnssStatus = status;
            lastServerResponse.setSatellites(getSatelliteCount());

            if (isServiceRunning() && clientAddr != null && !lastServerResponse.hasLocationUpdate()) {
                mainHandler.post(() -> updateNotification("GNSS status changed"));
            }
        }
    };

    private final LocationProto.ServerResponse.Builder lastServerResponse = LocationProto.ServerResponse.newBuilder()
            .setStatus(LocationProto.Status.UNINITIALIZED);

    // We need to use such runnable to make scheduled stopping cancelable
    private final Runnable stopLocationUpdates = this::stopLocationUpdates;

    // Bluetooth auto-stop runnable
    private final Runnable btAutoStopRunnable = this::btAutoStopService;

    private GnssStatus gnssStatus = null;
    private boolean isGnssActive = false;

    @Override
    public void onCreate() {
        notificationManager = getSystemService(NotificationManager.class);

        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, createNotification());
        }

        running = true;
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serverStartError = null;
        startServer();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;

        cancelBluetoothAutoStop();
        stopServer();
        stopLocationUpdates();

        locationManager = null;

        executor.shutdown();

        notificationManager.cancel(NOTIFICATION_ID);
        notificationManager = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void initializeLocationManager() {
        if (locationManager != null) {
            return;
        }

        locationManager = getSystemService(LocationManager.class);

        try {
            locationManager.registerGnssStatusCallback(gnssStatusCallback, mainHandler);

            Log.d(TAG, "GNSS status callback registered");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to register GNSS status callback", e);
        }
    }

    private void initializeFusedLocationProviderClient() {
        // Supported on Android 12+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }

        // User opted out
        if (!Preferences.fusedLocationEnabled(this)) {
            return;
        }

        try {
            // Google Play Services are required
            if (!isGooglePlayServicesAvailable(this)) {
                return;
            }

            if (fusedLocationProviderClient != null) {
                return;
            }

            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        } catch (NoClassDefFoundError e) {
            Log.w(TAG, "Google Play Services not available on this device", e);
            fusedLocationProviderClient = null;
        }
    }

    public static boolean isFusedLocationSupported(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        try {
            GoogleApiAvailability api = GoogleApiAvailability.getInstance();
            return api.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    private void startServer() {
        executor.execute(() -> {
            // Guard against START_STICKY redelivery (or any re-entry) while already bound: binding
            // the port again would throw BindException and the catch below would tear down the
            // live socket. If we're already running, do nothing.
            if (udpSocket != null && !udpSocket.isClosed()) {
                Log.d(TAG, "UDP server already running; ignoring start request");
                return;
            }
            try {
                udpSocket = new DatagramSocket(Protocol.PORT);
                Log.d(TAG, "UDP server bound on port " + Protocol.PORT);
            } catch (Throwable e) {
                Log.e(TAG, "Error starting UDP server", e);
                serverStartError = e.getMessage();
                stopServer();
                return;
            }

            mainHandler.post(() -> mainHandler.postDelayed(keepaliveRunnable, KEEPALIVE_INTERVAL_MS));

            byte[] buffer = new byte[Protocol.MAX_PACKET_BYTES];
            while (udpSocket != null && !udpSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    handlePacket(packet);
                } catch (IOException e) {
                    if (udpSocket != null && !udpSocket.isClosed()) {
                        Log.e(TAG, "Error receiving UDP packet", e);
                    }
                }
            }
        });
    }

    private void handlePacket(DatagramPacket packet) {
        Protocol.Header header;
        try {
            header = Protocol.parse(packet.getData(), packet.getLength());
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Discarding malformed packet from " + packet.getSocketAddress());
            return;
        }

        if (!Protocol.isSupportedVersion(header.version)) {
            Log.w(TAG, "Version mismatch from " + packet.getSocketAddress() + ": " + header.version);
            sendPacket(Protocol.buildPacket(Protocol.TYPE_VERSION_MISMATCH,
                    new byte[]{(byte) Protocol.VERSION}), packet.getSocketAddress());
            return;
        }

        if (header.type == Protocol.TYPE_HELLO) {
            boolean isNewClient = (clientAddr == null);
            clientAddr = packet.getSocketAddress();
            lastHeard = System.currentTimeMillis();
            if (isNewClient) {
                Log.i(TAG, "Client present: " + clientAddr);
                mainHandler.post(this::startLocationUpdates);
                cancelBluetoothAutoStop();
                mainHandler.post(() -> updateNotification("Client connected"));
            }
        } else {
            Log.w(TAG, "Unexpected packet type from client: " + header.type);
        }
    }

    private void sendPacket(byte[] data, SocketAddress dest) {
        if (udpSocket == null || udpSocket.isClosed() || dest == null) {
            return;
        }
        try {
            udpSocket.send(new DatagramPacket(data, data.length, dest));
        } catch (IOException e) {
            Log.w(TAG, "Error sending UDP packet", e);
        }
    }

    private void stopServer() {
        Log.d(TAG, "Stopping UDP server");
        mainHandler.removeCallbacks(keepaliveRunnable);
        clientAddr = null;
        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
        }
    }

    private void startLocationUpdates() {
        // If location updates were scheduled to be stopped, remove the scheduled action
        mainHandler.removeCallbacks(this.stopLocationUpdates);

        initializeLocationManager();
        initializeFusedLocationProviderClient();

        try {
            Log.d(TAG, "Starting location updates...");

            lastServerResponse.setStatus(LocationProto.Status.AWAITING_LOCATION);

            final int MIN_INTERVAL_MS = 200;
            final int MIN_DISTANCE_M = 0;
            if (fusedLocationProviderClient != null) {
                LocationRequest request = new LocationRequest.Builder(MIN_INTERVAL_MS)
                        .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
                        .setWaitForAccurateLocation(false)
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setGranularity(Granularity.GRANULARITY_FINE)
                        .build();
                fusedLocationProviderClient.requestLocationUpdates(request, fusedLocationListener, Looper.getMainLooper());
            } else {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_INTERVAL_MS,
                        MIN_DISTANCE_M,
                        locationListener
                );
            }

            Log.d(TAG, "Location updates started");

            isGnssActive = true;

            updateNotification("Started location updates");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        } catch (Exception e) {
            Log.e(TAG, "Error starting location updates", e);
        }
    }

    private void stopLocationUpdates() {
        if (running && clientAddr != null) {
            Log.w(TAG, "Location updates not stopped: still have clients connected");
            return;
        }

        Log.d(TAG, "Stopping location updates...");

        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
            locationManager = null;
        }

        if (fusedLocationProviderClient != null) {
            fusedLocationProviderClient.removeLocationUpdates(fusedLocationListener);
            fusedLocationProviderClient = null;
        }

        Log.d(TAG, "Location updates stopped");

        isGnssActive = false;
        lastServerResponse.setStatus(LocationProto.Status.LOCATION_STOPPED);

        updateNotification("Stopped location updates");
    }

    private void handleLocationUpdate(Location location) {
        Log.d(TAG, String.format("Handling location update: %s", location));

        // Create protobuf message
        LocationProto.LocationUpdate.Builder builder = LocationProto.LocationUpdate.newBuilder()
                .setTimestamp(location.getTime())
                .setLatitude(location.getLatitude())
                .setLongitude(location.getLongitude())
                .setProvider(location.getProvider())
                .setLocationAge((System.currentTimeMillis() - location.getTime()) / 1000.0f);

        if (location.hasAltitude()) {
            builder.setAltitude(location.getAltitude());
        }
        if (location.hasAccuracy()) {
            builder.setAccuracy(location.getAccuracy());
        }
        if (location.hasBearing()) {
            builder.setBearing(location.getBearing());
        }
        if (location.hasSpeed()) {
            builder.setSpeed(location.getSpeed());
        }
        if (location.hasSpeedAccuracy()) {
            builder.setSpeedAccuracy(location.getSpeedAccuracyMetersPerSecond());
        }
        if (location.hasBearingAccuracy()) {
            builder.setBearingAccuracy(location.getBearingAccuracyDegrees());
        }

        lastServerResponse.setStatus(LocationProto.Status.TRANSMITTING_LOCATION)
                .setLocationUpdate(builder.build());

        updateNotification("Received location update");

        // Broadcast to the connected client
        Log.d(TAG, "Broadcasting location: " + location);
        executor.execute(() -> broadcastLocationUpdate(lastServerResponse.build()));
    }

    private void broadcastLocationUpdate(LocationProto.ServerResponse serverResponse) {
        SocketAddress dest = clientAddr;
        if (dest == null) {
            return;
        }
        sendPacket(Protocol.buildPacket(Protocol.TYPE_RESPONSE, serverResponse.toByteArray()), dest);
    }

    private void keepaliveTick() {
        SocketAddress dest = clientAddr;
        if (dest != null) {
            long silence = System.currentTimeMillis() - lastHeard;
            if (silence > CLIENT_TIMEOUT_MS) {
                Log.i(TAG, "Client timed out (" + silence + "ms), marking gone");
                if (clientAddr == dest) {          // still the same client we timed out
                    clientAddr = null;
                    onClientGone();
                }
            } else {
                // Resend the latest response so the client's recency clock stays fresh.
                LocationProto.ServerResponse resp = lastServerResponse.build();
                executor.execute(() -> broadcastLocationUpdate(resp));
            }
        }
        if (running) {
            mainHandler.postDelayed(keepaliveRunnable, KEEPALIVE_INTERVAL_MS);
        }
    }

    private void onClientGone() {
        Log.d(TAG, "No client; scheduling stop of location updates in 15 seconds");
        mainHandler.removeCallbacks(this.stopLocationUpdates);
        mainHandler.postDelayed(this.stopLocationUpdates, 15000);
        evaluateAutoStop();
        mainHandler.post(() -> updateNotification("Client disconnected"));
    }

    public static boolean isServiceRunning() {
        return running;
    }

    // Public methods for checking service state
    public static boolean isServiceEnabled(Context context) {
        return getPrefs(context).getBoolean(PREF_IS_SERVICE_ENABLED, false);
    }

    public static void setServiceEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(PREF_IS_SERVICE_ENABLED, enabled).apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(context.getPackageName() + "_preferences", MODE_PRIVATE);
    }

    // Notifications

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.app_description));
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String content;
        if (serverStartError == null) {
            if (clientAddr != null) {
                content = getString(R.string.notification_clients_single);
            } else {
                content = getString(R.string.notification_no_clients);
            }

            content += getString(R.string.notification_divider);

            if (isGnssActive) {
                content += String.format(
                        getString(R.string.notification_satellites),
                        getSatelliteCount()
                );


                if (lastServerResponse.hasLocationUpdate()) {
                    content += getString(R.string.notification_divider) + String.format(
                            getString(R.string.notification_age),
                            (System.currentTimeMillis() - lastServerResponse.getLocationUpdate().getTimestamp()) / 1000.0
                    );
                }
            } else {
                content += getString(R.string.notification_gnss_inactive);
            }
        } else {
            content = serverStartError;
        }

        // Stop action for notification shade
        Intent stopIntent = new Intent("dezz.gnssshare.server.STOP");
        stopIntent.setPackage(getPackageName());
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(String.format(getString(serverStartError == null ? R.string.notification_title : R.string.notification_failed_title), getString(R.string.app_name)))
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_media_pause, getString(R.string.disable_service), stopPendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String reason) {
        if (notificationManager == null) {
            return;
        }
        Log.d(TAG, "Updating notification: " + reason);
        notificationManager.notify(NOTIFICATION_ID, createNotification());
    }

    public int getSatelliteCount() {
        if (gnssStatus == null) {
            return 0;
        }
        return gnssStatus.getSatelliteCount();
    }

    private boolean isGooglePlayServicesAvailable(Context context) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    // Bluetooth auto-stop methods
    //
    // Unified logic:
    //   - evaluateAutoStop() is called on BT disconnect and on last client disconnect.
    //     Schedules a 10s stop only when BOTH all BT trigger devices AND all clients are gone.
    //   - cancelBluetoothAutoStop() is called on BT reconnect and on new client connect.
    //   - btAutoStopService() re-checks conditions as a safety net before actually stopping.

    /**
     * Called from BluetoothReceiver (BT disconnect) and onClientGone (client timed out).
     * Schedules auto-stop only if both conditions are met.
     */
    public static void evaluateAutoStop() {
        if (instance != null) {
            instance.doEvaluateAutoStop();
        }
    }

    /** Called from BluetoothReceiver (BT reconnect) and handlePacket (new client connect). */
    public static void cancelBluetoothAutoStopRequest() {
        if (instance != null) {
            instance.cancelBluetoothAutoStop();
        }
    }

    private void doEvaluateAutoStop() {
        if (!running) return;

        // Only auto-stop if BT auto-start/stop feature is enabled in preferences
        if (!Preferences.bluetoothAutoStartEnabled(this)) {
            Log.d(TAG, "BT auto-start/stop disabled in preferences, skipping auto-stop evaluation");
            return;
        }

        boolean btGone = BluetoothReceiver.allTriggerDevicesDisconnected();
        boolean clientsGone = (clientAddr == null);

        if (btGone && clientsGone) {
            Log.d(TAG, "All BT devices and clients disconnected, scheduling auto-stop in " + BT_AUTO_STOP_DELAY_MS + "ms");
            mainHandler.removeCallbacks(btAutoStopRunnable);
            mainHandler.postDelayed(btAutoStopRunnable, BT_AUTO_STOP_DELAY_MS);
        } else {
            Log.d(TAG, "Auto-stop not needed (BT connected: " + !btGone + ", clients connected: " + !clientsGone + ")");
        }
    }

    private void cancelBluetoothAutoStop() {
        Log.d(TAG, "Cancelling Bluetooth auto-stop");
        mainHandler.removeCallbacks(btAutoStopRunnable);
    }

    private void btAutoStopService() {
        // Safety net: re-check conditions before stopping
        boolean btGone = BluetoothReceiver.allTriggerDevicesDisconnected();
        boolean clientsGone = (clientAddr == null);
        if (!btGone || !clientsGone) {
            Log.i(TAG, "Bluetooth auto-stop skipped (BT connected: " + !btGone + ", clients connected: " + !clientsGone + ")");
            return;
        }
        Log.i(TAG, "Bluetooth auto-stop triggered - stopping service");
        setServiceEnabled(this, false);
        stopSelf();
    }

}

package cn.jystudio.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. Supports multiple simultaneous connections
 * keyed by device MAC address.
 */
public class BluetoothService {
    // Debugging
    private static final String TAG = "BluetoothService";
    private static final boolean DEBUG = true;

    // Maximum number of simultaneous connections allowed
    public static final int MAX_CONNECTIONS = 7;

    // Name for the SDP record when creating server socket
    private static final String NAME = "BTPrinter";
    // UUID must be this — Unique UUID for this application (SPP)
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private BluetoothAdapter mAdapter;

    // Connection pool: maps device address → ConnectedThread
    private final ConcurrentHashMap<String, ConnectedThread> mConnections = new ConcurrentHashMap<>();

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTING = 2;  // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;   // now connected to a remote device

    public static final int MESSAGE_STATE_CHANGE = 4;
    public static final int MESSAGE_READ = 5;
    public static final int MESSAGE_WRITE = 6;
    public static final int MESSAGE_DEVICE_NAME = 7;
    public static final int MESSAGE_CONNECTION_LOST = 8;
    public static final int MESSAGE_UNABLE_CONNECT = 9;

    // Key names received from the BluetoothService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String DEVICE_ADDRESS = "device_address";
    public static final String TOAST = "toast";

    public static String ErrorMessage = "No_Error_Message";

    private static final List<BluetoothServiceStateObserver> observers =
            Collections.synchronizedList(new ArrayList<BluetoothServiceStateObserver>());

    /**
     * Constructor. Prepares a new BTPrinter session.
     *
     * @param context The UI Activity Context
     */
    public BluetoothService(Context context) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void addStateObserver(BluetoothServiceStateObserver observer) {
        observers.add(observer);
    }

    public void removeStateObserver(BluetoothServiceStateObserver observer) {
        observers.remove(observer);
    }

    private String getStateName(int state) {
        switch (state) {
            case STATE_NONE:
                return "STATE_NONE";
            case STATE_CONNECTING:
                return "STATE_CONNECTING";
            case STATE_CONNECTED:
                return "STATE_CONNECTED";
            default:
                return "UNKNOWN:" + state;
        }
    }

    private synchronized void infoObservers(int code, Map<String, Object> bundle) {
        for (BluetoothServiceStateObserver ob : observers) {
            ob.onBluetoothServiceStateChanged(code, bundle);
        }
    }

    /**
     * Return the aggregate connection state.
     * Returns STATE_CONNECTED if any device is connected,
     * STATE_NONE otherwise.
     */
    public synchronized int getState() {
        for (ConnectedThread thread : mConnections.values()) {
            if (thread.isConnected()) {
                return STATE_CONNECTED;
            }
        }
        return STATE_NONE;
    }

    /**
     * Return the connection state for a specific device address.
     *
     * @param address The MAC address of the device
     */
    public synchronized int getState(String address) {
        if (address == null) {
            return getState();
        }
        ConnectedThread thread = mConnections.get(address);
        if (thread != null && thread.isConnected()) {
            return STATE_CONNECTED;
        }
        return STATE_NONE;
    }

    /**
     * Returns the number of active connections.
     */
    public int getConnectionCount() {
        int count = 0;
        for (ConnectedThread thread : mConnections.values()) {
            if (thread.isConnected()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Start the ConnectedThread to initiate a connection to a remote device.
     * Supports multiple simultaneous connections up to MAX_CONNECTIONS.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "connect() called with null device");
            return;
        }
        String address = device.getAddress();
        if (DEBUG) Log.d(TAG, "connect to: " + address);

        // Check if already connected to this device
        ConnectedThread existingThread = mConnections.get(address);
        if (existingThread != null && existingThread.isConnected()) {
            if (DEBUG) Log.d(TAG, "Already connected to " + address);
            Map<String, Object> bundle = new HashMap<>();
            bundle.put(DEVICE_NAME, device.getName());
            bundle.put(DEVICE_ADDRESS, address);
            infoObservers(STATE_CONNECTED, bundle);
            return;
        }

        // Check connection limit
        if (getConnectionCount() >= MAX_CONNECTIONS) {
            Log.e(TAG, "Maximum connections (" + MAX_CONNECTIONS + ") reached. Cannot connect to " + address);
            Map<String, Object> bundle = new HashMap<>();
            bundle.put(DEVICE_ADDRESS, address);
            infoObservers(MESSAGE_UNABLE_CONNECT, bundle);
            return;
        }

        // Cancel existing thread for this address if it exists (e.g., a stale/disconnected one)
        if (existingThread != null) {
            existingThread.cancel();
            mConnections.remove(address);
        }

        // Start the thread to manage the connection and perform transmissions
        ConnectedThread thread = new ConnectedThread(device);
        mConnections.put(address, thread);
        thread.start();

        Map<String, Object> bundle = new HashMap<>();
        bundle.put(DEVICE_ADDRESS, address);
        infoObservers(STATE_CONNECTING, bundle);
    }

    /**
     * Get the first connected device (backward compatibility).
     */
    public synchronized BluetoothDevice getConnectedDevice() {
        for (ConnectedThread thread : mConnections.values()) {
            BluetoothDevice device = thread.bluetoothDevice();
            if (device != null) {
                return device;
            }
        }
        return null;
    }

    /**
     * Get a specific connected device by address.
     *
     * @param address The MAC address of the device
     */
    public synchronized BluetoothDevice getConnectedDevice(String address) {
        if (address == null) {
            return getConnectedDevice();
        }
        ConnectedThread thread = mConnections.get(address);
        if (thread != null) {
            return thread.bluetoothDevice();
        }
        return null;
    }

    /**
     * Get all connected devices.
     */
    public synchronized List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        for (ConnectedThread thread : mConnections.values()) {
            BluetoothDevice device = thread.bluetoothDevice();
            if (device != null) {
                devices.add(device);
            }
        }
        return devices;
    }

    /**
     * Stop all threads / disconnect all devices.
     */
    public synchronized void stop() {
        for (Map.Entry<String, ConnectedThread> entry : mConnections.entrySet()) {
            entry.getValue().cancel();
        }
        mConnections.clear();
    }

    /**
     * Stop a specific connection by address.
     *
     * @param address The MAC address of the device to disconnect
     */
    public synchronized void stop(String address) {
        if (address == null) {
            stop();
            return;
        }
        ConnectedThread thread = mConnections.remove(address);
        if (thread != null) {
            thread.cancel();
        }
    }

    /**
     * Write to a specific connected device.
     *
     * @param out     The bytes to write
     * @param address The MAC address of the target device (null = first connected)
     */
    public void write(byte[] out, String address) {
        ConnectedThread r;
        synchronized (this) {
            if (address != null) {
                r = mConnections.get(address);
            } else {
                // Fallback: write to the first connected device
                r = getFirstConnectedThread();
            }
            if (r == null || !r.isConnected()) return;
        }
        r.write(out);
    }

    /**
     * Write to the first connected device (backward compatibility).
     *
     * @param out The bytes to write
     */
    public void write(byte[] out) {
        write(out, null);
    }

    /**
     * Returns the first connected thread, or null if none.
     */
    private ConnectedThread getFirstConnectedThread() {
        for (ConnectedThread thread : mConnections.values()) {
            if (thread.isConnected()) {
                return thread;
            }
        }
        return null;
    }

    /**
     * Indicate that the connection attempt failed for a specific device.
     */
    private void connectionFailed(String address) {
        Map<String, Object> bundle = new HashMap<>();
        if (address != null) {
            bundle.put(DEVICE_ADDRESS, address);
        }
        infoObservers(MESSAGE_UNABLE_CONNECT, bundle);

        // Clean up
        if (address != null) {
            mConnections.remove(address);
        }
    }

    /**
     * Indicate that the connection was lost for a specific device.
     */
    private void connectionLost(String address) {
        Map<String, Object> bundle = new HashMap<>();
        if (address != null) {
            bundle.put(DEVICE_ADDRESS, address);
        }
        infoObservers(MESSAGE_CONNECTION_LOST, bundle);

        // Clean up
        if (address != null) {
            mConnections.remove(address);
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothDevice mmDevice;
        private BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private OutputStream mmOutStream;
        private volatile boolean mmConnected = false;

        public ConnectedThread(BluetoothDevice device) {
            mmDevice = device;
            setName("ConnectedThread-" + device.getAddress());
        }

        public boolean isConnected() {
            return mmConnected && mmSocket != null && mmSocket.isConnected();
        }

        @Override
        public void run() {
            String address = mmDevice.getAddress();
            Log.i(TAG, "BEGIN ConnectedThread for " + address);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            BluetoothSocket tmp = null;

            // Try to connect with socket inner method firstly
            for (int i = 1; i <= 3; i++) {
                try {
                    tmp = (BluetoothSocket) mmDevice.getClass()
                            .getMethod("createRfcommSocket", int.class)
                            .invoke(mmDevice, i);
                } catch (Exception e) {
                    // ignore
                }
                if (tmp != null) {
                    mmSocket = tmp;
                    break;
                }
            }

            // Try with given UUID
            if (mmSocket == null) {
                try {
                    tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
                } catch (IOException e) {
                    Log.e(TAG, "create() failed", e);
                }
                if (tmp == null) {
                    Log.e(TAG, "create() failed: Socket NULL for " + address);
                    connectionFailed(address);
                    return;
                }
                mmSocket = tmp;
            }

            // Make a connection to the BluetoothSocket
            try {
                mmSocket.connect();
            } catch (Exception e) {
                Log.e(TAG, "connect() failed for " + address, e);
                connectionFailed(address);
                try {
                    mmSocket.close();
                } catch (Exception e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                return;
            }

            // Get the BluetoothSocket input and output streams
            Log.d(TAG, "create ConnectedThread streams for " + address);
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created for " + address, e);
                connectionFailed(address);
                return;
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mmConnected = true;

            Map<String, Object> bundle = new HashMap<>();
            bundle.put(DEVICE_NAME, mmDevice.getName());
            bundle.put(DEVICE_ADDRESS, address);
            infoObservers(STATE_CONNECTED, bundle);

            Log.i(TAG, "Connected to " + address);

            // Keep listening to the InputStream while connected
            while (mmConnected) {
                try {
                    byte[] buffer = new byte[256];
                    int bytes = mmInStream.read(buffer);
                    if (bytes > 0) {
                        bundle = new HashMap<>();
                        bundle.put("bytes", bytes);
                        bundle.put(DEVICE_ADDRESS, address);
                        infoObservers(MESSAGE_READ, bundle);
                    } else {
                        Log.e(TAG, "disconnected from " + address);
                        mmConnected = false;
                        connectionLost(address);
                        break;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "disconnected from " + address, e);
                    mmConnected = false;
                    connectionLost(address);
                    break;
                }
            }
            Log.i(TAG, "ConnectedThread End for " + address);
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
                mmOutStream.flush();
                Log.i("BTPWRITE", "Wrote " + buffer.length + " bytes to " + mmDevice.getAddress());
                Map<String, Object> bundle = new HashMap<>();
                bundle.put("bytes", buffer);
                bundle.put(DEVICE_ADDRESS, mmDevice.getAddress());
                infoObservers(MESSAGE_WRITE, bundle);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write to " + mmDevice.getAddress(), e);
            }
        }

        public BluetoothDevice bluetoothDevice() {
            if (mmSocket != null && mmSocket.isConnected()) {
                return mmSocket.getRemoteDevice();
            }
            return null;
        }

        public void cancel() {
            mmConnected = false;
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed for " + mmDevice.getAddress(), e);
            }
        }
    }
}

package cn.jystudio.bluetooth;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RNBluetoothManagerModule extends ReactContextBaseJavaModule
        implements ActivityEventListener, BluetoothServiceStateObserver {

    private static final String TAG = "BluetoothManager";
    private final ReactApplicationContext reactContext;
    public static final String EVENT_DEVICE_ALREADY_PAIRED = "EVENT_DEVICE_ALREADY_PAIRED";
    public static final String EVENT_DEVICE_FOUND = "EVENT_DEVICE_FOUND";
    public static final String EVENT_DEVICE_DISCOVER_DONE = "EVENT_DEVICE_DISCOVER_DONE";
    public static final String EVENT_CONNECTION_LOST = "EVENT_CONNECTION_LOST";
    public static final String EVENT_UNABLE_CONNECT = "EVENT_UNABLE_CONNECT";
    public static final String EVENT_CONNECTED = "EVENT_CONNECTED";
    public static final String EVENT_BLUETOOTH_NOT_SUPPORT = "EVENT_BLUETOOTH_NOT_SUPPORT";


    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    public static final int MESSAGE_STATE_CHANGE = BluetoothService.MESSAGE_STATE_CHANGE;
    public static final int MESSAGE_READ = BluetoothService.MESSAGE_READ;
    public static final int MESSAGE_WRITE = BluetoothService.MESSAGE_WRITE;
    public static final int MESSAGE_DEVICE_NAME = BluetoothService.MESSAGE_DEVICE_NAME;

    public static final int MESSAGE_CONNECTION_LOST = BluetoothService.MESSAGE_CONNECTION_LOST;
    public static final int MESSAGE_UNABLE_CONNECT = BluetoothService.MESSAGE_UNABLE_CONNECT;
    public static final String DEVICE_NAME = BluetoothService.DEVICE_NAME;
    public static final String DEVICE_ADDRESS = BluetoothService.DEVICE_ADDRESS;
    public static final String TOAST = BluetoothService.TOAST;

    // Return Intent extra
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    // Promise map now supports per-address connect promises via "CONNECT_<address>" keys
    private static final Map<String, Promise> promiseMap = Collections.synchronizedMap(new HashMap<String, Promise>());
    private static final String PROMISE_ENABLE_BT = "ENABLE_BT";
    private static final String PROMISE_SCAN = "SCAN";
    private static final String PROMISE_CONNECT_PREFIX = "CONNECT_";

    private JSONArray pairedDevice = new JSONArray();
    private JSONArray foundDevice = new JSONArray();
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the services
    private BluetoothService mService = null;

    public RNBluetoothManagerModule(ReactApplicationContext reactContext, BluetoothService bluetoothService) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addActivityEventListener(this);
        this.mService = bluetoothService;
        this.mService.addStateObserver(this);
        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.reactContext.registerReceiver(discoverReceiver, filter);
    }

    @Override
    public
    @Nullable
    Map<String, Object> getConstants() {
        Map<String, Object> constants = new HashMap<>();
        constants.put(EVENT_DEVICE_ALREADY_PAIRED, EVENT_DEVICE_ALREADY_PAIRED);
        constants.put(EVENT_DEVICE_DISCOVER_DONE, EVENT_DEVICE_DISCOVER_DONE);
        constants.put(EVENT_DEVICE_FOUND, EVENT_DEVICE_FOUND);
        constants.put(EVENT_CONNECTION_LOST, EVENT_CONNECTION_LOST);
        constants.put(EVENT_UNABLE_CONNECT, EVENT_UNABLE_CONNECT);
        constants.put(EVENT_CONNECTED, EVENT_CONNECTED);
        constants.put(EVENT_BLUETOOTH_NOT_SUPPORT, EVENT_BLUETOOTH_NOT_SUPPORT);
        constants.put(DEVICE_NAME, DEVICE_NAME);
        return constants;
    }

    private BluetoothAdapter getBluetoothAdapter() {
        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        if (mBluetoothAdapter == null) {
            emitRNEvent(EVENT_BLUETOOTH_NOT_SUPPORT, Arguments.createMap());
        }
        return mBluetoothAdapter;
    }


    @ReactMethod
    public void enableBluetooth(final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        if (adapter == null) {
            promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT);
        } else if (!adapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            promiseMap.put(PROMISE_ENABLE_BT, promise);
            this.reactContext.startActivityForResult(enableIntent, REQUEST_ENABLE_BT, Bundle.EMPTY);
        } else {
            WritableArray pairedDeviceArray = Arguments.createArray();
            Set<BluetoothDevice> boundDevices = adapter.getBondedDevices();
            for (BluetoothDevice d : boundDevices) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("name", d.getName());
                    obj.put("address", d.getAddress());
                    pairedDeviceArray.pushString(obj.toString());
                } catch (Exception e) {
                    //ignore
                }
            }
            Log.d(TAG, "Bluetooth enabled");
            promise.resolve(pairedDeviceArray);
        }
    }

    @ReactMethod
    public void disableBluetooth(final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        if (adapter == null) {
            promise.resolve(true);
        } else {
            if (mService != null) {
                mService.stop(); // Disconnect all devices
            }
            promise.resolve(!adapter.isEnabled() || adapter.disable());
        }
    }

    @ReactMethod
    public void isBluetoothEnabled(final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        promise.resolve(adapter != null && adapter.isEnabled());
    }

    @ReactMethod
    public void scanDevices(final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        if (adapter == null) {
            promise.reject(EVENT_BLUETOOTH_NOT_SUPPORT);
        } else {
            cancelDiscovery();
            int permissionChecked = ContextCompat.checkSelfPermission(reactContext, android.Manifest.permission.ACCESS_FINE_LOCATION);
            if (permissionChecked == PackageManager.PERMISSION_DENIED) {
                Activity activity = reactContext.getCurrentActivity();
                if (activity != null) {
                    ActivityCompat.requestPermissions(activity, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                }
            }

            // Check android 12 bluetooth permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                int bluetoothConnectPermission = ContextCompat.checkSelfPermission(reactContext, android.Manifest.permission.BLUETOOTH_CONNECT);
                if (bluetoothConnectPermission == PackageManager.PERMISSION_DENIED) {
                    Activity activity = reactContext.getCurrentActivity();
                    if (activity != null) {
                        ActivityCompat.requestPermissions(activity, new String[]{
                                android.Manifest.permission.BLUETOOTH_CONNECT,
                                android.Manifest.permission.BLUETOOTH_SCAN,
                        }, 1);
                    }
                }
            }

            pairedDevice = new JSONArray();
            foundDevice = new JSONArray();
            Set<BluetoothDevice> boundDevices = adapter.getBondedDevices();
            for (BluetoothDevice d : boundDevices) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("name", d.getName());
                    obj.put("address", d.getAddress());
                    pairedDevice.put(obj);
                } catch (Exception e) {
                    //ignore
                }
            }

            WritableMap params = Arguments.createMap();
            params.putString("devices", pairedDevice.toString());
            emitRNEvent(EVENT_DEVICE_ALREADY_PAIRED, params);
            if (!adapter.startDiscovery()) {
                promise.reject("DISCOVER", "NOT_STARTED");
                cancelDiscovery();
            } else {
                promiseMap.put(PROMISE_SCAN, promise);
            }
        }
    }

    @ReactMethod
    public void connect(String address, final Promise promise) {
        try {
            BluetoothAdapter adapter = this.getBluetoothAdapter();
            if (adapter != null && adapter.isEnabled()) {
                BluetoothDevice device = adapter.getRemoteDevice(address);
                promiseMap.put(PROMISE_CONNECT_PREFIX + address, promise);
                mService.connect(device);
            } else {
                promise.reject("BT NOT ENABLED");
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            promise.reject(e.getMessage());
        }
    }

    /**
     * Disconnect a specific device by address.
     * If address is null or empty, disconnects all devices.
     */
    @ReactMethod
    public void disconnect(@Nullable String address, final Promise promise) {
        try {
            if (mService != null) {
                if (address != null && !address.isEmpty()) {
                    mService.stop(address);
                } else {
                    mService.stop();
                }
            }
            promise.resolve(null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            promise.reject(e.getMessage());
        }
    }

    /**
     * Get all connected devices.
     */
    @ReactMethod
    public void getConnectedDevices(final Promise promise) {
        try {
            List<BluetoothDevice> devices = mService.getConnectedDevices();
            if (devices != null && !devices.isEmpty()) {
                WritableArray connectedDeviceArray = Arguments.createArray();
                for (BluetoothDevice device : devices) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", device.getName());
                    obj.put("address", device.getAddress());
                    connectedDeviceArray.pushString(obj.toString());
                }
                promise.resolve(connectedDeviceArray);
            } else {
                promise.resolve(Arguments.createArray());
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            promise.reject(e.getMessage());
        }
    }

    @ReactMethod
    public void unpair(String address, final Promise promise) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        if (adapter != null && adapter.isEnabled()) {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            // Also disconnect if currently connected
            if (mService != null && mService.getState(address) == BluetoothService.STATE_CONNECTED) {
                mService.stop(address);
            }
            this.unpairDevice(device);
            promise.resolve(address);
        } else {
            promise.reject("BT NOT ENABLED");
        }
    }

    private void unpairDevice(BluetoothDevice device) {
        try {
            Method m = device.getClass()
                    .getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void cancelDiscovery() {
        try {
            BluetoothAdapter adapter = this.getBluetoothAdapter();
            if (adapter != null && adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
            Log.d(TAG, "Discover canceled");
        } catch (Exception e) {
            //ignore
        }
    }


    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        BluetoothAdapter adapter = this.getBluetoothAdapter();
        Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE: {
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(EXTRA_DEVICE_ADDRESS);
                    if (adapter != null && BluetoothAdapter.checkBluetoothAddress(address)) {
                        BluetoothDevice device = adapter.getRemoteDevice(address);
                        mService.connect(device);
                    }
                }
                break;
            }
            case REQUEST_ENABLE_BT: {
                Promise promise = promiseMap.remove(PROMISE_ENABLE_BT);
                if (resultCode == Activity.RESULT_OK && promise != null) {
                    if (adapter != null) {
                        WritableArray pairedDeviceArray = Arguments.createArray();
                        Set<BluetoothDevice> boundDevices = adapter.getBondedDevices();
                        for (BluetoothDevice d : boundDevices) {
                            try {
                                JSONObject obj = new JSONObject();
                                obj.put("name", d.getName());
                                obj.put("address", d.getAddress());
                                pairedDeviceArray.pushString(obj.toString());
                            } catch (Exception e) {
                                //ignore
                            }
                        }
                        promise.resolve(pairedDeviceArray);
                    } else {
                        promise.resolve(null);
                    }
                } else {
                    Log.d(TAG, "BT not enabled");
                    if (promise != null) {
                        promise.reject("ERR", new Exception("BT NOT ENABLED"));
                    }
                }
                break;
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    @Override
    public String getName() {
        return "BluetoothManager";
    }


    private boolean objectFound(JSONObject obj) {
        boolean found = false;
        if (foundDevice.length() > 0) {
            for (int i = 0; i < foundDevice.length(); i++) {
                try {
                    String objAddress = obj.optString("address", "objAddress");
                    String dsAddress = ((JSONObject) foundDevice.get(i)).optString("address", "dsAddress");
                    if (objAddress.equalsIgnoreCase(dsAddress)) {
                        found = true;
                        break;
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return found;
    }

    // The BroadcastReceiver that listens for discovered devices
    private final BroadcastReceiver discoverReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "on receive:" + action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    JSONObject deviceFound = new JSONObject();
                    try {
                        deviceFound.put("name", device.getName());
                        deviceFound.put("address", device.getAddress());
                    } catch (Exception e) {
                        //ignore
                    }
                    if (!objectFound(deviceFound)) {
                        foundDevice.put(deviceFound);
                        WritableMap params = Arguments.createMap();
                        params.putString("device", deviceFound.toString());
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                .emit(EVENT_DEVICE_FOUND, params);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Promise promise = promiseMap.remove(PROMISE_SCAN);
                if (promise != null) {
                    try {
                        JSONObject result = new JSONObject();
                        result.put("paired", pairedDevice);
                        result.put("found", foundDevice);
                        promise.resolve(result.toString());
                    } catch (Exception e) {
                        //ignore
                    }
                    WritableMap params = Arguments.createMap();
                    params.putString("paired", pairedDevice.toString());
                    params.putString("found", foundDevice.toString());
                    emitRNEvent(EVENT_DEVICE_DISCOVER_DONE, params);
                }
            }
        }
    };

    private void emitRNEvent(String event, @Nullable WritableMap params) {
        getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(event, params);
    }

    @Override
    public void onBluetoothServiceStateChanged(int state, Map<String, Object> bundle) {
        Log.d(TAG, "on bluetoothServiceStateChanged:" + state + " bundle: " + bundle);
        String deviceAddress = bundle != null ? (String) bundle.get(DEVICE_ADDRESS) : null;
        String deviceName = bundle != null ? (String) bundle.get(DEVICE_NAME) : null;

        switch (state) {
            case BluetoothService.STATE_CONNECTED:
            case MESSAGE_DEVICE_NAME: {
                // Resolve the per-address connect promise
                String promiseKey = deviceAddress != null
                        ? PROMISE_CONNECT_PREFIX + deviceAddress
                        : null;
                Promise p = promiseKey != null ? promiseMap.remove(promiseKey) : null;

                if (p == null) {
                    Log.d(TAG, "No Promise found for " + deviceAddress);
                    WritableMap params = Arguments.createMap();
                    if (deviceName != null) params.putString(DEVICE_NAME, deviceName);
                    if (deviceAddress != null) params.putString(DEVICE_ADDRESS, deviceAddress);
                    emitRNEvent(EVENT_CONNECTED, params);
                } else {
                    Log.d(TAG, "Promise Resolve for " + deviceAddress);
                    WritableMap result = Arguments.createMap();
                    if (deviceName != null) result.putString(DEVICE_NAME, deviceName);
                    if (deviceAddress != null) result.putString(DEVICE_ADDRESS, deviceAddress);
                    p.resolve(result);
                }
                break;
            }
            case MESSAGE_CONNECTION_LOST: {
                WritableMap params = Arguments.createMap();
                if (deviceAddress != null) params.putString(DEVICE_ADDRESS, deviceAddress);
                if (deviceName != null) params.putString(DEVICE_NAME, deviceName);
                emitRNEvent(EVENT_CONNECTION_LOST, params);
                break;
            }
            case MESSAGE_UNABLE_CONNECT: {
                String promiseKey = deviceAddress != null
                        ? PROMISE_CONNECT_PREFIX + deviceAddress
                        : null;
                Promise p = promiseKey != null ? promiseMap.remove(promiseKey) : null;

                if (p == null) {
                    WritableMap params = Arguments.createMap();
                    if (deviceAddress != null) params.putString(DEVICE_ADDRESS, deviceAddress);
                    emitRNEvent(EVENT_UNABLE_CONNECT, params);
                } else {
                    p.reject("Unable to connect device" + (deviceAddress != null ? ": " + deviceAddress : ""));
                }
                break;
            }
            default:
                break;
        }
    }
}

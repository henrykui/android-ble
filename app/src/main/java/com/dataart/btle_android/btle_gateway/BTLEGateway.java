package com.dataart.btle_android.btle_gateway;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.dataart.android.devicehive.Command;
import com.dataart.android.devicehive.Notification;
import com.dataart.btle_android.devicehive.BTLEDeviceHive;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;

public class BTLEGateway {

    private BluetoothServer bluetoothServerGateway;

    public BTLEGateway(BluetoothServer bluetoothServer) {
        this.bluetoothServerGateway = bluetoothServer;
    }

    public void doCommand(Context context, final BTLEDeviceHive dh, Command command) {
        try {
            final String name = command.getCommand();
            final LeCommand leCommand = LeCommand.fromName(name);
            final HashMap<String, Object> params = (HashMap<String, Object>) command.getParameters();
            final String deviceUUID = (params != null) ? (String) params.get("device") : null;
            final String serviceUUID = (params != null) ? (String) params.get("serviceUUID") : null;
            final String characteristicUUID = (params != null) ? (String) params.get("characteristicUUID") : null;

            switch (leCommand) {
                case SCAN_START:
                    bluetoothServerGateway.scanStart(context);
                    break;
                case SCAN_STOP:
                    bluetoothServerGateway.scanStop();
                    sendStopResult(dh);
                    break;
                case SCAN:
                    bluetoothServerGateway.scanStart(context);
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            sendStopResult(dh);
                        }
                    }, BluetoothServer.COMMAND_SCAN_DEALY);
                    break;
                case GATT_PRIMARY:
                    final String json = new Gson().toJson(
                            bluetoothServerGateway.gattPrimary(deviceUUID));
                    sendNotification(dh, leCommand, json);
                    break;
                case GATT_CHARACTERISTICS:
                    bluetoothServerGateway.gattCharacteristics(deviceUUID, context, new GattCharacteristicCallBack() {
                        @Override
                        public void characteristicsList(ArrayList<BTLECharacteristic> characteristics) {
                            final String json = new Gson().toJson(characteristics);
                            sendNotification(dh, leCommand, json);
                        }

                        @Override
                        public void onRead(byte[] value) {
                        }
                    });
                    break;
                case GATT_READ:
                    bluetoothServerGateway.gattRead(context, deviceUUID, serviceUUID, characteristicUUID, new GattCharacteristicCallBack() {
                        @Override
                        public void characteristicsList(ArrayList<BTLECharacteristic> characteristics) {
                        }

                        @Override
                        public void onRead(byte[] value) {
                            final String sValue = Utils.printHexBinary(value);
                            final String json = new Gson().toJson(sValue);
                            sendNotification(dh, leCommand, json);
                        }
                    });
                    break;
                case GATT_WRITE:
                    final String sValue = (String) params.get("value");
                    final byte[] value = Utils.parseHexBinary(sValue);
                    bluetoothServerGateway.gattWrite(context, deviceUUID, serviceUUID, characteristicUUID, value, new GattCharacteristicCallBack() {
                        @Override
                        public void characteristicsList(ArrayList<BTLECharacteristic> characteristics) {
                        }

                        @Override
                        public void onWrite(int state) {
                            final String json = new Gson().toJson(state);
                            sendNotification(dh, leCommand, json);
                        }
                    });
                    break;
                case GATT_NOTIFICATION:
                    bluetoothServerGateway.gattNotifications(context, deviceUUID, serviceUUID, characteristicUUID, true, new GattCharacteristicCallBack() {
                        @Override
                        public void characteristicsList(ArrayList<BTLECharacteristic> characteristics) {
                        }

                        @Override
                        public void onRead(byte[] value) {
                            final String sValue = Utils.printHexBinary(value);
                            final String json = new Gson().toJson(sValue);
                            sendNotification(dh, leCommand, json);
                        }
                    });
                    break;
                case GATT_NOTIFICATION_STOP:
                    bluetoothServerGateway.gattNotifications(context, deviceUUID, serviceUUID, characteristicUUID, false, new GattCharacteristicCallBack() {
                        @Override
                        public void characteristicsList(ArrayList<BTLECharacteristic> characteristics) {
                        }

                        @Override
                        public void onRead(byte[] value) {
                            final String sValue = Utils.printHexBinary(value);
                            final String json = new Gson().toJson(sValue);
                            sendNotification(dh, leCommand, json);
                        }
                    });
                    break;
                case UNKNOWN:
                    return;
            }
        } catch (Throwable thr) {
            Log.e("TAG", "Error during handling");
            Notification notification = new Notification("Error", thr.getMessage());
            dh.sendNotification(notification);
        }
    }

    private void sendNotification(final BTLEDeviceHive dh, final LeCommand leCommand, final String json) {
        final Notification notification = new Notification(leCommand.getCommand(), json);
        dh.sendNotification(notification);
    }

    private void sendStopResult(BTLEDeviceHive dh) {
        final ArrayList<BTLEDevice> devices = bluetoothServerGateway.getDiscoveredDevices();
        final String json = new Gson().toJson(devices);

        final HashMap<String, String> result = new HashMap<String, String>();
        result.put("result", json);

        final Notification notification = new Notification("discoveredDevices", result);
        dh.sendNotification(notification);
    }

    public enum LeCommand {
        SCAN_START("scan/start"),
        SCAN_STOP("scan/stop"),
        SCAN("scan"),
        GATT_PRIMARY("gatt/primary"),
        GATT_CHARACTERISTICS("gatt/characteristics"),
        GATT_READ("gatt/read"),
        GATT_WRITE("gatt/write"),
        GATT_NOTIFICATION("gatt/notifications"),
        GATT_NOTIFICATION_STOP("gatt/notifications/stop"),
        UNKNOWN("unknown");

        private final String command;

        LeCommand(final String command) {
            this.command = command;
        }

        public static LeCommand fromName(final String name) {
            for (LeCommand leCommand : values()) {
                if (leCommand.command.equalsIgnoreCase(name)) {
                    return leCommand;
                }
            }
            return UNKNOWN;
        }

        public String getCommand() {
            return command;
        }
    }

}
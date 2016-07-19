package net.ralphpina.drawwithme;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringDef;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.UUID;

import static net.ralphpina.drawwithme.DrawingMqttClient.ConnectionStatus.DISCONNECTED;

public class DrawingMqttClient {

    private static final String TAG = "DrawingMqttClient";

    @Retention(RetentionPolicy.CLASS)
    @StringDef({ConnectionStatus.CONNECTED,
            ConnectionStatus.DISCONNECTED})
    public @interface ConnectionStatus {
        String CONNECTED    = "Connected";
        String DISCONNECTED = "Disconnected";
    }

    @Retention(RetentionPolicy.CLASS)
    @StringDef({DrawingAction.TOUCH_DOWN,
            DrawingAction.TOUCH_MOVE,
            DrawingAction.TOUCH_UP})
    public @interface DrawingAction {
        String TOUCH_DOWN = "touch_down";
        String TOUCH_MOVE = "touch_move";
        String TOUCH_UP   = "touch_up";
    }

    private static final String CLIENT_ID     = "client_id_pref";
    private final static String SERVER_URI    = "tcp://iot.eclipse.org:1883";
    private final static String STATUS_TOPIC  = "drawwithme/friends/status";
    private final static String DRAWING_TOPIC = "drawwithme/drawing/change";

    private final MqttAndroidClient  mqttAndroidClient;
    private final MqttStatusListener statusListener;
    private final SharedPreferences  preferences;

    private String             clientId;
    private MqttDrawerListener drawerListener;

    public DrawingMqttClient(Context context, final MqttStatusListener statusListener) {
        this.statusListener = statusListener;
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());

        mqttAndroidClient = new MqttAndroidClient(context.getApplicationContext(),
                                                  SERVER_URI,
                                                  getClientId());
        mqttAndroidClient.setCallback(getMqttCallback(statusListener));
    }

    public String getClientId() {
        if (clientId != null) {
            return clientId;
        }

        clientId = preferences.getString(CLIENT_ID,
                                         null);
        if (clientId == null) {
            // get new client id
            clientId = UUID.randomUUID()
                           .toString();
            preferences.edit()
                       .putString(CLIENT_ID,
                                  clientId)
                       .apply();
        }
        return clientId;
    }

    // ===== CONNECTING ============================================================================

    public void connect(MqttDrawerListener drawerListener) {
        try {
            if (mqttAndroidClient.isConnected()) {
                return;
            }
        } catch (NullPointerException|IllegalArgumentException ignore) {
        }

        this.drawerListener = drawerListener;
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setWill(STATUS_TOPIC,
                                   DISCONNECTED.getBytes(),
                                   0,
                                   false);

        try {
            Log.e(TAG,
                  "=== connect() ===");
            mqttAndroidClient.connect(mqttConnectOptions,
                                      null,
                                      new IMqttActionListener() {
                                          @Override
                                          public void onSuccess(IMqttToken asyncActionToken) {
                                              statusListener.onConnect();
                                              Log.e(TAG,
                                                    "=== mqttAndroidClient.connect() onSuccess === ");
                                              subscribeToTopics();
                                          }

                                          @Override
                                          public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                              statusListener.onDisconnect();
                                          }
                                      });
        } catch (MqttException ex) {
            Log.e(TAG,
                  STATUS_TOPIC + "connect failure = " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void disconnect() {
        if (!mqttAndroidClient.isConnected()) {
            return;
        }
        try {
            publishConnectedStatus(DISCONNECTED);
            mqttAndroidClient.disconnect();
        } catch (MqttException e) {
            Log.e(TAG,
                  "=== disconnect === " + e.getMessage());
            e.printStackTrace();
        }
        drawerListener = null;
    }

    public void subscribeToTopics() {
        try {
            mqttAndroidClient.subscribe(STATUS_TOPIC,
                                        0,
                                        null,
                                        new IMqttActionListener() {
                                            @Override
                                            public void onSuccess(IMqttToken asyncActionToken) {
                                                Log.e(TAG,
                                                      STATUS_TOPIC
                                                      + " subscription === onSuccess() ===");
                                            }

                                            @Override
                                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                                Log.e(TAG,
                                                      STATUS_TOPIC
                                                      + "subscription === onFailure() === "
                                                      + exception.getMessage());
                                                exception.printStackTrace();
                                            }
                                        });
        } catch (MqttException ex) {
            Log.e(TAG,
                  "=== subscribe to status () === exception message = " + ex.getMessage());
            ex.printStackTrace();
        }

        try {
            mqttAndroidClient.subscribe(DRAWING_TOPIC,
                                        0,
                                        null,
                                        new IMqttActionListener() {
                                            @Override
                                            public void onSuccess(IMqttToken asyncActionToken) {
                                                Log.e(TAG,
                                                      DRAWING_TOPIC
                                                      + " subscription === onSuccess() ===");
                                            }

                                            @Override
                                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                                Log.e(TAG,
                                                      DRAWING_TOPIC
                                                      + "subscription === onFailure() === "
                                                      + exception.getMessage());
                                                exception.printStackTrace();
                                            }
                                        });
        } catch (MqttException ex) {
            Log.e(TAG,
                  "=== subscribe to drawing () === exception message = " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ===== PUBLISHING ============================================================================

    public void publishConnectedStatus(@ConnectionStatus String status) {
        Log.e(TAG,
              "=== publishConnectedStatus() === status = " + status);
        String payload = encodeConnectionStatus(status);
        publish(STATUS_TOPIC,
                payload);
    }

    public void publishDrawingAction(@DrawingAction String action, float x, float y) {
        String payload = encodeDrawingAction(action,
                                             x,
                                             y);
        publish(DRAWING_TOPIC,
                payload);
    }

    private void publish(String topic, String payload) {
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(payload.getBytes());
            message.setRetained(true);
            mqttAndroidClient.publish(topic,
                                      message);
            if (!mqttAndroidClient.isConnected()) {
                statusListener.onDisconnect();
            }
        } catch (MqttException e) {
            Log.e(TAG,
                  "Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String encodeConnectionStatus(@ConnectionStatus String status) {
        return clientId + "%2B" + statusListener.getUserName() + "%2B" + status;
    }

    private String[] parseConnectionStatus(String payload) {
        return payload.split("%2B");
    }

    private String encodeDrawingAction(@DrawingAction String action, float x, float y) {
        return clientId + "%23" + action + "%23" + Float.toString(x) + "%23" + Float.toString(y);
    }

    private String[] parseDrawingAction(String payload) {
        return payload.split("%23");
    }

    // ===== CALL BACK =============================================================================

    @NonNull
    private MqttCallbackExtended getMqttCallback(final MqttStatusListener listener) {
        return new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                listener.onConnect();
                Log.e(TAG,
                      "=== getMqttCallback() connectComplete === reconnect = " + reconnect);
                if (reconnect) {
                    // Because Clean Session is true, we need to re-subscribe
                    subscribeToTopics();
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                listener.onDisconnect();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                if (STATUS_TOPIC.equals(topic)) {
                    final String[] payload = parseConnectionStatus(new String(message.getPayload()));
                    listener.onUserConnection(payload[1],
                                              payload[2]);
                } else if (DRAWING_TOPIC.equals(topic)) {
                    final String[] payload = parseDrawingAction(new String(message.getPayload()));
                    if (payload[0].equals(clientId)) {
                        return;
                    }
                    if (DrawingAction.TOUCH_DOWN.equals(payload[1])) {
                        drawerListener.touchDown(payload[0],
                                                 Float.parseFloat(payload[2]),
                                                 Float.parseFloat(payload[3]));
                    } else if (DrawingAction.TOUCH_MOVE.equals(payload[1])) {
                        drawerListener.touchMove(payload[0],
                                                 Float.parseFloat(payload[2]),
                                                 Float.parseFloat(payload[3]));
                    } else if (DrawingAction.TOUCH_UP.equals(payload[1])) {
                        drawerListener.touchUp(payload[0]);
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // noop
            }
        };
    }

    public interface MqttStatusListener {
        void onConnect();

        void onDisconnect();

        void onUserConnection(String user, String connectedStatus);

        String getUserName();
    }

    public interface MqttDrawerListener {
        void touchDown(String userId, float x, float y);

        void touchMove(String userId, float x, float y);

        void touchUp(String userId);
    }
}

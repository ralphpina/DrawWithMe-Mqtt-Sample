package net.ralphpina.drawwithme;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import net.ralphpina.drawwithme.ProtobufMessages.DrawAction;
import net.ralphpina.drawwithme.ProtobufMessages.Presence;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.UUID;

import static net.ralphpina.drawwithme.ProtobufMessages.DrawAction.TOUCH_DOWN;
import static net.ralphpina.drawwithme.ProtobufMessages.DrawAction.TOUCH_MOVE;
import static net.ralphpina.drawwithme.ProtobufMessages.DrawAction.TOUCH_UP;
import static net.ralphpina.drawwithme.ProtobufMessages.Presence.DISCONNECTED;

public class DrawingMqttClient {

    private static final String TAG = "DrawingMqttClient";

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
        } catch (NullPointerException | IllegalArgumentException ignore) {
        }

        this.drawerListener = drawerListener;
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        Presence presence = new Presence();
        presence.clientId = clientId;
        presence.userName = statusListener.getUserName();
        presence.activeStatus = DISCONNECTED;

        mqttConnectOptions.setWill(STATUS_TOPIC,
                                   Presence.toByteArray(presence),
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

    public void publishConnectedStatus(int activeStatus) {
        Presence presence = new Presence();
        presence.clientId = clientId;
        presence.userName = statusListener.getUserName();
        presence.activeStatus = activeStatus;
        publish(STATUS_TOPIC,
                Presence.toByteArray(presence));
    }

    public void publishDrawingAction(int action, float x, float y) {
        DrawAction drawAction = new DrawAction();
        drawAction.clientId = clientId;
        drawAction.drawingAction = action;
        drawAction.x = x;
        drawAction.y = y;

        publish(DRAWING_TOPIC,
                DrawAction.toByteArray(drawAction));
    }

    private void publish(String topic, byte[] payload) {
        try {
            mqttAndroidClient.publish(topic,
                                      payload,
                                      0,
                                      true);
            if (!mqttAndroidClient.isConnected()) {
                statusListener.onDisconnect();
            }
        } catch (MqttException e) {
            Log.e(TAG,
                  "Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== CALL BACK =============================================================================

    @NonNull
    private MqttCallbackExtended getMqttCallback(final MqttStatusListener listener) {
        return new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                listener.onConnect();
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
                    Presence presence = Presence.parseFrom(message.getPayload());
                    listener.onUserConnection(presence.userName,
                                              presence.activeStatus);
                } else if (DRAWING_TOPIC.equals(topic)) {

                    DrawAction drawAction = DrawAction.parseFrom(message.getPayload());
                    if (drawAction.clientId.equals(clientId)) {
                        return;
                    }
                    switch (drawAction.drawingAction) {
                        case TOUCH_DOWN:
                            drawerListener.touchDown(drawAction.clientId,
                                                     drawAction.x,
                                                     drawAction.y);
                            break;
                        case TOUCH_MOVE:
                            drawerListener.touchMove(drawAction.clientId,
                                                     drawAction.x,
                                                     drawAction.y);
                            break;
                        case TOUCH_UP:
                            drawerListener.touchUp(drawAction.clientId);
                            break;
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

        void onUserConnection(String user, int connectedStatus);

        String getUserName();
    }

    public interface MqttDrawerListener {
        void touchDown(String userId, float x, float y);

        void touchMove(String userId, float x, float y);

        void touchUp(String userId);
    }
}

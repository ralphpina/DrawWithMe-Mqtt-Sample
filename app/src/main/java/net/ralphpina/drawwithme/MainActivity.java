package net.ralphpina.drawwithme;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.UUID;

import static net.ralphpina.drawwithme.DrawingMqttClient.ConnectionStatus.CONNECTED;
import static net.ralphpina.drawwithme.DrawingMqttClient.ConnectionStatus.DISCONNECTED;

public class MainActivity extends AppCompatActivity implements DrawingMqttClient.MqttStatusListener {

    private static final String TAG = "DrawingActivity";

    private AlertDialog dialog;
    private String name            = "";
    private String connectedStatus = "";

    private DrawingView       drawingView;
    private TextView          nameAndStatus;
    private HistoryAdapter    adapter;
    private DrawingMqttClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        client = new DrawingMqttClient(this,
                                       this);

        drawingView = (DrawingView) findViewById(R.id.drawing_view);
        nameAndStatus = (TextView) findViewById(R.id.name_status);

        final RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new HistoryAdapter(new ArrayList<String>());
        recyclerView.setAdapter(adapter);

        drawingView.setMqttClient(client);

        enterNameDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!"".equals(name)) {
            client.connect(drawingView);
        }
    }

    @Override
    protected void onPause() {
        client.disconnect();
        super.onPause();
    }

    private void enterNameDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final EditText editText = new EditText(this);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                      .setEnabled(charSequence.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        builder.setTitle("Enter Your Name");
        builder.setMessage("Let your friends know who you are!");
        builder.setView(editText);
        builder.setPositiveButton(android.R.string.ok,
                                  new DialogInterface.OnClickListener() {
                                      public void onClick(DialogInterface dialog, int whichButton) {
                                          name = editText.getText()
                                                         .toString();
                                          updateConnectedStatus();
                                          client.connect(drawingView);
                                      }
                                  });

        builder.setCancelable(false);
        dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
              .setEnabled(false);
    }

    @SuppressLint("SetTextI18n")
    private void updateConnectedStatus() {
        nameAndStatus.setText(name + " - " + connectedStatus);
    }

    // ==== Mqtt Aware =============================================================================

    @Override
    public void onConnect() {
        if (!CONNECTED.equals(connectedStatus)) {
            connectedStatus = CONNECTED;
            client.publishConnectedStatus(CONNECTED);
            updateConnectedStatus();
        }
    }

    @Override
    public void onDisconnect() {
        if (!DISCONNECTED.equals(connectedStatus)) {
            connectedStatus = DISCONNECTED;
            updateConnectedStatus();
        }
    }

    @Override
    public void onUserConnection(String user, @DrawingMqttClient.ConnectionStatus String connectedStatus) {
        Log.e(TAG,
              "=== onUserConnected === user = " + user + " connectionStatus = " + connectedStatus);
        adapter.add(user + " - " + connectedStatus);
    }

    @Override
    public String getUserName() {
        return name;
    }
}

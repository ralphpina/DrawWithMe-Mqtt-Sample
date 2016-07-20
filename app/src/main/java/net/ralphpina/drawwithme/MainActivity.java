package net.ralphpina.drawwithme;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements DrawingMqttClient.MqttStatusListener {

    private static final String CONNECTED    = "Connected";
    private static final String DISCONNECTED = "Disonnected";

    private AlertDialog dialog;
    private String name            = "";
    private String connectedStatus = "";

    private DrawingView       drawingView;
    private RecyclerView      recyclerView;
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

        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);

        drawingView.setMqttClient(client);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!"".equals(name)) {
            client.connect(drawingView);
        } else {
            enterNameDialog();
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
            client.publishConnectedStatus(ProtobufMessages.Presence.CONNECTED);
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
    public void onUserConnection(String user, int connectedStatus) {
        adapter.add(
                user + " - " + (connectedStatus == ProtobufMessages.Presence.CONNECTED ? CONNECTED
                                                                                       : DISCONNECTED));
        recyclerView.scrollToPosition(adapter.getItemCount() - 1);

    }

    @Override
    public String getUserName() {
        return name;
    }
}

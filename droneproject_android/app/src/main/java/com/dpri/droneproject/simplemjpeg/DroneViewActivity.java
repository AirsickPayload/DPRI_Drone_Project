package com.dpri.droneproject.simplemjpeg;

/**
 * Created by alan on 23.09.15.
 */

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.dpri.droneproject.simplemjpeg.inputmanagercompat.InputManagerCompat;
import com.dpri.droneproject.simplemjpeg.inputmanagercompat.InputManagerCompat.InputDeviceListener;
import com.dpri.droneproject.simplemjpeg.socketPackage.DroneSocketClient;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.net.URI;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DroneViewActivity extends Activity implements InputDeviceListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private static final boolean DEBUG = true;
    private static final String TAG = "DroneActivity";

    private InputManagerCompat mInputManager;
    private List<Integer> gamepadIds;
    private NumberFormat mNumberFormatter;
    private DroneSocketClient droneSocketClient;

    private DroneValues droneValues;
    private MjpegView mjpegView = null;
    final Handler handler = new Handler();
    private boolean streamRunning = false, socketConnection = false, connectionError = false;

    private TextView inputTextView;
    private TextView yawTxtV, throttleTxtV, rollTxtV, pitchTxtV;
    private ImageButton settingsButton;
    private Button streamButton, socketButton;

    private String urlStream, droneIP;
    private LinkedList<String> asyncValuesQueue;
    private final int serverPort = 8887;
    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.drone_input_window);

        initializeUIelements();

        mInputManager = InputManagerCompat.Factory.getInputManager(this.getBaseContext());
        mInputManager.registerInputDeviceListener(this, null);

        droneValues = new DroneValues();
        gamepadIds = findControllers();

        mNumberFormatter = NumberFormat.getIntegerInstance();
        sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        initializeSettingsValues();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    void initializeUIelements() {
        inputTextView = (TextView) findViewById(R.id.inputTextView);
        inputTextView.setMovementMethod(new ScrollingMovementMethod());
        yawTxtV = (TextView) findViewById(R.id.yawTextView);
        yawTxtV.setText("0%");
        throttleTxtV = (TextView) findViewById(R.id.throttleTextView);
        throttleTxtV.setText("0%");
        rollTxtV = (TextView) findViewById(R.id.rollTextView);
        rollTxtV.setText("0%");
        pitchTxtV = (TextView) findViewById(R.id.pitchTextView);
        pitchTxtV.setText("0%");
        settingsButton = (ImageButton) findViewById(R.id.settingsButton);
        //Dodanie obsługi otwarcia Okna Ustawień
        settingsButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent i = new Intent(DroneViewActivity.this, DroneSettingsActivity.class);
                startActivity(i);
            }
        });
        //Dodanie obsługi rozpoczęcia i zatrzymania streamingu
        streamButton = (Button) findViewById(R.id.streamButton);
        streamButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if (!streamRunning) {
                    //droneSocketClient = new DroneSocketClient(droneIP, serverPort);
                    mjpegView = (MjpegView) findViewById(R.id.mjpegView);
                    if (mjpegView != null) {
                        int width = Integer.parseInt(sharedPref.getString("stream_width", "320"));
                        int height = Integer.parseInt(sharedPref.getString("stream_height", "240"));
                        mjpegView.setResolution(width, height);
                    }
                    setTitle(R.string.title_connecting);
                    new DoRead().execute(urlStream);
                    streamButton.setText("StopStream");
                    streamRunning = true;
                } else {
                    mjpegView.stopPlayback();
                    mjpegView.freeCameraMemory();
                    streamButton.setText("StartStream");
                    streamRunning = false;
                }
            }
        });
        // Obsługa łącznia się ze zdalnym serwerem
        socketButton = (Button) findViewById(R.id.socketButton);
        socketButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                connectionInitHandler();
            }
        });
    }

    void connectionInitHandler(){
        if (!socketConnection) {
            new AsyncSocketSonnect().execute();
        } else {
            socketConnection = false;
            droneSocketClient.closeConnection();
            socketButton.setText("Disconnected");
            socketButton.setTextColor(Color.YELLOW);
        }
    }


    void initializeSettingsValues(){
        droneIP = sharedPref.getString("ip_drona", "");
        urlStream = sharedPref.getString("url_stream", "");
        droneValues.setThrottlePin(Integer.parseInt(sharedPref.getString("pin_throttle", "0")));
        droneValues.setYawPin(Integer.parseInt(sharedPref.getString("pin_yaw", "1")));
        droneValues.setPitchPin(Integer.parseInt(sharedPref.getString("pin_pitch", "2")));
        droneValues.setRollPin(Integer.parseInt(sharedPref.getString("pin_roll", "6")));
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    List<Integer> findControllers() {
        int[] deviceIds = mInputManager.getInputDeviceIds();
        List<Integer> gamepads = new ArrayList<Integer>();
        for (int deviceId : deviceIds) {
            // Sprawdzamy istnienie urządzenia w dotychczasowej liscie
            if (gamepads.contains(deviceId)) {
                continue;
            }

            InputDevice dev = mInputManager.getInputDevice(deviceId);
            int sources = dev.getSources();
            if (((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) ||
                    ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
                gamepads.add(deviceId);
                inputTextView.append(System.getProperty("line.separator") + "ID:" + Integer.toString(deviceId) + " TO GAMEPAD/JOYSTICK!");
            }
        }
        return gamepads;
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public boolean onGenericMotionEvent(MotionEvent event) {

        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) ==
                InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {

            final int historySize = event.getHistorySize();

            // Process the movements starting from the
            // earliest historical position in the batch
            for (int i = 0; i < historySize; i++) {
                // Process the event at historical position i
                processJoystickInput(event, i);
            }

            // Process the current movement sample in the batch (position -1)
            processJoystickInput(event, -1);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private void processJoystickInput(MotionEvent event,
                                      int historyPos) {

/*      LEWY STICK

        PIONOWO (THROTTLE):
        MIN WARTOSC AXIS_Y (-1, GÓRA) / MAX WARTOSC (1, DÓŁ)

        POZIOMO (YAW):
        MIN WARTOSC AXIS_X (-1, LEWO) / MAX WARTOSC (1, PRAWO)

        PRAWY STICK

        PIONOWO (PITCH):
        MIN WARTOSC AXIS_RZ (-1, GÓRA) / MAX WARTOSC (1,DÓŁ)

        POZIOMO (ROLL):
        MIN WARTOSC AXIS_Z (-1, LEWO) / MAX WARTOSC (1, PRAWO)*/

        InputDevice mInputDevice = event.getDevice();
        // inputTextView.append(System.getProperty("line.separator") + "ID: " + mInputDevice.getId() + " - ");

        // OBSLUGA LEWEGO SITCKA
        float throttle = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Y, historyPos);
        throttle *= 100;

        float yaw = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_X, historyPos);
        yaw *= 100;

        // OBLSUGA PRAWEGO STICKA

        float pitch = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_RZ, historyPos);
        pitch *= 100;

        float roll = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Z, historyPos);
        roll *= 100;

        // OBSLUGA D-PADA
        float dPAD;
        boolean czyDpad = false;
        String dPadStr = "";

        dPAD = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_HAT_X, historyPos);

        if (dPAD == -1.0) {
            dPadStr = " D-PAD LEWY";
            czyDpad = true;
        }
        if (dPAD == 1.0) {
            dPadStr = " D-PAD PRAWY // OBSŁUGA POŁĄCZENIA Z SERWEREM";
            connectionInitHandler();
            czyDpad = true;
        }

        dPAD = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_HAT_Y, historyPos);

        if (dPAD == -1.0) {
            dPadStr = " D-PAD GÓRA // INICJALIZACJA FLIGHT CONTROLLERA";
            if(socketConnection) {
                droneValues.setInitializationValues();
                asyncValuesQueue.addLast(droneValues.getValuesSocketString());
            }
            czyDpad = true;
        }
        if (dPAD == 1.0) {
            dPadStr = " D-PAD DÓŁ // RESET WARTOŚCI";
            if(socketConnection) {
                droneValues = new DroneValues();
                asyncValuesQueue.addLast(droneValues.getValuesSocketString());
            }
            czyDpad = true;
        }

        boolean significantChange = false;
        int floatToInt;
        if (!czyDpad) {
            if (Math.abs(droneValues.getThrottle() - (droneValues.getCalibratedThrottleValue(throttle))) >= 1) {
                floatToInt = Integer.parseInt(mNumberFormatter.format(throttle));
                droneValues.setThrottle(floatToInt);
                significantChange = true;
                throttleTxtV.setText(mNumberFormatter.format(droneValues.getThrottle()) + "%");
            }

            if (Math.abs(droneValues.getYaw() - (droneValues.getCalibratedHorizontalGenericValue(yaw))) >= 1) {
                floatToInt = Integer.parseInt(mNumberFormatter.format(yaw));
                droneValues.setYaw(floatToInt);
                significantChange = true;
                yawTxtV.setText(mNumberFormatter.format(droneValues.getYaw()) + "%");
            }

            if (Math.abs(droneValues.getPitch() - (droneValues.getCalibratedVerticalGenericValue(pitch))) >= 1) {
                floatToInt = Integer.parseInt(mNumberFormatter.format(pitch));
                droneValues.setPitch(floatToInt);
                significantChange = true;
                pitchTxtV.setText(mNumberFormatter.format(droneValues.getPitch()) + "%");
            }

            if (Math.abs(droneValues.getRoll() - (droneValues.getCalibratedHorizontalGenericValue(roll))) >= 1) {
                floatToInt = Integer.parseInt(mNumberFormatter.format(roll));
                droneValues.setRoll(floatToInt);
                significantChange = true;
                rollTxtV.setText(mNumberFormatter.format(droneValues.getRoll()) + "%");
            }
        } else {
            inputTextView.append(System.getProperty("line.separator") + dPadStr);
        }

        if(socketConnection && significantChange){
            asyncValuesQueue.addLast(droneValues.getValuesSocketString());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) ||
                ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {

            // TODO: Dodać zdarzenia na klawiszach + poprawić mapowanie.
            inputTextView.append(System.getProperty("line.separator") + "KEYEVENT: ");
            switch (keyCode) {
                case KeyEvent.KEYCODE_BUTTON_X:
                    inputTextView.append(" X(lewy)");
                    return true;
                case KeyEvent.KEYCODE_BUTTON_A:
                    inputTextView.append(" A(dół)");
                    return true;
                case KeyEvent.KEYCODE_BUTTON_B:
                    inputTextView.append(" B(prawy)");
                    return true;
                case KeyEvent.KEYCODE_BUTTON_Y:
                    inputTextView.append(" Y(góra)");
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    inputTextView.append(" R1");
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    inputTextView.append(" L1");
                    return true;

            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    private static float getCenteredAxis(MotionEvent event,
                                         InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            final float value =
                    historyPos < 0 ? event.getAxisValue(axis) :
                            event.getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        inputTextView.append(System.getProperty("line.separator") + "NOWE URZĄDZENIE O ID: " + Integer.toString(deviceId));
        gamepadIds = findControllers();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        gamepadIds = findControllers();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        inputTextView.append(System.getProperty("line.separator") + "USUNIĘTO URZĄDZENIE O ID: " + Integer.toString(deviceId));
        gamepadIds = findControllers();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("ip_drona")) {
            droneIP = sharedPreferences.getString("ip_drona","");
            inputTextView.append(System.getProperty("line.separator") + "Zmiana IP na: " + droneIP);
        }
        if (key.equals("url_stream")) {
            urlStream = sharedPreferences.getString("url_stream", "");
            inputTextView.append(System.getProperty("line.separator") + "Zmiana URL streamu na: " + urlStream);
        }
        if(key.equals("pin_throttle")){
            droneValues.setThrottlePin(Integer.parseInt(sharedPref.getString("pin_throttle", "0")));
        }
        if(key.equals("pin_yaw")){
            droneValues.setYawPin(Integer.parseInt(sharedPref.getString("pin_yaw", "1")));
        }
        if(key.equals("pin_pitch")){
            droneValues.setPitchPin(Integer.parseInt(sharedPref.getString("pin_pitch", "2")));
        }
        if(key.equals("pin_roll")){
            droneValues.setRollPin(Integer.parseInt(sharedPref.getString("pin_roll", "6")));
        }
    }

    public void onPause() {
        if (DEBUG) Log.d(TAG, "onPause()");
        super.onPause();
        if (mjpegView != null) {
            if (mjpegView.isStreaming()) {
                mjpegView.stopPlayback();
            }
        }
    }

    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy()");

        if (mjpegView != null) {
            mjpegView.freeCameraMemory();
        }

        super.onDestroy();
    }

    public void onStop() {
        if (DEBUG) Log.d(TAG, "onStop()");
        super.onStop();
    }

    public void setImageError() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                setTitle(R.string.title_imageerror);
                return;
            }
        });
    }

    private class AsyncSocketSonnect extends AsyncTask<Void, Void, Boolean>{

        @Override
        protected Boolean doInBackground(Void... params) {
            if(connectionError) { droneSocketClient.closeSocket(); connectionError = false; }
            droneSocketClient = new DroneSocketClient(droneIP, serverPort);
            return droneSocketClient.confirmVersionCompability();
        }

        @Override
        protected void onPostExecute(Boolean result){
            if(result){
                asyncValuesQueue = new LinkedList<String>();
                socketConnection = true;
                new AsyncSocketSend().execute();
                socketButton.setText("Connected!");
                socketButton.setTextColor(Color.GREEN);
            } else {
                socketButton.setText("CONN_ERROR!");
                socketButton.setTextColor(Color.RED);
            }
        }
    }

    private void dropElementsInQueue(LinkedList<String> queue, int howMany){
        for(int i=0; i<howMany; i++){
            queue.removeFirst();
        }
    }

    private void removeExcessiveValues(LinkedList<String> queue){
        int qSize = queue.size();
        if(qSize >= 75){
            dropElementsInQueue(queue, 10);
        }else if(qSize >= 50){
            dropElementsInQueue(queue, 5);
        }else if(qSize >= 25){
            dropElementsInQueue(queue, 2);
        }
    }

    private class AsyncSocketSend extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... params) {
            while (true) {
                if(!socketConnection) { break; }

                //Przerzedzanie kolejki, gdy wartości jest zbyt wiele
                removeExcessiveValues(asyncValuesQueue);

                try {
                    String peekValue = asyncValuesQueue.peekFirst();
                    if (peekValue != null) {
                        if(!droneSocketClient.sendValues(asyncValuesQueue.removeFirst())){
                            socketConnection = false;
                            connectionError = true;
                            break;
                        }
                    }
                } catch (NullPointerException e){
                    continue;
                }
            }
            return null;
        }

        protected void onPostExecute(Void result){
            if(!connectionError) {
                inputTextView.append(System.getProperty("line.separator") + "Zakończono wysyłanie w tle");
            }else {
                inputTextView.append(System.getProperty("line.separator") + "Błąd podczas wysyłania wartości! Próbuję wznowić połączenie...");
                socketButton.setText("ERROR");
                socketButton.setTextColor(Color.RED);
                new AsyncSocketSonnect().execute();
            }
        }
    }

    public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {
        protected MjpegInputStream doInBackground(String... url) {
            HttpResponse res = null;
            DefaultHttpClient httpclient = new DefaultHttpClient();
            HttpParams httpParams = httpclient.getParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, 5 * 1000);
            HttpConnectionParams.setSoTimeout(httpParams, 5 * 1000);
            if (DEBUG) Log.d(TAG, "1. Sending http request");
            try {
                res = httpclient.execute(new HttpGet(URI.create(url[0])));
                if (DEBUG)
                    Log.d(TAG, "2. Request finished, status = " + res.getStatusLine().getStatusCode());
                if (res.getStatusLine().getStatusCode() == 401) {
                    //You must turn off camera User Access Control before this will work
                    return null;
                }
                return new MjpegInputStream(res.getEntity().getContent());
            } catch (ClientProtocolException e) {
                if (DEBUG) {
                    e.printStackTrace();
                    Log.d(TAG, "Request failed-ClientProtocolException", e);
                }
                //Error connecting to camera
            } catch (IOException e) {
                if (DEBUG) {
                    e.printStackTrace();
                    Log.d(TAG, "Request failed-IOException", e);
                }
                //Error connecting to camera
            }
            return null;
        }

        protected void onPostExecute(MjpegInputStream result) {
            mjpegView.setSource(result);
            if (result != null) {
                result.setSkip(1);
                setTitle(R.string.app_name);
            } else {
                setTitle(R.string.title_disconnected);
            }
            mjpegView.setDisplayMode(MjpegView.SIZE_BEST_FIT);
            mjpegView.showFps(false);
        }
    }

    public class RestartApp extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... v) {
            DroneViewActivity.this.finish();
            return null;
        }

        protected void onPostExecute(Void v) {
            startActivity((new Intent(DroneViewActivity.this, DroneViewActivity.class)));
        }
    }
}

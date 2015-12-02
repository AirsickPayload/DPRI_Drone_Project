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
import android.view.Window;
import android.view.WindowManager;
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
    /*
        Wartość boolean socketConnection wykorzystywana w obsłudze inicjalizacji połączenia (metoda void connectionInitHandler() ),
        podczas wysyłania wartości( metoda private void processJoystickInput(...) ) oraz dodatkowo w obsłudze błędów połączenia
        (podklasy wątku asynchronicznego AsyncServerPingListener oraz AsyncSocketSend).

        Boolean connectionError wykorzystywany wyłącznie do obłsugi błędów połączenia(podklasy wątków
        AsyncSocketConnect, AsyncServerPingListener oraz AsyncSocketSend.
     */
    private boolean streamRunning = false, socketConnection = false, connectionError = false;

    private TextView inputTextView;
    private TextView yawTxtV, throttleTxtV, rollTxtV, pitchTxtV;
    private ImageButton settingsButton;
    private Button streamButton, socketButton;

    private String urlStream, droneIP;
    private LinkedList<String> asyncValuesQueue;
    private static final int serverPort = 8887;
    private static final int pingPort = 8889;
    private static final int clientPort = 8888;
    // Obiekt służący do pobierania preferencji/ustawień i nasłuchiwania zmian.
    private SharedPreferences sharedPref;

    private int queue50, queue25, queue10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Usunięcie belki z informacją o nazwie aplikacji.
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.drone_input_window);
        // TODO: ZMIENIĆ PRZYPIĘCIE ELEMENTÓW UI NA NOWY LAYOUT!
        //this.setContentView(R.layout.drone_main_window_v2);
        initializeUIelements();

        // Inicjalizacja obiektu nasłuchującego eventów z gamepada oraz rozpoczęcie nasłuchiwania.
        mInputManager = InputManagerCompat.Factory.getInputManager(this.getBaseContext());
        mInputManager.registerInputDeviceListener(this, null);

        droneValues = new DroneValues();
        // Rozpoznanie urządzeń typu gamepad i przekazanie ich listy z identyfikatorami do tablicy.
        // Obecnie niewykorzystywane - zakładamy, że wejście będzie pochodzić tylko z jednego
        // źródła, więc nie ma sensu dodawać rozróżniania źródła wejścia.
        gamepadIds = findControllers();

        mNumberFormatter = NumberFormat.getIntegerInstance();
        sharedPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        initializeSettingsValues();
        /*
            Wyłącznie ostrzeżeń o ewentualnym wykonywaniu operacji sieciowych, IO itp. w głównym wątku.
            Obecny stan aplikacji nie powinien wywoływać ostrzeżenia (inicjalizacja połączenia, wysyłanie wartosci
            oraz nasłuchiwanie na PING serwera odbywają się w osobnych wątkach).
        */
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        // Wyłącz wyłączanie się ekranu po pewnym czasie bezczynności.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Wymuszenie działania głównego layoutu w trybie horyzontalnym.
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    void initializeUIelements() {
        /*
            Przypisanie elementów interfejsu do obiektów w kodzie.
            W przypadku klawiszy również dodanie obsługi zdarzeń wciśnięcia.
         */
        inputTextView = (TextView) findViewById(R.id.inputTextView);
        inputTextView.setMovementMethod(new ScrollingMovementMethod());
        yawTxtV = (TextView) findViewById(R.id.yawTextView);
        yawTxtV.setText("50%");
        throttleTxtV = (TextView) findViewById(R.id.throttleTextView);
        throttleTxtV.setText("0%");
        rollTxtV = (TextView) findViewById(R.id.rollTextView);
        rollTxtV.setText("50%");
        pitchTxtV = (TextView) findViewById(R.id.pitchTextView);
        pitchTxtV.setText("50%");
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
                    mjpegView = (MjpegView) findViewById(R.id.mjpegView);
                    setTitle(R.string.title_connecting);
                    new DoRead().execute(urlStream);
                    streamButton.setText("StopStream");
                    streamRunning = true;
                } else {
                    mjpegView.stopPlayback();
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
            new AsyncSocketConnect().execute();
        } else {
            socketConnection = false;
            droneSocketClient.closeConnection();
            socketButton.setText("Disconnected");
            socketButton.setTextColor(Color.YELLOW);
        }
    }


    void initializeSettingsValues(){
        // Inicjalizacja obiektów przechowujących dane z zakładki ustawień.
        droneIP = sharedPref.getString("ip_drona", "");
        urlStream = sharedPref.getString("url_stream", "");
        droneValues.setThrottlePin(Integer.parseInt(sharedPref.getString("pin_throttle", "0")));
        droneValues.setYawPin(Integer.parseInt(sharedPref.getString("pin_yaw", "1")));
        droneValues.setPitchPin(Integer.parseInt(sharedPref.getString("pin_pitch", "2")));
        droneValues.setRollPin(Integer.parseInt(sharedPref.getString("pin_roll", "6")));
        queue10 = Integer.parseInt(sharedPref.getString("queue_10", "1"));
        queue25 = Integer.parseInt(sharedPref.getString("queue_25", "5"));
        queue50 = Integer.parseInt(sharedPref.getString("queue_50", "10"));
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    List<Integer> findControllers() {
        int[] deviceIds = mInputManager.getInputDeviceIds();
        List<Integer> gamepads = new ArrayList<>();
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

    private void setUIaxisValues(){
        /*
            Metoda wywoływana przez processJoystickInput(...).
            Służy do formatowania wartości na osiach (pomijamy wartości po przecinku) oraz dodaniu znaku %,
            po to by ostatecznie przypisać je do elementów interfejsu.
        */
        throttleTxtV.setText(mNumberFormatter.format(droneValues.getThrottle()) + "%");
        yawTxtV.setText(mNumberFormatter.format(droneValues.getYaw()) + "%");
        pitchTxtV.setText(mNumberFormatter.format(droneValues.getPitch()) + "%");
        rollTxtV.setText(mNumberFormatter.format(droneValues.getRoll()) + "%");
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

        /*
         WARTOŚCI SĄ OD RAZU MNOŻONE PRZEZ 100, ABY UNIKNĄĆ POPEŁNIENIA BŁĘDU W DALSZYM PRZETWARZANIU
         WARTOŚCI!

         OBSLUGA LEWEGO SITCKA
        */
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

        /*
             OBSLUGA D-PADA

             Obecna implementacja NIE pozwala na wciśnięcie dwóch przycisków D-Pada jednocześnie!
             W przypadku inicjalizacji należy przytrzymać klawisz - jego puszczenie jest interpretowane jako osobne zdarzenie!
        */
        float dPAD;
        boolean czyDpad = false;
        String dPadStr = "";

        dPAD = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_HAT_X, historyPos);

        // OŚ POZIOMA - LEWO/PRAWO
        if (dPAD == -1.0) {
            dPadStr = " D-PAD LEWY";
            czyDpad = true;
        }
        if (dPAD == 1.0) {
            dPadStr = " D-PAD PRAWY // OBSŁUGA POŁĄCZENIA Z SERWEREM";
            connectionInitHandler();
            czyDpad = true;
        }

        // OŚ PIONOWA - GÓRA/DÓŁ
        dPAD = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_HAT_Y, historyPos);

        if (dPAD == -1.0) {
            dPadStr = " D-PAD GÓRA // INICJALIZACJA FLIGHT CONTROLLERA";
            // PRZYTRZYMAĆ KLAWISZ, DOPÓKI NIE ZAPALI SIĘ DIODA NA KONTROLERZE LOTU!
            if(socketConnection) {
                droneValues.setInitializationValues();
                setUIaxisValues();
                asyncValuesQueue.addLast(droneValues.getValuesSocketString());
            }
            czyDpad = true;
        }
        if (dPAD == 1.0) {
            dPadStr = " D-PAD DÓŁ // RESET WARTOŚCI";
            if(socketConnection) {
                droneValues = new DroneValues();
                setUIaxisValues();
                asyncValuesQueue.addLast(droneValues.getValuesSocketString());
            }
            czyDpad = true;
        }

        /*
            Jeżeli nie nastąpiło wciśnięcie któregoś z przycisków D-PADA - pobranie wartości z joysticków,
            sprawdzenie czy na którymkolwiek z nich nastąpiła zmiana wartości >=1.
         */
        boolean significantChange = false;
        if (!czyDpad) {
            if (Math.abs(droneValues.getThrottle() - (droneValues.getCalibratedThrottleValue(throttle))) >= 2) {
                droneValues.setThrottle(Integer.parseInt(mNumberFormatter.format(throttle)));
                significantChange = true;
            }

            if (Math.abs(droneValues.getYaw() - (droneValues.getCalibratedHorizontalGenericValue(yaw))) >= 2) {
                droneValues.setYaw(Integer.parseInt(mNumberFormatter.format(yaw)));
                significantChange = true;
            }

            if (Math.abs(droneValues.getPitch() - (droneValues.getCalibratedVerticalGenericValue(pitch))) >= 2) {
                droneValues.setPitch(Integer.parseInt(mNumberFormatter.format(pitch)));
                significantChange = true;
            }

            if (Math.abs(droneValues.getRoll() - (droneValues.getCalibratedHorizontalGenericValue(roll))) >= 2) {
                droneValues.setRoll(Integer.parseInt(mNumberFormatter.format(roll)));
                significantChange = true;
            }
        } else {
            // W przeciwnym wypadku po prostu wypisz informację o tym jaki klawisz D-Pada został wciśnięty
        }
        if(significantChange){
            // Jeśli zaszła duża zmiana - zaktualizuj elementy UI.
            setUIaxisValues();
        }
        // Jeżeli połączenie jest aktywne i nastąpiła znacząca zmiana - dodaj do kolejki wysyłania.
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
        /*
            Metoda wywoływana przy dokonaniu zmianyw zakładce ustawień i powrocie do głównego layoutu.
            Aktualizuje zmienne przechowujące wartości ustawień.
         */
        switch(key){
            case "ip_drona":
                droneIP = sharedPreferences.getString("ip_drona","");
                inputTextView.append(System.getProperty("line.separator") + "Zmiana IP na: " + droneIP);
                break;
            case "url_stream":
                urlStream = sharedPreferences.getString("url_stream", "");
                inputTextView.append(System.getProperty("line.separator") + "Zmiana URL streamu na: " + urlStream);
                break;
            case "pin_throttle":
                droneValues.setThrottlePin(Integer.parseInt(sharedPref.getString("pin_throttle", "0")));
                break;
            case "pin_yaw":
                droneValues.setYawPin(Integer.parseInt(sharedPref.getString("pin_yaw", "1")));
                break;
            case "pin_pitch":
                droneValues.setPitchPin(Integer.parseInt(sharedPref.getString("pin_pitch", "2")));
                break;
            case "pin_roll":
                droneValues.setRollPin(Integer.parseInt(sharedPref.getString("pin_roll", "6")));
                break;
            case "queue_50":
                queue50 = Integer.parseInt(sharedPref.getString("queue_50", "10"));
                inputTextView.append(System.getProperty("line.separator") + "Kolejka >= 50 - usuwanie " + queue50 + " wartości");
                break;
            case "queue_25":
                queue25 = Integer.parseInt(sharedPref.getString("queue_25", "5"));
                inputTextView.append(System.getProperty("line.separator") + "Kolejka >= 25 - usuwanie " + queue25 + " wartości");
                break;
            case "queue_10":
                queue10 = Integer.parseInt(sharedPref.getString("queue_10", "1"));
                inputTextView.append(System.getProperty("line.separator") + "Kolejka >= 10 - usuwanie " + queue10 + " wartości");
                break;
        }
    }

    public void onPause() {
        // Metoda obługi streamingu - obsługa eventu onPause
        if (DEBUG) Log.d(TAG, "onPause()");
        super.onPause();
        if (mjpegView != null) {
            mjpegView.stopPlayback();
        }
    }

    public void onDestroy() {
        // Metoda obługi zabicia layoutu.
        if (DEBUG) Log.d(TAG, "onDestroy()");
        if (mjpegView != null) {
            mjpegView.stopPlayback();
        }
        super.onDestroy();
    }

    public void onStop() {
        // Metoda obługi zatrzymania layoutu.
        if (DEBUG) Log.d(TAG, "onStop()");
        if (mjpegView != null) {
            mjpegView.stopPlayback();
        }
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

    private class AsyncSocketConnect extends AsyncTask<Void, Void, Boolean>{
        /*
            Podklasa wątku asynchronicznego.
            Odpowiedzialna za nawiązywanie połączenia z serwerem oraz obsługą zdarzeń związanych z połączniem.
         */

        @Override
        protected Boolean doInBackground(Void... params) {
            //  Jeżeli wcześniej wystąpił błąd - zamknij połączenie i socket.
            if(connectionError) { droneSocketClient.closeConnection(); connectionError = false; }
            try {
                droneSocketClient = new DroneSocketClient(droneIP, clientPort, serverPort, pingPort);
            }catch (DroneSocketClient.DroneSocketClientException e){
                return false;
            }
            // Wynik poniższego wywołania zostanie przekazany metodzie onPostExecute().
            return droneSocketClient.confirmVersionCompability();
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        protected void onPostExecute(Boolean result){
            if(result){
                // Jeżeli połączenie zotanie poprawnie zainicjalizowane.
                // Zainicjalizuj kolejkę wartości, oraz włącz wątki nasłuchiwacza wiadomości PING serwera
                // oraz wątek wysyłania wartości z kolejki.
                asyncValuesQueue = new LinkedList<>();
                socketConnection = true;
                new AsyncServerPingListener().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                new AsyncSocketSend().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                socketButton.setText("Connected!");
                socketButton.setTextColor(Color.GREEN);
            } else {
                connectionError = true;
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
        if(qSize >= 50){
            dropElementsInQueue(queue, queue50);
        }else if(qSize >= 25){
            dropElementsInQueue(queue, queue25);
        }else if(qSize >= 10){
            dropElementsInQueue(queue, queue10);
        }
    }

    private class AsyncSocketSend extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... params) {
            while (true) {
                // Zakończ pętlę jeżeli wystąpił błąd w wysyłaniu wartości lub nastąpił problem w wątku AsyncServerPingListener.
                if(!socketConnection || connectionError) { break; }

                //Przerzedzanie kolejki, gdy wartości jest zbyt wiele.
                removeExcessiveValues(asyncValuesQueue);

                try {
                    String peekValue = asyncValuesQueue.peekFirst();
                    if (peekValue != null) {
                        if (!droneSocketClient.sendValues(asyncValuesQueue.removeFirst())) {
                            socketConnection = false;
                            connectionError = true;
                            break;
                        }
                    }
                //W celu uniknięcia zatrzymania wątku z powodu nigroźnego błędu pustej kolejki, która mimo starań nadal może wystąpić.
                } catch (NullPointerException e){
                    // Kontynuuj pętlę
                }
            }
            return null;
        }

        protected void onPostExecute(Void result){
            if(!connectionError) {
                inputTextView.append(System.getProperty("line.separator") + "Zakończono wysyłanie w tle");
            }else {
                inputTextView.append(System.getProperty("line.separator") + "Błąd podczas wysyłania wartości! Próbuję wznowić połączenie...");
                socketButton.setText("CONN_ERROR");
                socketButton.setTextColor(Color.RED);
                new AsyncSocketConnect().execute();
            }
        }
    }

    private class AsyncServerPingListener extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... params) {
            if (DEBUG) Log.d(TAG, "Ping listener started!");
            while(true){
                if(!socketConnection || connectionError) { if (DEBUG) Log.d(TAG, "Ping listener break!"); break; }
                if(!droneSocketClient.pingAwaitAndReply()) {
                    socketConnection = false;
                    connectionError = true;
                    break;
                }
            }
            return null;
        }
    }

    public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {
        protected MjpegInputStream doInBackground(String... url) {
            //TODO: if camera has authentication deal with it and don't just not work
            HttpResponse res = null;
            DefaultHttpClient httpclient = new DefaultHttpClient();
            Log.d(TAG, "1. Sending http request");
            try {
                res = httpclient.execute(new HttpGet(URI.create(url[0])));
                Log.d(TAG, "2. Request finished, status = " + res.getStatusLine().getStatusCode());
                if(res.getStatusLine().getStatusCode()==401){
                    //You must turn off camera User Access Control before this will work
                    return null;
                }
                return new MjpegInputStream(res.getEntity().getContent());
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                Log.d(TAG, "Request failed-ClientProtocolException", e);
                //Error connecting to camera
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Request failed-IOException", e);
                //Error connecting to camera
            }

            return null;
        }

        protected void onPostExecute(MjpegInputStream result) {
            mjpegView.setSource(result);
            mjpegView.setDisplayMode(MjpegView.SIZE_BEST_FIT);
            mjpegView.showFps(true);
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

    @TargetApi(Build.VERSION_CODES.HONEYCOMB) // API 11
    public static <T> void executeAsyncTask(AsyncTask<T, ?, ?> asyncTask, T... params) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        else
            asyncTask.execute(params);
    }

}

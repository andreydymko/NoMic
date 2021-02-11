package com.andreydymko.nomic;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

// Наследуем класс AppCompatActivity, что бы ОС поняла что это класс активности
// а также перечисления интерфейсов Java
public class MainActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        View.OnClickListener,
        SeekBar.OnSeekBarChangeListener,
        CompoundButton.OnCheckedChangeListener {

    // тэг для функции логирования
    private static final String TAG = "MainActivity";

    // константы для идентификации запросов разрешений
    private static final int MICROPHONE_REQUEST_CODE = 0;
    private static final int CAMERA_REQUEST_CODE = 1;
    // экземпляры класса кнопок, переключателей, для связи интерфейса с кодом
    private Button buttonPlay, buttonStop, buttonSettings;
    private SeekBar soundVolumeSeekBar;
    private SwitchCompat muteSwitchCompat;
    private TextView textViewServiceState;

    // экземпляр класса для облегчения управления настройками приложения
    private PreferencesManager prefManager;
    // объект класса сервиса, позволяет вызывать методы внутри его класса
    private StreamingService streamingService;
    // указатель на менеджера каналов для широковещательных сообщений
    private LocalBroadcastManager broadcastManager;


    @Override
    // метод вызывается системой при запуске активности (в нашем случае и приложения)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // создаём канал для уведомлений
        createStreamServiceNotificationChannel();

        // получаем указатели на элементов интерфейса
        buttonPlay = findViewById(R.id.buttonPlay);
        buttonStop = findViewById(R.id.buttonStop);
        buttonSettings = findViewById(R.id.buttonSettings);
        soundVolumeSeekBar = findViewById(R.id.soundVolumeSeekBar);
        muteSwitchCompat = findViewById(R.id.soundMuteSwitch);
        textViewServiceState = findViewById(R.id.textViewServiceState);

        // и указываем что реализуемые интерфейсы находятся в этом классе
        buttonPlay.setOnClickListener(this);
        buttonStop.setOnClickListener(this);
        buttonSettings.setOnClickListener(this);
        soundVolumeSeekBar.setOnSeekBarChangeListener(this);
        muteSwitchCompat.setOnCheckedChangeListener(this);

        prefManager = new PreferencesManager(this);
        // загружаем сохраненный уровень громкости звука
        soundVolumeSeekBar.setProgress(prefManager.getSoundVolumeProgress());

        // получаем экземпляр менеджера широковещательных сообщений
        broadcastManager = LocalBroadcastManager.getInstance(this);
        // привязываем сервис - позволяет получить объект класса сервиса
        bindService(new Intent(this, StreamingService.class),
                streamingServiceConnection,
                0);
    }

    @Override
    // метод вызывается системой при открытии активности
    protected void onResume() {
        super.onResume();
        // регистрируем приемник широковещательных сообщений с фильтром
        // позволит нам получать сообщения от сервиса
        broadcastManager.registerReceiver(streamingServiceReceiver, new IntentFilter(getString(R.string.filtersStreamingSvcToMainActivity)));
        updateServiceState();
    }

    @Override
    // метод вызывается системой при скрытии активности из виду
    protected void onPause() {
        super.onPause();
        // отменяем регистрацию приемника широковещательных сообщений
        broadcastManager.unregisterReceiver(streamingServiceReceiver);
    }

    // вручную обновляет состояние интерфейса относительно состояния сервиса
    private void updateServiceState() {
        if (streamingService != null) {
            streamingServiceStateChanged(streamingService.getCurrentState());
        } else {
            streamingServiceStateChanged(StreamingService.SERVICE_STATE.STOPPED);
        }
    }

    @Override
    // метод вызывается системой при закрытии активности
    protected void onDestroy() {
        super.onDestroy();
        // отвязываемся от сервиса
        unbindService(streamingServiceConnection);
        // сохраняем уровень звука, заданный пользователем
        prefManager.setSoundVolumeProgress(soundVolumeSeekBar.getProgress());
    }

    // создает канал доставки уведомлений (только Android 8.0+)
    private void createStreamServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    getString(R.string.notificationStreamChannelId),
                    getString(R.string.streaming_audio_video_notifications_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(getString(R.string.streaming_audio_video_notifications_description));
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    // реализация интерфейса. Позволяет реагировать на нажатия кнопок пользователем
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonPlay:
                // проверяем, есть ли у нас разрешение на запись звука с микрофона
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    // разрешение есть - запускаем сервис прослушки микрофона
                    startStreamingService();
                } else {
                    // разрешения нет - запрашиваем разрешение
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_REQUEST_CODE);
                }
                break;
            case R.id.buttonStop:
                // останавливем сервис по кнопке
                stopStreamingService();
                break;
            case R.id.buttonSettings:
                // открываем активность с настройками
                startActivity(new Intent(this, SettingsActivity.class));
                break;
                default:
                    break;
        }
    }

    @Override
    // реализация интерфейса. Позволяет реагировать на передвижение ползунка пользователем
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar.getId() == R.id.soundVolumeSeekBar) {
            setSoundVolumeMultiplier();
        }
    }

    @Override
    // реализация интерфейса. Позволяет реагировать на переключение выключателя звука
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        if (compoundButton.getId() == R.id.soundMuteSwitch) {
            setSoundVolumeMultiplier();
        }
    }

    @Override
    // реализация интерфейса. Позволяет выполнить какое-либо действие
    // после получения разрешений от пользователя
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MICROPHONE_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // проучили разрешение для использования микрофона - запускаем сервис
                    startStreamingService();
                }
                break;
            case CAMERA_REQUEST_CODE:
                // possible feature
                break;
            default:
                break;
        }
    }

    // запускает сервис, передавая в него настройки пользователя
    private void startStreamingService() {
        // создаем "намеренность"
        Intent intent = new Intent(this, StreamingService.class);
        // закладываем в него доп. данные
        intent.putExtra(this.getString(R.string.extrasAudioRecorderSettings), prefManager.getRecorderPreferences());
        intent.putExtra(getString(R.string.extrasTCPPort), prefManager.getControlPort());
        // получаем локальный IP-адрес и закладываем его в "намеренность"
        intent.putExtra(getString(R.string.extrasTCPIP), Utils.getWifiIpAddress(this));
        // запускаем сервис, отправив в него данные
        startService(intent);
        // привязываемся к сервису
        bindService(intent, streamingServiceConnection, 0);
    }

    // останавливает сервис и отвзязвает активность от него
    private void stopStreamingService() {
        stopService(new Intent(this, StreamingService.class));
        unbindService(streamingServiceConnection);
    }

    // обращается к сервису по указателю, что бы он поставил громкость звука для микрофона
    private void setSoundVolumeMultiplier() {
        if (streamingService == null) return;
        switch (streamingService.getCurrentState()) {
            case StreamingService.SERVICE_STATE.STARTED:
            case StreamingService.SERVICE_STATE.CONNECTED:
                float multiplier = (muteSwitchCompat.isChecked() ? 0.0f : (float) soundVolumeSeekBar.getProgress());
                if (multiplier > 20.0f) {
                    multiplier = 20.0f;
                }
                if (multiplier < 0.0f) {
                    multiplier = 0.0f;
                }
                streamingService.setSoundVolumeOnThread(multiplier);
            default:
                break;
        }
    }

    // реализация приемника широковещательных сообщений
    private BroadcastReceiver streamingServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // получаем состояние сервиса и обновляем интерфейс в соотвествии с ним
            streamingServiceStateChanged(intent.getIntExtra(
                    getString(R.string.extrasStreamingServiceState),
                    StreamingService.SERVICE_STATE.ERROR)
            );
        }
    };

    // реализация привязки сервиса к активности, позволяет получить указатель на сервис
    private ServiceConnection streamingServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            streamingService = ((StreamingService.LocalBinder) iBinder).getService();
            updateServiceState();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            streamingService = null;
        }
    };

    // обновляем интерфейс и не только в соответсвии с состоянием сервиса
    private void streamingServiceStateChanged(@StreamingService.serviceState int serviceState) {
        boolean shouldPlayButtonBeEnabled = (serviceState == StreamingService.SERVICE_STATE.STOPPED);

        setSoundVolumeMultiplier();
        // устанавливаем текст, отображаемый пользователю
        textViewServiceState.setText(Utils.getStateString(this, serviceState));
        // устанавливаем состояние кнопок "включена - выключена"
        buttonPlay.setEnabled(shouldPlayButtonBeEnabled);
        buttonSettings.setEnabled(shouldPlayButtonBeEnabled);
        buttonStop.setEnabled(!shouldPlayButtonBeEnabled);
    }

    // реализации интерфейса для ползунка громкости, не используются
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}

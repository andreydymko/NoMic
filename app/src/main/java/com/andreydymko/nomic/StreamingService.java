package com.andreydymko.nomic;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.net.SocketException;

import static java.lang.annotation.RetentionPolicy.SOURCE;

// класс сервиса, который захватывает звук микрофона в фоне и отправляет его клиенту
public class StreamingService extends Service implements
        TCPServerMicThread.TCPServerMicDelegate,
        MicThread.UDPSocketFailListener {

    // Java-аннотации для состояния сервиса
    @Retention(SOURCE)
    @IntDef({SERVICE_STATE.STOPPING,
            SERVICE_STATE.STOPPED,
            SERVICE_STATE.STARTING,
            SERVICE_STATE.STARTED,
            SERVICE_STATE.CONNECTED,
            SERVICE_STATE.ERROR})
    public @interface serviceState {}

    private final static String TAG = "StreamingService";
    // идентификатор уведомления, позволяет управлять уведомлением из любого места программы
    private final static int ONGOING_NOTIFICATION_ID = 333;

    // "привязыватель" сервиса, позволяет привязываться к сервису
    private final IBinder binder = new LocalBinder();
    // переменная, отображающая состояние сервиса
    private @serviceState int currServiceState;
    // переменная для хранения порта "рукопожатий" сервера и клиента
    private int localTCPPort;
    // локальный IP-адрес устройства
    private String localIp;

    // простой контейнер для хранения настроек пользователя
    private AudioRecorderSettings settings;
    // экземпляр класса потока для "рукопожатия" сервера и клиента
    private TCPServerMicThread serverMicThread;
    // экземпляр класса потока для отправки звука с микрофона
    private MicThread micThread;

    // вызывается системой, когда отправлена комманда запустить сервис - startService()
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null) {
            // если в "намеренности" оказался флаг указывающий на завершение работы сервиса - то
            // останавливаем сервис
            if (intent.getAction().equals(this.getString(R.string.actionsStopMicService))) {
                stopSelf();
            }
            return START_STICKY;
        }
        // обновляем состояние сервиса
        updateServiceState(SERVICE_STATE.STARTING);

        // отправляем запрос системе на запуск сервиса в переднем плане,
        // при этом система требует что-бы мы показали уведомление пользователю
        startForeground(ONGOING_NOTIFICATION_ID, createNotification());
        // забираем данные из "намеренности", которую мы создали при запуске сервиса
        settings = intent.getParcelableExtra(this.getString(R.string.extrasAudioRecorderSettings));
        localTCPPort = intent.getIntExtra(getString(R.string.extrasTCPPort), 8126);
        localIp = intent.getStringExtra(getString(R.string.extrasTCPIP));
        // запускаем процесс ожидания "рукопожатия" и отправки звука с микрофона
        startStreamSession();

        // вернув это значение, мы скажем системе, что бы она перезапустила сервис,
        // если она остановила его по причине нехватки ресурсов
        return START_STICKY;
    }

    // создает экземпляр уведомления с определенными параметрами
    private Notification createNotification() {
        Intent stopServiceIntent = new Intent(this, StreamingService.class);
        // задаем "намерение" с действием завершения работы сервиса
        stopServiceIntent.setAction(this.getString(R.string.actionsStopMicService));

        // создаем "ожидающее намерение", которое отправит наше "намерение" в сервис,
        // т.е. завершит работу сервиса
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, stopServiceIntent,0);

        // при нажатии на уведомление - "ожидающее намерение" отправит свои данные в сервис
        // и он прекратит работу
        return new NotificationCompat.Builder(this, this.getString(R.string.notificationStreamChannelId))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(this.getString(R.string.app_name))
                .setContentText(this.getString(R.string.tap_to_stop_listening))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .build();
    }

    // начинает процесс рукопожатий и отправки звука микрофона
    private void startStreamSession() {
        // инициализируем поток настройками для "записывателя" звука
        micThread = new MicThread(settings);
        try {
            // пытаемся поднять сокет для рукопожатия посредством TCP
            // при этом просим у системы выделить UDP-порт для будущей отправки звука
            serverMicThread = new TCPServerMicThread(localTCPPort, localIp, micThread.allocateUDPPort(), settings);
        } catch (SocketException e) {
            // при неудаче обновляем состояние сервиса
            updateServiceState(SERVICE_STATE.ERROR);
            // показываем сообщение пользователю
            Toast.makeText(this, getString(R.string.udp_socket_error), Toast.LENGTH_LONG).show();
            // останавливаем сервис
            stopSelf();
        }
        // если всё удачно - будем ожидать когда клиент присоедениться к нашему серверу
        serverMicThread.setOnHandshakeDoneListener(this);
        serverMicThread.start();
        updateServiceState(SERVICE_STATE.STARTED);
    }

    // клиент присоеденился к серверу и удачно получил UDP-порт, к которому должен будет подключиться
    // а также настройки "записывателя" звука, необходимые для инициализации проигрывателя.
    // Заодно мы узнали IP клиента, что позволит нам направить UDP-пакеты к нему,
    // а не создавать широковещательный UDP-пакет
    @Override
    public void onHandshakeDone(String remoteINetAddress) {
        // ожидаем ситуации, если отправка данных будет провалена
        micThread.setOnSocketFailListener(this);
        // отправляем IP клиента
        micThread.setRemoteINetAddress(remoteINetAddress);
        // запускаем поток
        micThread.start();
        updateServiceState(SERVICE_STATE.CONNECTED);
    }

    // если рукопожатие провалилось - отработаем ошибку и остановим сервис
    @Override
    public void onHandshakeFailed(Exception ex, @TCPServerMicThread.failedPlace int failedPlace) {
        switch (failedPlace) {
            case TCPServerMicThread.FAILED_PLACE.OPEN_SOCKET:
            case TCPServerMicThread.FAILED_PLACE.WAIT_FOR_CONNECTION:
            case TCPServerMicThread.FAILED_PLACE.SEND_SAMPLE_RATE_SETTINGS:
                updateServiceState(SERVICE_STATE.ERROR);
                stopSelf();
                break;
        }
    }

    // если отправка пакета провалилась - остановим сервис
    @Override
    public void onSocketFail(Exception e) {
        if (e instanceof IOException) {
            updateServiceState(SERVICE_STATE.ERROR);
            stopSelf();
        }
    }

    // вызывается системой когда программист запрашивает остановку сервиса
    // stopService() или stopSelf()
    @Override
    public void onDestroy() {
        updateServiceState(SERVICE_STATE.STOPPING);
        Log.d(TAG, "Destroying Service");
        if (serverMicThread != null) {
            // останавливаем коммуникацию между клиентом и сервером
            serverMicThread.interrupt();
        }
        if (micThread != null) {
            // останавливаем запись звука микрофона и его отправку
            micThread.interrupt();
        }
        updateServiceState(SERVICE_STATE.STOPPED);
    }

    public void setSoundVolumeOnThread(float multiplier) {
        // устанавливаем уровень звука
        if (micThread == null) {
            return;
        }
        micThread.setSoundVolumeMultiplier(multiplier);
    }

    // возвращает текущее состояние сервиса
    public @serviceState int getCurrentState() {
        return currServiceState;
    }

    // вызывается системой когда программист запрашивает привязку к сервису
    @Override
    public IBinder onBind(Intent intent) {
        sendServiceState();
        return binder;
    }

    private void updateServiceState(@serviceState int state) {
        currServiceState = state;
        sendServiceState();
    }

    // отправляет состояние сервися при помощи менеджера широковещательных сообщений
    private void sendServiceState() {
        // создаем "намеренность с фильтром"
        Intent intent = new Intent(getString(R.string.filtersStreamingSvcToMainActivity));
        // закладываем в нее текущее состояние сервиса
        intent.putExtra(getString(R.string.extrasStreamingServiceState), currServiceState);
        // отправляет "намеренность"
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    // позволяет отправить привязываемой активности указатель на этот сервис
    public class LocalBinder extends Binder {
        StreamingService getService() {
            return StreamingService.this;
        }
    }

    // класс перечисления состояний сервиса
    public static class SERVICE_STATE {
        public final static int STOPPING = 0;
        public final static int STOPPED = 1;
        public final static int STARTING = 2;
        public final static int STARTED = 3;
        public final static int CONNECTED = 4;
        public final static int ERROR = 5;
    }
}

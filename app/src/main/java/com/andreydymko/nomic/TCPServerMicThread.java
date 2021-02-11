package com.andreydymko.nomic;

import androidx.annotation.IntDef;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class TCPServerMicThread extends Thread {
    @Retention(SOURCE)
    @IntDef({FAILED_PLACE.OPEN_SOCKET,
            FAILED_PLACE.WAIT_FOR_CONNECTION,
            FAILED_PLACE.SEND_SAMPLE_RATE_SETTINGS})
    public @interface failedPlace {}

    // через какой порт должны общаться клиент и сервер
    private int localTcpPort;
    // на каком локальном IP должен открываться сокет
    private String localIp;
    // какой номер порта отправить клиенту
    private int localUdpPort;
    // настройки "записывателя" звука, которые должны быть переданы клиенту
    private AudioRecorderSettings settings;

    // серверный TCP-сокет, к которому должен подключиться клиент, для начала связи
    private ServerSocket serverSocket;
    // сокет открывающийся после подключения клиента, позволяет посылать сообщения
    private Socket socket;
    private TCPServerMicDelegate serverMicDelegate;

    TCPServerMicThread(int localTcpPort, String localIp, int localUdpPort, AudioRecorderSettings settings) {
        this.localTcpPort = localTcpPort;
        this.localIp = localIp;
        this.localUdpPort = localUdpPort;
        this.settings = settings;
    }

    public void setOnHandshakeDoneListener(TCPServerMicDelegate delegate) {
        this.serverMicDelegate = delegate;
    }

    public void run() {
        // инициализируем процесс рукопожатия
        if (!Thread.interrupted() && openSocket()) {
            if (waitForConnection()) {
                if (!Thread.interrupted() && sendPortSettings(localUdpPort, settings)) {
                    if (serverMicDelegate != null) {
                        // если рукопожатие удачно - отправляем IP подключившегося клиента
                        // в реализацию интерфейса
                        serverMicDelegate.onHandshakeDone(socket.getRemoteSocketAddress().toString());
                    }
                }
            }
        }

        // закрываем сокеты
        try {
            serverSocket.close();
            socket.close();
        } catch (NullPointerException | IOException ignored) {
            // игнорируем ошибку
        }
    }

    private boolean openSocket() {
        try {
            // открываем сокет на определенном порте и привязываем его к определенному IP
            serverSocket = new ServerSocket(localTcpPort,0, InetAddress.getByName(localIp));
            // сокет будет ждать подключения в течении 1 секунды, а затем выдаст ошибку таймаута
            serverSocket.setSoTimeout(1000);
        } catch (IOException e) {
            handshakeFailed(e, FAILED_PLACE.OPEN_SOCKET);
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean waitForConnection() {
        boolean interrupted;
        // пока поток не попросили остановиться
        while (!(interrupted = Thread.interrupted())) {
            try {
                // ждем подключения к сокету в течении 1 сек
                socket = serverSocket.accept();
            } catch (SocketTimeoutException ex) {
                // если подключения не произошло, и поток не останавливают,
                // то ждем подключения еще раз
                continue;
            } catch (SecurityException | IOException e) {
                handshakeFailed(e, FAILED_PLACE.WAIT_FOR_CONNECTION);
                e.printStackTrace();
                return false;
            }
            break;
        }
        return !interrupted;
    }

    private boolean sendPortSettings(int port, AudioRecorderSettings settings) {
        try (PrintWriter printWriter = new PrintWriter(socket.getOutputStream())) {
            // отправляем строку с UDP-портом клиенту
            printWriter.println(port);
            // отправляем строку с настройкой записывателя
            printWriter.println(settings.getSampleRate());
            // закрываем поток записи
            printWriter.close();
            return true;
        } catch (IOException e) {
            handshakeFailed(e, FAILED_PLACE.SEND_SAMPLE_RATE_SETTINGS);
            e.printStackTrace();
            return false;
        }
    }

    private void handshakeFailed(Exception ex, @failedPlace int failedPlace) {
        if (serverMicDelegate != null) {
            serverMicDelegate.onHandshakeFailed(ex, failedPlace);
        }
    }

    public interface TCPServerMicDelegate {
        void onHandshakeDone(String remoteINetAddress);

        void onHandshakeFailed(Exception ex, @failedPlace int failedPlace);
    }

    public static class FAILED_PLACE {
        public final static int OPEN_SOCKET = 0;
        public final static int WAIT_FOR_CONNECTION = 1;
        public final static int SEND_SAMPLE_RATE_SETTINGS = 2;
    }
}

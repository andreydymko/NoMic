package com.andreydymko.nomic;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;


import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MicThread extends Thread {

    private static final String TAG = "MicThread";

    private AudioRecorderSettings recorderSettings;
    // переменная для хранения множителя громкости звука микрофона
    private float soundVolumeMultiplier = 10.0f;
    // указатель на экземпляр реализации интерфейса
    private UDPSocketFailListener delegate;
    private String remoteINetAddress;
    // UDP-сокет
    private DatagramSocket datagramSocket;

    MicThread(AudioRecorderSettings audioRecorderSettings) {
        this.recorderSettings = audioRecorderSettings;
    }

    // получает указатель на реализацию интерфейса
    public void setOnSocketFailListener(UDPSocketFailListener delegate) {
        this.delegate = delegate;
    }

    // пытается открыть UDP-сокет, при этом запрашивая выделение свободного UDP-порта
    public int allocateUDPPort() throws SocketException {
        this.datagramSocket = new DatagramSocket();
        return datagramSocket.getLocalPort();
    }

    // устанавливает IP-адрес получателя звука микрофона
    public void setRemoteINetAddress(String iNetAddress) {
        this.remoteINetAddress = iNetAddress;
    }

    // метод вызывается системой, когда программист вызывает Thread.start()
    // при этом происходит выделение нового потока для программы
    // т.е. код этой функции работает в отдельном потоке
    public void run() {
        // получаем порт и IP адрес
        int udpPort = datagramSocket.getLocalPort();
        InetAddress broadcastIp;
        try {
            broadcastIp = InetAddress.getByName(Utils.trimHostname(remoteINetAddress));
        } catch (UnknownHostException | NullPointerException e) {
            socketFailed(e);
            e.printStackTrace();
            return;
        }

        // загружаем нативную библиотеку
        System.loadLibrary("soundVolumeIncreaser");
        int bufferSize = AudioRecord.getMinBufferSize(
                recorderSettings.getSampleRate(),
                recorderSettings.getAudioFormatChannel(),
                recorderSettings.getAudioFormatEncoding()
        );
        // инициализируем "записыватель" звука микрофона
        AudioRecord micRecorder = new AudioRecord(
                recorderSettings.getAudioSource(),
                recorderSettings.getSampleRate(),
                recorderSettings.getAudioFormatChannel(),
                recorderSettings.getAudioFormatEncoding(),
                bufferSize
        );
        // буффер для хранения звуковых данных
        byte[] buffer = new byte[bufferSize];

        // экземпляр UDP-пакета, направленный в сторону клиента по опредленному порту
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length, broadcastIp, udpPort);
        // начинаем запись звука в буфер
        micRecorder.startRecording();
        Log.d(TAG, "Streaming with buffer size of: " + bufferSize
                + " and volume: " + soundVolumeMultiplier
                + " to ip: " + this.remoteINetAddress
                + " using port: " + udpPort);


        // пока поток не попросили остановиться извне
        while (!Thread.interrupted()) {
            // читаем звук в буфер
            micRecorder.read(buffer, 0, bufferSize);

            // увеличиваем громкость звука в n раз
            increaseSoundVolume(buffer, soundVolumeMultiplier);
            // устанавливаем данные в пакет
            datagramPacket.setData(buffer);
            try {
                // отправляем пакет по сокету
                datagramSocket.send(datagramPacket);
            } catch (IOException e) {
                socketFailed(e);
                e.printStackTrace();
                break;
            }
        }

        // поток попросили остановиться
        // закрываем сокет
        datagramSocket.close();
        // завершаем запись звука
        micRecorder.stop();
        micRecorder.release();
    }

    private void socketFailed(Exception e) {
        if (delegate != null) {
            delegate.onSocketFail(e);
        }
    }

    public interface UDPSocketFailListener {
        void onSocketFail(Exception e);
    }

    public void setSoundVolumeMultiplier(float multiplier) {
        this.soundVolumeMultiplier = multiplier;
    }

    // объявляем нативный метод, который усиливает звук в n раз
    private native void increaseSoundVolume(@NotNull byte[] soundBuffer, float multiplier);

    // метод, который усиливает звук, но реализован не нативно
    private void increaseSoundVolumeOld(@NotNull byte[] audioBuffer, float multiplier) {
        // PCM 16 bit in mono; 16 bit == 2 byte
        int sampleLength = 2;
        short sample;
        for (int i = 0; i < audioBuffer.length; i += sampleLength) {
            sample = (short) Math.floor(((audioBuffer[i+1] & 0xFF) << 8 | audioBuffer[i] & 0xFF) * multiplier);
            audioBuffer[i] = (byte) (sample & 0xff);
            audioBuffer[i+1] = (byte) ((sample >> 8) & 0xff);
        }
    }

    @Deprecated
    // метод, который усиливает звук, реализован не нативно, использует ByteBuffer
    // менее эффективен
    private void increaseSoundVolumeOld1(@NotNull byte[] audioBuffer, float multiplier) {
        // PCM 16 bit in mono; 16 bit == 2 byte
        int sampleLength = 2;
        ByteBuffer byteBuffer = ByteBuffer.allocate(sampleLength);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        short sample;
        for (int i = 0; i < audioBuffer.length; i += sampleLength) {
            byteBuffer.put(audioBuffer, i, sampleLength).rewind();
            sample = (short) Math.floor(byteBuffer.getShort(0) * multiplier);
            byteBuffer.clear();
            byteBuffer.putShort(sample).rewind();
            byteBuffer.get(audioBuffer, i, sampleLength).clear();
        }
    }
}

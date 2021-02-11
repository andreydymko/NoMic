package com.andreydymko.nomic;

import android.os.Parcel;
import android.os.Parcelable;

public class AudioRecorderSettings implements Parcelable {

    private int audioSource;
    private int sampleRate;
    private int audioFormatChannel;
    private int audioFormatEncoding;

    public AudioRecorderSettings(int audioSource, int sampleRate, int audioFormatChannel, int audioFormatEncoding) {
        this.audioSource = audioSource;
        this.sampleRate = sampleRate;
        this.audioFormatChannel = audioFormatChannel;
        this.audioFormatEncoding = audioFormatEncoding;
    }

    public AudioRecorderSettings(Parcel in) {
        int[] data = new int[4];
        in.readIntArray(data);
        audioSource = data[0];
        sampleRate = data[1];
        audioFormatChannel = data[2];
        audioFormatEncoding = data[3];
    }

    public int getAudioFormatChannel() {
        return audioFormatChannel;
    }

    public int getAudioFormatEncoding() {
        return audioFormatEncoding;
    }

    public int getAudioSource() {
        return audioSource;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setAudioFormatChannel(int audioFormatChannel) {
        this.audioFormatChannel = audioFormatChannel;
    }

    public void setAudioFormatEncoding(int audioFormatEncoding) {
        this.audioFormatEncoding = audioFormatEncoding;
    }

    public void setAudioSource(int audioSource) {
        this.audioSource = audioSource;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeIntArray(new int[] { audioSource, sampleRate, audioFormatChannel, audioFormatEncoding });
    }

    public static final Parcelable.Creator<AudioRecorderSettings> CREATOR = new Parcelable.Creator<AudioRecorderSettings>() {
        @Override
        public AudioRecorderSettings createFromParcel(Parcel in) {
            return new AudioRecorderSettings(in);
        }

        @Override
        public AudioRecorderSettings[] newArray(int size) {
            return new AudioRecorderSettings[size];
        }
    };
}

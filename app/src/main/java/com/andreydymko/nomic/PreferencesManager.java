package com.andreydymko.nomic;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.MediaRecorder;

import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Map;

public class PreferencesManager {

    private final Map<String, Integer> audioSourceMap;

    private final static String prefKey = "No Mic";

    private Context context;
    private SharedPreferences sharedPreferences;

    PreferencesManager(final Context context) {
        this.context = context;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        audioSourceMap = new HashMap<String, Integer>() {{
            put(context.getString(R.string.source_default_value), MediaRecorder.AudioSource.DEFAULT);
            put(context.getString(R.string.source_microphone_value), MediaRecorder.AudioSource.MIC);
            put(context.getString(R.string.source_camcorder_value), MediaRecorder.AudioSource.CAMCORDER);
        }
        };
    }

    public void setRecorderPreferences(AudioRecorderSettings settings) {
        SharedPreferences.Editor editor = this.sharedPreferences.edit();
        editor.putInt(context.getString(R.string.prefKeyAudioSource), settings.getAudioSource());
        editor.putInt(context.getString(R.string.prefKeySampleRate), settings.getSampleRate());
        editor.putInt(context.getString(R.string.prefKeyAudioFormatChannel), settings.getAudioFormatChannel());
        editor.putInt(context.getString(R.string.prefKeyAudioFormatEncoding), settings.getAudioFormatEncoding());

        editor.apply();
    }


    public AudioRecorderSettings getRecorderPreferences() {
        try {
            return new AudioRecorderSettings(
                    audioSourceMap.get(sharedPreferences.getString(context.getString(R.string.prefKeyAudioSource), context.getString(R.string.source_default_value))),
                    Integer.valueOf(sharedPreferences.getString(context.getString(R.string.prefKeySampleRate), String.valueOf(context.getResources().getInteger(R.integer.defaultSampleRate)))),
                    sharedPreferences.getInt(context.getString(R.string.prefKeyAudioFormatChannel), AudioFormat.CHANNEL_IN_MONO),
                    sharedPreferences.getInt(context.getString(R.string.prefKeyAudioFormatEncoding), AudioFormat.ENCODING_PCM_16BIT)
            );
        } catch (Exception ex) {
            ex.printStackTrace();
            return new AudioRecorderSettings(
                    MediaRecorder.AudioSource.DEFAULT,
                    48000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
        }
    }

    public void setPrefAudioSource(int audioSource) {
        SharedPreferences.Editor editor = this.sharedPreferences.edit();
        editor.putInt(context.getString(R.string.prefKeyAudioSource), audioSource);
        editor.apply();
    }

    public void setPrefSampleRate(int sampleRate) {
        SharedPreferences.Editor editor = this.sharedPreferences.edit();
        editor.putInt(context.getString(R.string.prefKeySampleRate), sampleRate);
        editor.apply();
    }

    public void setPrefAudioFormatChannel(int audioFormatChannel) {
        SharedPreferences.Editor editor = this.sharedPreferences.edit();
        editor.putInt(context.getString(R.string.prefKeyAudioFormatChannel), audioFormatChannel);
        editor.apply();
    }

    public void setPrefAudioFormatEncoding(int audioFormatEncoding) {
        SharedPreferences.Editor editor = this.sharedPreferences.edit();
        editor.putInt(context.getString(R.string.prefKeyAudioFormatEncoding), audioFormatEncoding);
        editor.apply();
    }

    public void setSoundVolumeProgress(int progress) {
        SharedPreferences.Editor editor = this.sharedPreferences.edit();
        editor.putInt(context.getString(R.string.prefKeySoundVolumeProgress), progress);
        editor.apply();
    }

    public int getSoundVolumeProgress() {
        return sharedPreferences.getInt(context.getString(R.string.prefKeySoundVolumeProgress), 10);
    }

    public int getControlPort() {
        try {
            return Integer.parseInt(sharedPreferences.getString(context.getString(R.string.prefKeyControlPort), "8126"));
        } catch (NullPointerException | NumberFormatException e) {
            return 8126;
        }
    }
}

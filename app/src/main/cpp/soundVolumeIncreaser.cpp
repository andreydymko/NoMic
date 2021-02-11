#include "soundVolumeIncreaser.h"
#include <jni.h>
#include <algorithm>
#include <cmath>
// реализация нативного метода
extern "C" void Java_com_andreydymko_nomic_MicThread_increaseSoundVolume (
JNIEnv *env,
jobject obj,
jbyteArray audioBuffer,
jfloat multiplier
)
{
    // PCM 16 бит в моно-режиме. Каждый сэмпл будет размером в 16 бит == 2 байта
    jint sampleLength = 2;
    jshort sample;
    // получаем указатель на массив из JVM
    jbyte *bufferPtr = env->GetByteArrayElements(audioBuffer, JNI_FALSE);
    jbyte *bufferEnd = bufferPtr + env->GetArrayLength(audioBuffer);
    // для каждого элемента массива
    for (jbyte *ptr = bufferPtr; ptr < bufferEnd; ptr += sampleLength) {
        // преобразуем два byte в один short посредством битовых сдвигов и умножаем на множитель звука
        sample = static_cast<jshort>(
                std::floor(static_cast<jfloat>(
                        ((*(ptr+1) & 0xFF) << 8 ) | (*ptr & 0xff)) * multiplier
                        )
                );
        // преобразуем один short в два byte, и сразу же копируем их в массив
        std::copy(static_cast<const jbyte*>(static_cast<const void*>(&sample)),
                  static_cast<const jbyte*>(static_cast<const void*>(&sample)) + sizeof sample,
                  ptr);
    }
    // говорим JVM, что нам больше не нужен указатель на данный массив,
    // и что она должна записать изменения в его версию доступную из Java-кода
    env->ReleaseByteArrayElements(audioBuffer, bufferPtr, 0);
}

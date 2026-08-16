package com.voconexus.app.core.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromChunkStatus(value: ChunkStatus): String = value.name

    @TypeConverter
    fun toChunkStatus(value: String): ChunkStatus =
        runCatching { ChunkStatus.valueOf(value) }.getOrDefault(ChunkStatus.PENDING)

    @TypeConverter
    fun fromGenerationJobStatus(value: GenerationJobStatus): String = value.name

    @TypeConverter
    fun toGenerationJobStatus(value: String): GenerationJobStatus =
        runCatching { GenerationJobStatus.valueOf(value) }.getOrDefault(GenerationJobStatus.QUEUED)

    @TypeConverter
    fun fromAudioFormat(value: AudioFormat): String = value.name

    @TypeConverter
    fun toAudioFormat(value: String): AudioFormat =
        runCatching { AudioFormat.valueOf(value) }.getOrDefault(AudioFormat.WAV)
}

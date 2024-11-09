package milan.bowzgore.mfp;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class AudioGainNormalizer {

    private static final int DESIRED_RMS = -20; // Target RMS in dB

    public void normalizeMP3(File inputFile, File outputFile) {
        try {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(inputFile.getAbsolutePath());

            // Find the audio track in the file
            int audioTrackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    extractor.selectTrack(i);
                    break;
                }
            }

            if (audioTrackIndex == -1) {
                throw new RuntimeException("No audio track found in file.");
            }

            // Set up the decoder
            MediaFormat format = extractor.getTrackFormat(audioTrackIndex);
            MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            ByteBuffer[] inputBuffers = codec.getInputBuffers();
            ByteBuffer[] outputBuffers = codec.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            FileOutputStream outputStream = new FileOutputStream(outputFile);

            double rmsSum = 0;
            int sampleCount = 0;

            // Decode, calculate RMS, and adjust gain
            while (true) {
                int inputBufferIndex = codec.dequeueInputBuffer(10000);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    int size = extractor.readSampleData(inputBuffer, 0);
                    if (size >= 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, size, extractor.getSampleTime(), 0);
                        extractor.advance();
                    } else {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        break;
                    }
                }

                int outputBufferIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    byte[] bufferData = new byte[info.size];
                    outputBuffer.get(bufferData);
                    outputBuffer.clear();

                    // Calculate RMS for loudness analysis
                    short[] pcmSamples = byteArrayToShortArray(bufferData);
                    for (short sample : pcmSamples) {
                        rmsSum += sample * sample;
                        sampleCount++;
                    }

                    // Adjust the gain based on desired loudness (if at end of file)
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        double currentRMS = Math.sqrt(rmsSum / sampleCount);
                        double gainFactor = Math.pow(10, (DESIRED_RMS - currentRMS) / 20);
                        pcmSamples = adjustGain(pcmSamples, gainFactor);

                        // Write adjusted samples to output file
                        outputStream.write(shortArrayToByteArray(pcmSamples));
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false);
                }
            }

            outputStream.close();
            codec.stop();
            codec.release();
            extractor.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private short[] byteArrayToShortArray(byte[] byteArray) {
        short[] shorts = new short[byteArray.length / 2];
        for (int i = 0; i < shorts.length; i++) {
            shorts[i] = (short) ((byteArray[i * 2] & 0xFF) | (byteArray[i * 2 + 1] << 8));
        }
        return shorts;
    }

    private byte[] shortArrayToByteArray(short[] shortArray) {
        byte[] byteArray = new byte[shortArray.length * 2];
        for (int i = 0; i < shortArray.length; i++) {
            byteArray[i * 2] = (byte) (shortArray[i] & 0xFF);
            byteArray[i * 2 + 1] = (byte) ((shortArray[i] >> 8) & 0xFF);
        }
        return byteArray;
    }

    private short[] adjustGain(short[] samples, double gainFactor) {
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) Math.min(Short.MAX_VALUE, Math.max(Short.MIN_VALUE, samples[i] * gainFactor));
        }
        return samples;
    }
}

package com.example.libtest;

import android.media.MediaCodec;

import java.io.InputStream;


import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;

import android.annotation.SuppressLint;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.ParcelFileDescriptor;
import android.util.Log;

/**
 * An InputStream that uses data from a MediaCodec.
 * The purpose of this class is to interface existing RTP packetizers of
 * libstreaming with the new MediaCodec API. This class is not thread safe !
 */
@SuppressLint("NewApi")
public class MediaCodecInputStream extends InputStream {

    public final String TAG = "MediaCodecInputStream";

    private MediaCodec mMediaCodec = null;
    private BufferInfo mBufferInfo = new BufferInfo();
    private ByteBuffer mBuffer = null;
    private int mIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
    private boolean mClosed = false;

    public MediaFormat mMediaFormat;

    public MediaCodecInputStream(MediaCodec mediaCodec) {
        mMediaCodec = mediaCodec;
    }

    @Override
    public void close() {
        mClosed = true;
    }

    @Override
    public int read() throws IOException {
        return 0;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int min = 0;

        try {
            if (mClosed) throw new IOException("This InputStream was closed");

            checkBufferNull();

            min = Math.min(length, mBufferInfo.size - mBuffer.position());
            mBuffer.get(buffer, offset, min);
            if (mBuffer.position() >= mBufferInfo.size) {
                mMediaCodec.releaseOutputBuffer(mIndex, false);
                mBuffer = null;
            }

        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        return min;
    }

    private void checkBufferNull() {
        if (mBuffer==null) {
            while (!Thread.interrupted() && !mClosed) {
                mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 500000);
                if (mIndex >= 0){
                    //Log.d(TAG,"Index: "+mIndex+" Time: "+mBufferInfo.presentationTimeUs+" size: "+mBufferInfo.size);

                    mBuffer = mMediaCodec.getOutputBuffer(mIndex);
                    Objects.requireNonNull(mBuffer).position(0);
                    break;
                } else if (mIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mMediaFormat = mMediaCodec.getOutputFormat();
                    Log.i(TAG, mMediaFormat.toString());
                } else if (mIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.v(TAG,"No buffer available...");
                } else {
                    Log.e(TAG,"Message: "+mIndex);
                }
            }
        }
    }

    public int available() {
        checkBufferNull();
        if (mBuffer != null)
            return mBufferInfo.size - mBuffer.position();
        else
            return 0;
    }

    public BufferInfo getLastBufferInfo() {
        return mBufferInfo;
    }

    public void writeToChannel(WritableByteChannel channel) throws IOException {
        Log.v(TAG,"writeToChannel");

        if (mClosed) throw new IOException("This InputStream was closed");

        checkBufferNull();

        channel.write(mBuffer);
        mMediaCodec.releaseOutputBuffer(mIndex, false);
        mBuffer = null;
        Log.v(TAG,"released " + mIndex);
    }
}


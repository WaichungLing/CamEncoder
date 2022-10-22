package ucla.cs211.camencoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AvcDecoder {
    private final static String TAG = AvcEncoder.class.getSimpleName();
    private final static String MIME_TYPE = "video/avc";

    final int TIMEOUT_USEC = 10000;     // TIMEOUT for inactive buffer

    MediaCodec decoder;
    MediaFormat format;

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private Surface surface;

    public AvcDecoder() {}

    public boolean init(int mWidth, int mHeight){
        try{
            decoder = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (IOException e) {
            return false;
        }

        format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 500000);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        //format.setByteBuffer("csd-0", ByteBuffer.wrap());
        decoder.configure(format, this.surface, null, 0);        // TODO: remove surface
        decoder.start();

        Log.i("Decoder", String.valueOf(decoder.getOutputFormat().getInteger(MediaFormat.KEY_COLOR_FORMAT)));

        return true;
    }

    public void setSurface(Surface surface) {
        this.surface = surface;
        if (this.surface == null){
            Log.d("setSurface", "null surface");
        }
    }

    public byte[] offerDecoder(byte[] data) {        // Decode format: YUV420SemiPlanar
        if (this.surface == null){
            Log.i("Decode", "null surface");
        }

        try {
            // put current data in queue
            ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
            ByteBuffer[] decoderOutputBuffers = decoder.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            int inputBufIndex = decoder.dequeueInputBuffer(-1);

            ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
            inputBuf.clear();
            inputBuf.put(data);
            decoder.queueInputBuffer(inputBufIndex, 0, data.length, 0, 0);

            int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);

            while (decoderStatus >= 0) {
                ByteBuffer outputFrame = decoderOutputBuffers[decoderStatus];
                outputFrame.position(info.offset);
                outputFrame.limit(info.offset + info.size);

                byte[] outData = new byte[info.size];
                outputFrame.get(outData);
                outputStream.write(outData);

                decoder.releaseOutputBuffer(decoderStatus, true /*render*/);    // TODO: change to false if not using surface
                decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        byte[] ret = outputStream.toByteArray();
        outputStream.reset();
        return ret;
    }


    public void close() {
        if (decoder == null) return;
        try {
            decoder.stop();
            decoder.release();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}

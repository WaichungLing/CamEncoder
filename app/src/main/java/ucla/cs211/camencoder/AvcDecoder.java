package ucla.cs211.camencoder;

import android.media.MediaCodec;

import java.io.IOException;

public class AvcDecoder {
    private final static String TAG = AvcEncoder.class.getSimpleName();
    private final static String MIME_TYPE = "video/avc";

    MediaCodec decoder;

    public AvcDecoder() {
    }

    public boolean init(){
        try{
            decoder = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (IOException e) {
            return false;
        }



        return true;
    }


}

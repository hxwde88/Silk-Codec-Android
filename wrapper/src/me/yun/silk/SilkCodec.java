package me.yun.silk;

public class SilkCodec {
    static {
        System.loadLibrary("silk");
    }

    public native int autoToSilk(String input, String output, int sampleRate);
    public native int silkToMp3(String input, String output, int sampleRate);
    public native int autoToPcm(String input, String output);
    public native int pcmToSilk(String input, String output, int sampleRate, int maxRate, int channels);
    public native int getFileType(String path);
    public native long getDuration(String path);
}

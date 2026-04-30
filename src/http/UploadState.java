package http;

import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class UploadState {
    public final Path tempFile;
    public final FileChannel out;
    public final long totalBodyBytes;
    public long written = 0;
    public final String boundary;
    public final boolean keepAlive;
    public final ResponseWriter writer;

    public UploadState(Path tempFile, FileChannel out,
                       long totalBodyBytes, String boundary,
                       boolean keepAlive, ResponseWriter writer) {
        this.tempFile       = tempFile;
        this.out            = out;
        this.totalBodyBytes = totalBodyBytes;
        this.boundary       = boundary;
        this.keepAlive      = keepAlive;
        this.writer         = writer;
    }
}
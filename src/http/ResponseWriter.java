package http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ResponseWriter {

    private final SelectionKey key;
    private final SocketChannel client;
    private final boolean keepAlive;

    public ResponseWriter(SelectionKey key, SocketChannel client, boolean keepAlive) {
        this.key = key;
        this.client = client;
        this.keepAlive = keepAlive;
    }

    public long transferFrom(FileChannel fc, long position, long count) throws IOException {
        return fc.transferTo(position, count, client);
    }

    public void write(byte[] data) throws IOException {
        client.write(ByteBuffer.wrap(data));
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void close() {
        try { key.cancel(); } catch (Exception ignored) {}
        try { client.close(); } catch (Exception ignored) {}
    }


    public void pauseReads() {
        key.interestOps(0);
    }

    public void resumeReads() {
        if (key.isValid()) {
            key.interestOps(SelectionKey.OP_READ);
            key.selector().wakeup();
        }
    }
}
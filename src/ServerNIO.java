import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class ServerNIO {
    private final int port;
    private final ReadHandler handler;

    @FunctionalInterface
    public interface ReadHandler {
        void handle(SelectionKey key) throws IOException;
    }

    public ServerNIO(int port, ReadHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    public ServerNIO() {
        this.port = 3000;
        this.handler = this::defaultEcho;
    }

    private void defaultEcho(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = client.read(buffer);
        System.out.println("Read bytes: " + bytesRead);
        if (bytesRead == -1) {
            client.close();
            System.out.println("Client disconnected");
            return;
        }
        buffer.flip();
        client.write(buffer);
    }

    public void start() {
        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("NIO server started on port " + port);
            while (true) {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (key.isAcceptable()) handleAccept(serverChannel, selector);
                    if (key.isReadable())   handler.handle(key);
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private void handleAccept(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        SocketChannel client = serverChannel.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, new ByteArrayOutputStream());
        System.out.println("Client connected: " + client.getRemoteAddress());
    }

    public static void main(String[] args) {
        new ServerNIO().start();
    }
}
//import java.io.*;
//import java.nio.ByteBuffer;
//import java.nio.channels.*;
//import java.net.InetSocketAddress;
//import java.util.Iterator;
//
//public class ServerNIO {
//    private final int port;
//
//    public ServerNIO(int port) {  // change 2: constructor takes port
//        this.port = port;
//    }
//
//    public ServerNIO() {
//        this.port = 3000;
//    }
//
//    public void start() {
//        try (Selector selector = Selector.open();
//             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
//            serverChannel.bind(new InetSocketAddress(port));  // use field
//            serverChannel.configureBlocking(false);
//            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
//            System.out.println("NIO server started on port " + port);
//            while (true) {
//                selector.select();
//                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
//                while (keys.hasNext()) {
//                    SelectionKey key = keys.next();
//                    keys.remove();
//                    if (key.isAcceptable()) handleAccept(serverChannel, selector);
//                    if (key.isReadable())   handleRead(key);
//                }
//            }
//        } catch (IOException e) {
//            System.err.println("Server error: " + e.getMessage());
//        }
//    }
//
//    protected void handleAccept(ServerSocketChannel serverChannel, Selector selector)  // change 3
//            throws IOException {
//        SocketChannel client = serverChannel.accept();
//        client.configureBlocking(false);
//        client.register(selector, SelectionKey.OP_READ, new StringBuilder());
//        System.out.println("Client connected: " + client.getRemoteAddress());
//    }
//
//    protected void handleRead(SelectionKey key) throws IOException {  // change 3
//        SocketChannel client = (SocketChannel) key.channel();
//        ByteBuffer buffer = ByteBuffer.allocate(1024);
//        int bytesRead = client.read(buffer);
//        if (bytesRead == -1) {
//            client.close();
//            System.out.println("Client disconnected");
//            return;
//        }
//        buffer.flip();
//        client.write(buffer);  // echo behavior stays here
//    }
//
//    public static void main(String[] args) {
//        new ServerNIO().start();  // old behavior unchanged
//    }
//}
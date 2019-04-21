package com.zavier.nio.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

public class MultiplexerTimeServer implements Runnable {

    private Selector selector;

    private ServerSocketChannel servChannel;

    private volatile boolean stop;

    public MultiplexerTimeServer(int port) {
        try {
            // 创建Selector与ServerSocketChannel
            selector = Selector.open();
            servChannel = ServerSocketChannel.open();
            // 设置为非阻塞
            servChannel.configureBlocking(false);
            // 绑定监听端口 与设置 backlog(处理队列长度)
            servChannel.socket().bind(new InetSocketAddress(port), 1024);
            // 将serverSocketChannel 注册到 Selector，监听 Accept事件
            servChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("The time server is start in port:" + port);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void stop() {
        stop = true;
    }

    @Override
    public void run() {
        while (!stop) {
            try {
                System.out.println("start");
                // 休眠最多1000毫秒，除非有channel被选中时唤醒
                selector.select();
                selector.select(90000);
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                SelectionKey key = null;
                while (iterator.hasNext()) {
                    key = iterator.next();
                    iterator.remove();
                    try {
                        handleInput(key);
                    } catch (Exception e) {
                        if (key != null) {
                            key.cancel();
                            if (key.channel() != null) {
                                key.channel().close();
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        if (selector != null) {
            try {
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleInput(SelectionKey key) throws Exception {
        if (key.isValid()) {
            // 如果有新连接建立
            if (key.isAcceptable()) {
                ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                SocketChannel sc = ssc.accept();
                sc.configureBlocking(false);
                // 将对应SocketChannel注册到Selector, 监听读事件
                sc.register(selector, SelectionKey.OP_READ);
            }

            // 如果可读
            if (key.isReadable()) {
                SocketChannel sc = (SocketChannel) key.channel();
                // 分配1024个字节的buffer,将数据从channel写入到其中
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                int readBytes = sc.read(readBuffer);
                if (readBytes > 0) {
                    // 将buffer改为读状态
                    readBuffer.flip();
                    byte[] bytes = new byte[readBuffer.remaining()];
                    // 将buffer中的数据读取到字节数组中
                    readBuffer.get(bytes);
                    // 处理字节数组中的消息
                    String body = new String(bytes, "UTF-8");
                    System.out.println("The time server receive order:" + body);
                    String currentTime = "QUERY TIME ORDER".equalsIgnoreCase(body) ? new Date().toString() : "BAD ORDER";
                    doWrite(sc, currentTime);
                } else if (readBytes < 0) {
                    key.cancel();
                    sc.close();
                }
            }
        }
    }

    private void doWrite(SocketChannel channel, String response) throws IOException {
        if (response != null && response.trim().length() > 0) {
            byte[] bytes = response.getBytes();
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
            // 将数据写入buffer
            writeBuffer.put(bytes);
            // 将buffer转为读状态
            writeBuffer.flip();
            // 读取buffer中数据，写入到channel
            channel.write(writeBuffer);
        }
    }
}

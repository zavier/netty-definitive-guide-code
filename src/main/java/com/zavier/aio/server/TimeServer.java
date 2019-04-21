package com.zavier.aio.server;

public class TimeServer {
    public static void main(String[] args) {
        int port = 8080;
        AsyncTimeServerHandler timeServerHandler = new AsyncTimeServerHandler(port);
        new Thread(timeServerHandler, "AsyncTimeServerHandler-001").start();
    }
}

package com.kasaneta.statsapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class WebServer {
    private static HttpServer server;
    private static ThreadPoolExecutor threadPoolExecutor;
    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public static void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(Config.PORT.get()), 0);
            server.createContext("/stats", WebServer::handleStats);
            threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
            server.setExecutor(threadPoolExecutor);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            threadPoolExecutor.shutdown();
        }
    }

    private static void handleStats(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            byte[] bytes = DataCollector.getCachedJsonBytes();
            
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }
}
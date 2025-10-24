package com.cs6650.chat.client.send;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

final class ReliableWebSocketClient {

    private final URI uri;
    private final Duration connectTimeout;
    private final HttpClient httpClient;

    private volatile WebSocket webSocket;
    private volatile CompletableFuture<String> pendingResponse = new CompletableFuture<>();

    ReliableWebSocketClient(URI uri, Duration connectTimeout) {
        this.uri = uri;
        this.connectTimeout = connectTimeout;
        this.httpClient = HttpClient.newHttpClient();
    }

    synchronized void connect() {
        close();
        webSocket = httpClient.newWebSocketBuilder()
                .connectTimeout(connectTimeout)
                .buildAsync(uri, new ResponseListener())
                .join();
    }

    synchronized void reconnect() {
        connect();
    }

    synchronized String sendAndAwait(String payload, Duration timeout) {
        ensureOpen();
        pendingResponse = new CompletableFuture<>();
        webSocket.sendText(payload, true).join();
        try {
            return pendingResponse.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).join();
        } catch (Exception e) {
            pendingResponse.completeExceptionally(e);
            throw new RuntimeException("WebSocket send/receive failed", e);
        }
    }

    synchronized boolean isOpen() {
        return webSocket != null && !webSocket.isOutputClosed();
    }

    synchronized void close() {
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
            } catch (Exception ignored) {
                // best effort
            } finally {
                webSocket = null;
            }
        }
    }

    private void ensureOpen() {
        if (!isOpen()) {
            throw new IllegalStateException("WebSocket is not open");
        }
    }

    private final class ResponseListener implements Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            pendingResponse.complete(data.toString());
            return Listener.super.onText(webSocket, data, last);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            pendingResponse.completeExceptionally(error);
            Listener.super.onError(webSocket, error);
        }
    }
}

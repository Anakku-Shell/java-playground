package dev.playground.library.openlibrary;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * A real HTTP server on a free local port standing in for openlibrary.org: the JDK's own
 * {@code com.sun.net.httpserver}, so no extra dependency. A mock of the client
 * ({@code MockRestServiceServer}) never opens a socket, so it cannot show a read timeout or a
 * redirect being followed; this can. Paths without a route answer 404, like Open Library.
 * Guide: §5.9 Beyond CRUD.
 */
final class OpenLibraryStub implements AutoCloseable {

    private record Route(int status, String body, String location, Duration delay) {}

    private final HttpServer server;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();

    private OpenLibraryStub(HttpServer server) {
        this.server = server;
    }

    static OpenLibraryStub start() {
        try {
            // Port 0: the operating system picks a free one, so parallel builds never collide.
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            OpenLibraryStub stub = new OpenLibraryStub(server);
            server.createContext("/", stub::handle);
            // One virtual thread per request: a slow route does not block the next request.
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return stub;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void json(String path, String body) {
        routes.put(path, new Route(200, body, null, Duration.ZERO));
    }

    void html(String path, String body) {
        routes.put(path, new Route(200, body, null, Duration.ZERO));
    }

    void status(String path, int status) {
        routes.put(path, new Route(status, "", null, Duration.ZERO));
    }

    void redirect(String path, String location) {
        routes.put(path, new Route(302, "", location, Duration.ZERO));
    }

    void slow(String path, Duration delay, String body) {
        routes.put(path, new Route(200, body, null, delay));
    }

    void reset() {
        routes.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Route route = routes.getOrDefault(exchange.getRequestURI().getPath(), new Route(404, "", null, Duration.ZERO));
        try (exchange) {
            if (route.delay().isPositive()) {
                Thread.sleep(route.delay());
            }
            if (route.location() != null) {
                exchange.getResponseHeaders().add("Location", route.location());
            }
            byte[] body = route.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders()
                    .add(
                            "Content-Type",
                            body.length > 0 && route.body().startsWith("{") ? "application/json" : "text/html");
            // -1: no body at all; otherwise its exact length.
            exchange.sendResponseHeaders(route.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // The client gave up (a timeout) and closed the connection: nothing to answer.
        }
    }
}

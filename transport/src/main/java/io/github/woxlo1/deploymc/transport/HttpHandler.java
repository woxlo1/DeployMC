package io.github.woxlo1.deploymc.transport;

/** Application callback for one HTTP request. Implementations must not block transport threads. */
public interface HttpHandler {

  HttpResponse handle(HttpRequest request) throws Exception;
}

package filters;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Iterator;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.http.HttpEntity;
import play.mvc.EssentialAction;
import play.mvc.EssentialFilter;
import play.mvc.Http;

/** This is a simple filter that produces an apache-style access log. */
@Singleton
public class LoggingFilter extends EssentialFilter {

  private final Executor exec;
  private final Clock clock;
  private static final Logger log = LoggerFactory.getLogger("loggingfilter");

  /** @param exec This class is needed to execute code asynchronously. */
  @Inject
  public LoggingFilter(Executor exec, Clock clock) {
    this.exec = checkNotNull(exec);
    this.clock = checkNotNull(clock);
  }

  @Override
  public EssentialAction apply(EssentialAction next) {
    return EssentialAction.of(
        request -> {
          final long startTime = clock.millis();
          return next.apply(request)
              .map(
                  result -> {
                    long time = clock.millis() - startTime;
                    log.info(
                        "{}\t{}\t{}ms\t{}", request.method(), request.uri(), time, result.status());
                    StringBuilder requestCookies = new StringBuilder();
                    Iterator<Http.Cookie> requestCookieIterator = request.cookies().iterator();
                    while (requestCookieIterator.hasNext()) {
                      Http.Cookie cookie = requestCookieIterator.next();
                      requestCookies.append(
                          String.format("key: %s, value: %s\n", cookie.name(), cookie.value()));
                    }
                    log.trace("request cookies: {}", requestCookies.toString());
                    log.trace("response headers: {}", result.headers().toString());
                    if (result.body() instanceof HttpEntity.Strict) {
                      log.trace(
                          "response body: {}",
                          ((HttpEntity.Strict) result.body())
                              .data()
                              .decodeString(StandardCharsets.UTF_8));
                    }

                    StringBuilder responseCookies = new StringBuilder();
                    Iterator<Http.Cookie> responseCookieIterator = result.cookies().iterator();
                    while (responseCookieIterator.hasNext()) {
                      Http.Cookie cookie = responseCookieIterator.next();
                      responseCookies.append(
                          String.format("key: %s, value: %s\n", cookie.name(), cookie.value()));
                    }
                    log.trace("response cookies: {}", responseCookies.toString());
                    return result.withHeader("RequestTime", String.valueOf(time));
                  },
                  exec);
        });
  }
}

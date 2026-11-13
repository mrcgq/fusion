package ctbrec;

import kotlin.Pair;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LoggingInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public Response intercept(Chain chain) throws IOException {
        long t1 = System.nanoTime();
        Request request = chain.request();
        LOG.debug("OkHttp Sending request {} on {}", request.url(), chain.connection());
        for (Pair<?, ?> header : request.headers()) {
            LOG.debug("{}: {}", header.getFirst(), header.getSecond());
        }
        if (request.method().equalsIgnoreCase("POST")) {
            LOG.debug("Body: {}", request.body());
        }
        Response response = chain.proceed(request);
        long t2 = System.nanoTime();
        LOG.debug("OkHttp Received {} response for {} in {}ms", response.code(), response.request().url(), (t2 - t1) / 1e6d);
        for (Pair<?, ?> header : response.headers()) {
            LOG.debug("{}: {}", header.getFirst(), header.getSecond());
        }
        return response;
    }
}

package ctbrec.io;

import ctbrec.io.FlaresolverrResponse;
import ctbrec.io.FlaresolverrSolutionResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Getter;
import lombok.Setter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Cookie;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;


public class FlaresolverrClient {
    public String api_url;
    protected int timeout_ms;
    protected OkHttpClient client;
    protected JsonMapper mapper = new JsonMapper();

    @Getter
    protected String sessionName = "";

    public FlaresolverrClient() {
        this("http://localhost:8191/v1", 60000);
    }

    public FlaresolverrClient(String apiUrl, int timeout_ms) {
        api_url = apiUrl;
        this.timeout_ms = timeout_ms;
        client = new OkHttpClient.Builder()
            .callTimeout(timeout_ms + 10000, TimeUnit.MILLISECONDS)
            .readTimeout(timeout_ms + 1000, TimeUnit.MILLISECONDS)
            .build();
    }
    
    public CompletableFuture<FlaresolverrResponse> createSession(String name) throws IOException {
        if (!sessionName.equals("")) 
            throw new IOException("Cannot start new session because another one is already started. Finish it before creating a new one");
        
        sessionName = name;
        var body = mapper.createObjectNode()
            .put("cmd", "sessions.create")
            .put("session", name);

        return makeApiCall(body).thenApply(r -> new FlaresolverrResponse(r));
    }

    public CompletableFuture<FlaresolverrResponse> destroySession(String name) throws IOException {
        if (sessionName.equals(""))
            throw new IOException("Cannot destroy session because no session is active");
        
        var body = mapper.createObjectNode()
            .put("cmd", "sessions.destroy")
            .put("session", name);

        sessionName = "";
        return makeApiCall(body).thenApply(r -> new FlaresolverrResponse(r));
    }

    public CompletableFuture<FlaresolverrSolutionResponse> getCookies(String url) throws IOException {
        var body = mapper.createObjectNode()
            .put("cmd", "request.get")
            .put("url", url)
            .put("maxTimeout", timeout_ms)
            .put("returnOnlyCookies", true);

        if (sessionName != "") {
            body.put("session", sessionName);
        }

        return makeApiCall(body).thenApply(r -> new FlaresolverrSolutionResponse(r));
    }

    protected CompletableRequestFuture<JsonNode> makeApiCall(ObjectNode body) throws IOException {
        var requestBody = RequestBody.create(mapper.writeValueAsString(body), MediaType.get("application/json"));
        var request = new Request.Builder()
            .url(api_url)
            .post(requestBody)
            .build();
        
        var call = client.newCall(request);
        var future = new CompletableRequestFuture<JsonNode>(call) {
            @Override
            public void processBody(ResponseBody body) throws IOException {
                complete(mapper.readTree(body.charStream()));                
            }
        };
        
        // FIXME?: unfortunate cyclic reference here to allow cancelling through the future, is this bad?
        call.enqueue(future);
        return future;
    }
}

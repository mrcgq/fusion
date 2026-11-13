package ctbrec.io;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import lombok.Getter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class CompletableRequestFuture<T> extends CompletableFuture<T> implements Callback {
    @Getter
    protected Call call;
    
    public CompletableRequestFuture(Call call) {
        this.call = call;
    }
    
    @Override
    public void onResponse(Call c, Response response) throws IOException {
        try (var body = response.body()) {
            if (response.isSuccessful()) {
                processBody(body);
            } else {
                completeExceptionally(new HttpException(response.code(), response.message()));
            }
        } catch (Exception e) {
            completeExceptionally(e);
        }
    }

    @Override
    public void onFailure(Call c, IOException error) {            
        completeExceptionally(error);
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        call.cancel();
        return super.cancel(mayInterruptIfRunning);
    }
    
    
    protected void processBody(ResponseBody body) throws Exception {}
}
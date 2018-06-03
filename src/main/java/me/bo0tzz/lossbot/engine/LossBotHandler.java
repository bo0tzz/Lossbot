package me.bo0tzz.lossbot.engine;

import com.google.gson.Gson;
import com.jtelegram.api.events.EventHandler;
import com.jtelegram.api.events.message.PhotoMessageEvent;
import com.jtelegram.api.requests.message.framework.ReplyMarkup;
import com.jtelegram.api.requests.message.send.SendText;
import me.bo0tzz.lossbot.LossBot;
import me.bo0tzz.lossbot.bean.PredictionResult;
import me.bo0tzz.lossbot.config.Configuration;
import okhttp3.*;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class LossBotHandler implements EventHandler<PhotoMessageEvent> {

    private final LossBot lossBot;
    private final Configuration configuration;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public static final MediaType MULTIPART = MediaType.parse("multipart/form-data");
    public static final String FORMAT = "I am %.2f% sure that your image is %s.";

    public LossBotHandler(LossBot lossBot, Configuration configuration) {
        this.lossBot = lossBot;
        this.configuration = configuration;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
    }

    @Override
    public void onEvent(PhotoMessageEvent event) {
        try {

            InputStream download = lossBot.getBot().downloadFile(event.getMessage().getHighestResolutionPhoto().getFileId());
            byte[] image = IOUtils.toByteArray(download);

            RequestBody body = RequestBody.create(MULTIPART, image);
            Request request = new Request.Builder()
                    .url(configuration.getApiUrl())
                    .post(body)
                    .build();

            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException(response.message());
            }

            PredictionResult result = gson.fromJson(response.body().string(), PredictionResult.class);

            TreeMap<Double, String> predictions = new TreeMap<>();
            result.getPredictions().forEach(p -> predictions.put(p.getProbability(), p.getTagName()));

            Map.Entry<Double, String> entry = predictions.firstEntry();
            String message = String.format(FORMAT, entry.getKey() * 100, entry.getValue());

            lossBot.getBot().perform(SendText.builder()
                    .chatId(event.getMessage().getChat().getChatId())
                    .replyToMessageID(event.getMessage().getMessageId())
                    .text(message)
                    .build());

        } catch (Exception e) {
            e.printStackTrace();
            lossBot.getBot().perform(SendText.builder()
                    .chatId(event.getMessage().getChat().getChatId())
                    .text("I tried to figure out if your image is loss, but I had a miscarriage along the way :(")
                    .build());
        }
    }

}

package cn.bigfish.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class VisionClient {
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();

    private String send(Request request) throws Exception {
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("视觉接口返回 HTTP " + response.code() + "，请检查权限和配置");
            if (response.body() == null) throw new IOException("视觉接口未返回内容");
            if (response.body().contentLength() > 1024 * 1024) throw new IOException("视觉接口返回内容过大");
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            java.io.InputStream input = response.body().byteStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 1024 * 1024) throw new IOException("视觉接口返回内容过大");
                output.write(buffer, 0, count);
            }
            byte[] bytes = output.toByteArray();
            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) throw new IOException("视觉接口返回空内容");
            return content;
        }
    }

    public String compatible(byte[] photo, String question, Settings config) throws Exception {
        config.validate();
        if (!Protocol.validUrl(config.completionUrl, false) || config.model.isEmpty() || config.apiKey.isEmpty())
            throw new IllegalArgumentException("请填写完整视觉接口地址、模型名称和 API Key");
        JSONArray content = new JSONArray().put(new JSONObject().put("type", "text").put("text", question))
            .put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(photo))));
        JSONObject body = new JSONObject().put("model", config.model).put("max_tokens", config.detail ? 700 : 384)
            .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", content)));
        Request request = new Request.Builder().url(config.completionUrl).header("Authorization", "Bearer " + config.apiKey)
            .post(RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8"))).build();
        JSONObject answer = new JSONObject(send(request));
        Object text = answer.getJSONArray("choices").getJSONObject(0).getJSONObject("message").get("content");
        if (!(text instanceof String) || ((String) text).trim().isEmpty()) throw new IOException("视觉接口未返回有效文字");
        return (String) text;
    }

    public String official(byte[] photo, String question, JSONObject vision, Settings config, String clientId) throws Exception {
        JSONObject valid = Protocol.vision(vision);
        if (valid == null) throw new IllegalArgumentException("官方尚未向此 MCP 会话下发视觉接口");
        MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("question", question)
            .addFormDataPart("file", "phone.jpg", RequestBody.create(photo, MediaType.get("image/jpeg"))).build();
        Request.Builder request = new Request.Builder().url(valid.getString("url")).header("Client-Id", clientId).post(body);
        if (!valid.optString("token").isEmpty()) request.header("Authorization", "Bearer " + valid.getString("token"));
        if (!config.deviceId.isEmpty()) request.header("Device-Id", config.deviceId);
        return send(request.build());
    }
}

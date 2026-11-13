package cn.bigfish.phone;

import org.json.JSONObject;

public final class Settings {
    public final String endpoint;
    public final String completionUrl;
    public final String model;
    public final String apiKey;
    public final String deviceId;
    public final String contacts;
    public final boolean detail;
    public final boolean useCustom;

    public Settings(JSONObject json) {
        endpoint = json.optString("endpoint").trim();
        completionUrl = json.optString("completion_url").trim();
        model = json.optString("model").trim();
        apiKey = json.optString("api_key").trim();
        deviceId = json.optString("device_id").trim();
        contacts = json.optString("contacts");
        detail = json.optBoolean("detail", false);
        useCustom = json.optBoolean("use_custom", false);
    }

    public void validate() {
        if (!endpoint.isEmpty() && !Protocol.validUrl(endpoint, true)) throw new IllegalArgumentException("MCP 接入点需要完整 wss:// 地址");
        if (!completionUrl.isEmpty() && !Protocol.validUrl(completionUrl, false)) throw new IllegalArgumentException("视觉接口需要完整 HTTP / HTTPS 地址");
        if (apiKey.contains("\n") || apiKey.contains("\r") || deviceId.contains("\n") || deviceId.contains("\r")) throw new IllegalArgumentException("密钥和 Device-Id 不可包含换行");
        Protocol.contacts(contacts);
    }

    public JSONObject json() throws Exception {
        return new JSONObject().put("endpoint", endpoint).put("completion_url", completionUrl).put("model", model)
            .put("api_key", apiKey).put("device_id", deviceId).put("contacts", contacts).put("detail", detail).put("use_custom", useCustom);
    }
}

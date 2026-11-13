package cn.bigfish.phone;

import org.json.JSONObject;

public final class WebConfig {
    private WebConfig() {}

    public static Settings merge(Settings current, JSONObject values) throws Exception {
        JSONObject stored = current.json();
        for (String key : new String[]{"completion_url", "model", "device_id", "contacts", "detail", "use_custom"}) if (values.has(key)) stored.put(key, values.get(key));
        for (String key : new String[]{"endpoint", "api_key"}) {
            if (values.optBoolean("clear_" + key, false)) stored.put(key, "");
            else if (!values.optString(key).trim().isEmpty()) stored.put(key, values.getString(key));
        }
        Settings result = new Settings(stored);result.validate();return result;
    }

    public static JSONObject visible(Settings current, String status) throws Exception {
        JSONObject result = current.json();result.remove("endpoint");result.remove("api_key");
        return result.put("endpoint_set", !current.endpoint.isEmpty()).put("api_key_set", !current.apiKey.isEmpty()).put("status", status);
    }
}

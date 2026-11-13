package cn.bigfish.phone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Protocol {
    private Protocol() {}

    public static boolean validUrl(String value, boolean websocket) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null
                && (websocket ? "wss".equals(scheme) : "http".equals(scheme) || "https".equals(scheme));
        } catch (Exception error) { return false; }
    }

    public static JSONObject vision(JSONObject value) {
        if (value == null || !validUrl(value.optString("url"), false)) return null;
        String token = value.optString("token", "");
        if (token.contains("\r") || token.contains("\n")) return null;
        return value;
    }

    public static JSONObject requestVision(JSONObject params) {
        JSONObject meta = params.optJSONObject("_meta");
        JSONObject capabilities = meta == null ? null : meta.optJSONObject("io.modelcontextprotocol/clientCapabilities");
        JSONObject extensions = capabilities == null ? null : capabilities.optJSONObject("extensions");
        return extensions == null ? null : vision(extensions.optJSONObject("me.xiaozhi/vision"));
    }

    public static String normalizeNumber(String value) {
        if (value == null || value.length() > 60 || !value.matches("[+0-9 ()-]+"))
            throw new IllegalArgumentException("号码仅支持数字、开头的 +、空格、括号和短横线，不支持分机或拨号代码");
        String number = value.replaceAll("[ ()-]", "");
        if (!number.matches("\\+?[0-9]{3,20}")) throw new IllegalArgumentException("请输入 3～20 位电话号码");
        return number;
    }

    public static Map<String, String> contacts(String input) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : input.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("=", -1);
            if (parts.length != 2 || parts[0].trim().isEmpty()) throw new IllegalArgumentException("联系人每行填写：称呼=电话号码");
            String name = parts[0].trim();
            if (result.containsKey(name)) throw new IllegalArgumentException("联系人称呼不能重复");
            result.put(name, normalizeNumber(parts[1].trim()));
        }
        return result;
    }

    public static String resolveNumber(String recipient, String contactText) {
        String value = recipient.trim();
        Map<String, String> saved = contacts(contactText);
        return saved.containsKey(value) ? saved.get(value) : normalizeNumber(value);
    }

    public static JSONObject text(String text, boolean error) throws JSONException {
        return new JSONObject().put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", text))).put("isError", error);
    }

    public static JSONObject response(JSONObject request, JSONObject result) throws JSONException {
        JSONObject params = request.optJSONObject("params");
        JSONObject meta = params == null ? null : params.optJSONObject("_meta");
        boolean modern = "server/discover".equals(request.optString("method")) || meta != null &&
            (meta.has("io.modelcontextprotocol/protocolVersion") || meta.has("io.modelcontextprotocol/clientCapabilities"));
        if (modern) {
            JSONObject envelope = new JSONObject().put("resultType", "complete").put("_meta", new JSONObject()
                .put("io.modelcontextprotocol/serverInfo", new JSONObject().put("name", "companion").put("version", BuildConfig.VERSION_NAME)));
            java.util.Iterator<String> keys = result.keys();
            while (keys.hasNext()) { String key = keys.next();envelope.put(key, result.get(key)); }
            if ("tools/list".equals(request.optString("method"))) envelope.put("ttlMs", 60000).put("cacheScope", "private");
            result = envelope;
        }
        return new JSONObject().put("jsonrpc", "2.0").put("id", request.opt("id") == null ? JSONObject.NULL : request.opt("id")).put("result", result);
    }

    public static JSONArray tools() throws JSONException {
        JSONArray tools = new JSONArray();
        tools.put(tool("self.camera.take_photo", "从手机常开相机抓拍一帧并识图，支持用户已开启的后台相机服务。失败时如实提示，不能编造。", "question", "用户关于当前画面的具体问题"));
        tools.put(new JSONObject().put("name", "phone_camera.get_status").put("description", "检查手机相机和连接状态，不包含密钥或联系人号码")
            .put("inputSchema", new JSONObject().put("type", "object").put("properties", new JSONObject()).put("additionalProperties", false)));
        tools.put(tool("self.phone.dial", "用户要求打电话时，直接通过手机电话服务呼叫，无需再次询问或在伴侣应用确认。recipient 为用户指定的已保存联系人称呼或完整号码，不能猜测号码。手机需预先授予电话权限；系统可能要求选择SIM卡。工具成功仅表示提交系统，不代表已经接通。", "recipient", "已保存的联系人称呼或电话号码，不支持 USSD 和分机"));
        tools.put(new JSONObject().put("name", "self.touch.get_recent").put("description", "读取手机伴侣应用触摸页最近五分钟的轻点、长按和抚摸。在用户询问触摸、抱抱或互动时调用，依据真实事件温柔回应；无事件则如实告知。不能测量温度或力度，也不会主动唤醒小智。读取不清空，使用事件id避免重复回应。")
            .put("inputSchema", new JSONObject().put("type", "object").put("properties", new JSONObject()
                .put("after_id", new JSONObject().put("type", "integer").put("minimum", 0).put("description", "只读取此id之后的事件；首次省略，应用重启后id重新开始"))
                .put("limit", new JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 32).put("description", "返回最新事件数，默认6，最多32")))
                .put("additionalProperties", false)));
        return tools;
    }

    private static JSONObject tool(String name, String description, String argument, String help) throws JSONException {
        return new JSONObject().put("name", name).put("description", description).put("inputSchema", new JSONObject()
            .put("type", "object").put("properties", new JSONObject().put(argument, new JSONObject().put("type", "string").put("description", help)))
            .put("required", new JSONArray().put(argument)).put("additionalProperties", false));
    }
}

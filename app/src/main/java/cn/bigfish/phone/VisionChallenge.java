package cn.bigfish.phone;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public final class VisionChallenge {
    public final Bitmap bitmap;
    public final byte[] jpeg;
    public final JSONObject expected = new JSONObject();
    public static final String QUESTION = "Inspect the attached image. Identify the color and shape at left, center, right and read the four-digit code. Return ONLY JSON: {\"left\":{\"color\":\"...\",\"shape\":\"...\"},\"center\":{\"color\":\"...\",\"shape\":\"...\"},\"right\":{\"color\":\"...\",\"shape\":\"...\"},\"code\":\"....\"}. Colors: red, green, blue. Shapes: circle, square, triangle. Use only what you can see, do not guess.";

    public VisionChallenge() throws Exception {
        bitmap = Bitmap.createBitmap(720, 360, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ArrayList<String> colors = new ArrayList<>(Arrays.asList("red", "green", "blue"));
        ArrayList<String> shapes = new ArrayList<>(Arrays.asList("circle", "square", "triangle"));
        SecureRandom random = new SecureRandom();Collections.shuffle(colors, random);Collections.shuffle(shapes, random);
        String[] positions = {"left", "center", "right"};
        for (int index = 0; index < 3; index++) {
            float left = 45 + index * 240;
            String color = colors.get(index);String shape = shapes.get(index);
            paint.setColor(Color.parseColor(color.equals("red") ? "#e02020" : color.equals("green") ? "#159530" : "#204ce0"));
            if (shape.equals("circle")) canvas.drawCircle(left + 75, 110, 75, paint);
            else if (shape.equals("square")) canvas.drawRect(left, 35, left + 150, 185, paint);
            else {
                Path path = new Path();path.moveTo(left + 75, 35);path.lineTo(left, 185);path.lineTo(left + 150, 185);path.close();canvas.drawPath(path, paint);
            }
            expected.put(positions[index], new JSONObject().put("color", color).put("shape", shape));
        }
        String code = Integer.toString(1000 + random.nextInt(9000));expected.put("code", code);
        paint.setColor(Color.BLACK);paint.setTextSize(55);canvas.drawText("Code: " + code, 155, 305, paint);
        ByteArrayOutputStream output = new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);jpeg = output.toByteArray();
    }

    public static boolean matches(String answer, JSONObject expected) {
        try {
            JSONObject actual = new JSONObject(answer.substring(answer.indexOf('{'), answer.lastIndexOf('}') + 1));
            for (String position : new String[]{"left", "center", "right"}) {
                for (String key : new String[]{"color", "shape"}) {
                    if (!actual.getJSONObject(position).getString(key).trim().toLowerCase(Locale.ROOT).equals(expected.getJSONObject(position).getString(key))) return false;
                }
            }
            return actual.getString("code").trim().equals(expected.getString("code"));
        } catch (Exception error) { return false; }
    }
}

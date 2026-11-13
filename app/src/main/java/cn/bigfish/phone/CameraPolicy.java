package cn.bigfish.phone;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;

public final class CameraPolicy {
    private CameraPolicy() {}

    public static String select(String[] identifiers, int[] facings, boolean front) {
        int desired = front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        for (int index = 0; index < identifiers.length; index++) if (facings[index] == desired) return identifiers[index];
        throw new IllegalStateException(front ? "没有可用的前置摄像头" : "没有可用的后置摄像头");
    }

    public static int autofocus(int[] modes) {
        if (modes != null) {
            for (int mode : modes) if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) return mode;
            for (int mode : modes) if (mode == CaptureRequest.CONTROL_AF_MODE_AUTO) return mode;
        }
        return CaptureRequest.CONTROL_AF_MODE_OFF;
    }

    public static int jpegOrientation(int sensor, int displayRotation, boolean front) {
        return (sensor + (front ? displayRotation : -displayRotation) + 360) % 360;
    }
}

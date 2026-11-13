package cn.bigfish.phone;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import org.junit.Test;
import static org.junit.Assert.*;

public class CameraPolicyTest {
    @Test public void facingUsesCharacteristicsInsteadOfCameraIdNumbers() {
        String[] identifiers = {"external", "selfie", "rear-wide", "rear-tele"};
        int[] facings = {CameraCharacteristics.LENS_FACING_EXTERNAL, CameraCharacteristics.LENS_FACING_FRONT,
            CameraCharacteristics.LENS_FACING_BACK, CameraCharacteristics.LENS_FACING_BACK};
        assertEquals("selfie", CameraPolicy.select(identifiers, facings, true));
        assertEquals("rear-wide", CameraPolicy.select(identifiers, facings, false));
    }

    @Test public void missingRequestedLensDoesNotFallBackToWrongView() {
        for (boolean front : new boolean[]{false, true}) {
            int opposite = front ? CameraCharacteristics.LENS_FACING_BACK : CameraCharacteristics.LENS_FACING_FRONT;
            try { CameraPolicy.select(new String[]{"only-camera"}, new int[]{opposite}, front);fail(); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains(front ? "前置" : "后置")); }
        }
        try { CameraPolicy.select(new String[]{}, new int[]{}, true);fail(); } catch (IllegalStateException expected) {}
    }

    @Test public void fixedFocusFrontCameraDoesNotReceiveUnsupportedAutofocus() {
        assertEquals(CaptureRequest.CONTROL_AF_MODE_OFF, CameraPolicy.autofocus(new int[]{CaptureRequest.CONTROL_AF_MODE_OFF}));
        assertEquals(CaptureRequest.CONTROL_AF_MODE_OFF, CameraPolicy.autofocus(null));
        assertEquals(CaptureRequest.CONTROL_AF_MODE_AUTO, CameraPolicy.autofocus(new int[]{CaptureRequest.CONTROL_AF_MODE_OFF, CaptureRequest.CONTROL_AF_MODE_AUTO}));
        assertEquals(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE, CameraPolicy.autofocus(new int[]{CaptureRequest.CONTROL_AF_MODE_AUTO, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE}));
    }

    @Test public void jpegRotationAccountsForOppositeFrontSensorDirection() {
        int[] rotations = {0, 90, 180, 270};int[] back = {90, 0, 270, 180};int[] front = {270, 0, 90, 180};
        for (int index = 0; index < rotations.length; index++) {
            assertEquals(back[index], CameraPolicy.jpegOrientation(90, rotations[index], false));
            assertEquals(front[index], CameraPolicy.jpegOrientation(270, rotations[index], true));
        }
    }
}

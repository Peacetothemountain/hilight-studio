package com.hilight.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class BilateralDirectionTest {

    @Test
    public void waveBilateralProducesSymmetricalOutput() throws Exception {
        JSONObject config = new JSONObject()
                .put("mode", "wave")
                .put("speedMs", 1000)
                .put("color", 0xFF00E5FFL)
                .put("direction", "bilateral");

        Renderer renderer = new Renderer();
        int[] frame = renderer.frame(config, 250, 8);

        // Bilateral symmetry on an 8-LED bar: (0,7), (1,6), (2,5), (3,4) must match
        assertEquals(frame[0], frame[7]);
        assertEquals(frame[1], frame[6]);
        assertEquals(frame[2], frame[5]);
        assertEquals(frame[3], frame[4]);
    }

    @Test
    public void chaseBilateralAnimatesSymmetricallyFromCenter() throws Exception {
        JSONObject config = new JSONObject()
                .put("mode", "chase")
                .put("speedMs", 800)
                .put("color", 0xFF7C4DFFL)
                .put("direction", "bilateral");

        Renderer renderer = new Renderer();
        // At t=0, the center pair (LED 3 and 4) should be lit
        int[] frame0 = renderer.frame(config, 0, 8);
        assertEquals(frame0[3], frame0[4]);
        assertEquals(0xFF7C4DFF, frame0[3]);
        assertEquals(0xFF000000, frame0[0]);
        assertEquals(0xFF000000, frame0[7]);
    }
}

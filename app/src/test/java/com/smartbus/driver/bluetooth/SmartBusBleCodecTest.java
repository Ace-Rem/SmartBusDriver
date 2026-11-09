package com.smartbus.driver.bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class SmartBusBleCodecTest {

    @Test
    public void decodesExactlyTheTwoBusinessIds() {
        DriverBlePayload payload = SmartBusBleCodec.decode(
                "#1 2".getBytes(StandardCharsets.UTF_8));

        assertNotNull(payload);
        assertEquals(Long.valueOf(1L), payload.getRouteId());
        assertEquals(Long.valueOf(2L), payload.getDestinationStopId());
    }

    @Test
    public void rejectsExtraFieldsAndAmbiguousSeparators() {
        assertNull(SmartBusBleCodec.decode("#1 2 passenger".getBytes(StandardCharsets.UTF_8)));
        assertNull(SmartBusBleCodec.decode("#1  2".getBytes(StandardCharsets.UTF_8)));
        assertNull(SmartBusBleCodec.decode("SB1;route=1;dest=2".getBytes(StandardCharsets.UTF_8)));
    }
}

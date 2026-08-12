/*
 * Copyright © 2025-2026 Dezz (https://github.com/DezzK)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dezz.gnssshare.shared;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public class ProtocolTest {

    @Test
    public void buildPacket_writesVersionTypeAndPayload() {
        byte[] payload = {10, 20, 30};
        byte[] packet = Protocol.buildPacket(Protocol.TYPE_RESPONSE, payload);

        assertEquals(Protocol.HEADER_BYTES + payload.length, packet.length);
        assertEquals(Protocol.VERSION, packet[0] & 0xFF);
        assertEquals(Protocol.TYPE_RESPONSE, packet[1]);
        assertArrayEquals(payload, Arrays.copyOfRange(packet, Protocol.HEADER_BYTES, packet.length));
    }

    @Test
    public void buildPacket_nullPayload_isHeaderOnly() {
        byte[] packet = Protocol.buildPacket(Protocol.TYPE_HELLO, null);
        assertEquals(Protocol.HEADER_BYTES, packet.length);
        assertEquals(Protocol.TYPE_HELLO, packet[1]);
    }

    @Test
    public void parse_roundTripsBuildPacket() {
        byte[] payload = {1, 2, 3, 4};
        byte[] packet = Protocol.buildPacket(Protocol.TYPE_RESPONSE, payload);

        Protocol.Header header = Protocol.parse(packet, packet.length);
        assertEquals(Protocol.VERSION, header.version);
        assertEquals(Protocol.TYPE_RESPONSE, header.type);
        assertEquals(Protocol.HEADER_BYTES, header.payloadOffset);
        assertEquals(payload.length, header.payloadLength);
        assertArrayEquals(payload,
                Arrays.copyOfRange(packet, header.payloadOffset, header.payloadOffset + header.payloadLength));
    }

    @Test
    public void parse_tooSmall_throws() {
        try {
            Protocol.parse(new byte[]{0x02}, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void isSupportedVersion_onlyCurrent() {
        assertEquals(true, Protocol.isSupportedVersion(Protocol.VERSION));
        assertEquals(false, Protocol.isSupportedVersion(Protocol.VERSION + 1));
        assertEquals(false, Protocol.isSupportedVersion(1));
    }
}

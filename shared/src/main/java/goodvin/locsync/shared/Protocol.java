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

package goodvin.locsync.shared;

public final class Protocol {

    private Protocol() {}

    public static final int VERSION = 3;
    public static final int PORT = 8887;
    public static final int HEADER_BYTES = 2; // [version][type]
    public static final int MAX_PACKET_BYTES = 2048;

    public static final byte TYPE_HELLO = 0x01;
    public static final byte TYPE_RESPONSE = 0x02;
    public static final byte TYPE_VERSION_MISMATCH = 0x03;

    /** Build a packet: [version][type][payload...]. payload may be null. */
    public static byte[] buildPacket(byte type, byte[] payload) {
        int payloadLen = payload == null ? 0 : payload.length;
        byte[] packet = new byte[HEADER_BYTES + payloadLen];
        packet[0] = (byte) VERSION;
        packet[1] = type;
        if (payloadLen > 0) {
            System.arraycopy(payload, 0, packet, HEADER_BYTES, payloadLen);
        }
        return packet;
    }

    /** Parsed header view over a received datagram. */
    public static final class Header {
        public final int version;
        public final byte type;
        public final int payloadOffset;
        public final int payloadLength;

        Header(int version, byte type, int payloadOffset, int payloadLength) {
            this.version = version;
            this.type = type;
            this.payloadOffset = payloadOffset;
            this.payloadLength = payloadLength;
        }
    }

    /**
     * Parse the header from a received buffer holding {@code length} valid bytes.
     *
     * @throws IllegalArgumentException if the buffer is too small to contain a header.
     */
    public static Header parse(byte[] buffer, int length) {
        if (buffer == null || length < HEADER_BYTES) {
            throw new IllegalArgumentException("Packet too small: " + length);
        }
        int version = buffer[0] & 0xFF;
        byte type = buffer[1];
        return new Header(version, type, HEADER_BYTES, length - HEADER_BYTES);
    }

    public static boolean isSupportedVersion(int version) {
        return version == VERSION;
    }
}

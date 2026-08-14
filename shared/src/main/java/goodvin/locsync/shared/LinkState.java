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

/**
 * Link state driving the redesigned power orb and status line. It is derived from — never a second
 * source of truth for — the service/connection status the activities already poll:
 * <ul>
 *   <li>{@code running} — the foreground service is up (client: {@code isServiceRunning()};
 *       server: {@code isServiceRunning()}).</li>
 *   <li>{@code connected} — a live peer within the recency window (client: {@code ConnectionState.CONNECTED};
 *       server: a HELLO-registered client that has not timed out).</li>
 * </ul>
 * The middle {@link #WAITING} state is not user-driven; one press only toggles {@code running}.
 */
public enum LinkState {
    STOPPED,
    WAITING,
    CONNECTED;

    /** The single mapping used by both apps. See the class doc for how each app supplies the flags. */
    public static LinkState of(boolean running, boolean connected) {
        if (!running) {
            return STOPPED;
        }
        return connected ? CONNECTED : WAITING;
    }
}

package jagex2.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketImpl;
import java.util.Base64;

/**
 * THE GAME SOCKET WHEN THIS CLIENT IS RUNNING IN A BROWSER (2026-10-06).
 *
 * CheerpJ runs this jar as it is - the same bytecode the launcher installs - and everything works
 * in there except one thing: a browser tab cannot open a TCP socket. The game stream and the
 * update stream both go through Client.openSocket, so that is the only seam, and this is what it
 * returns when the page sets lostcity.ws. A desktop client never sets it and never comes here.
 *
 * It is a java.net.Socket subclass rather than a new type because OnDemand and ClientStream both
 * take a Socket and call getInputStream/getOutputStream/close on it; anything else would mean
 * changing them for a case neither of them should have to know about. The super constructor is the
 * protected SocketImpl one with null, which is the way to build a Socket that never creates a real
 * socket implementation underneath - with the no-argument constructor the JVM makes one eagerly,
 * and in a browser there is nothing for it to make.
 *
 * ONE WEBSOCKET PER SOCKET, all of them to the server's web port rather than to 43594 and 43595:
 * the server multiplexes them itself (the first byte a client sends decides whether the connection
 * is a login or an update stream - World.onClientData, opcode 15), so the port the caller asked for
 * does not matter here.
 *
 * BYTES CROSS AS BASE64. The four methods below are CheerpJ natives: the page implements them in
 * JavaScript as Java_jagex2_io_WsSocket_&lt;name&gt;. Strings are the one thing that is documented to
 * cross that boundary unchanged, and the game stream is a few kilobytes a second, so the encoding
 * costs nothing worth measuring and the alternative is guessing at how a byte[] arrives on the
 * other side.
 */
public class WsSocket extends Socket {

	/** Opens a WebSocket and returns a handle, or a negative number if it could not connect. */
	public static native int wsOpen(String url);

	/**
	 * Up to max bytes as base64. "" means nothing has arrived yet; null means the socket is gone.
	 * waitMillis is how long the page may wait before answering "" - the Java thread blocks for it,
	 * and because the native is async on the JavaScript side the browser keeps running meanwhile,
	 * which is what makes a blocking read possible here at all.
	 */
	public static native String wsRead(int handle, int max, int waitMillis);

	public static native void wsWrite(int handle, String base64);

	public static native void wsClose(int handle);

	// HOW MUCH TO TAKE AT ONCE. Every one of these calls crosses into JavaScript and back, which is
	// the expensive part - not the bytes - so the bigger the gulp the better. OnDemand consumes the
	// stream 506 bytes at a time; at 8k per call the animation download ran at a crawl.
	private static final int CHUNK = 65536;
	// And a top-up only when what is left would not cover a part, for the same reason: available()
	// is asked on every one of those 506-byte reads.
	private static final int TOP_UP_BELOW = 4096;

	private final int handle;
	private boolean closed;
	private byte[] pending = new byte[0];
	private int pos;

	public WsSocket(String url) throws IOException {
		super((SocketImpl) null);
		this.handle = wsOpen(url);
		if (this.handle < 0) {
			throw new IOException("websocket: cannot reach " + url);
		}
	}

	private int buffered() {
		return this.pending.length - this.pos;
	}

	/** Take whatever the page is holding. waitMillis 0 is a peek. */
	private void fill(int waitMillis) throws IOException {
		if (this.closed) {
			throw new IOException("socket closed");
		}
		String b64 = wsRead(this.handle, CHUNK, waitMillis);
		if (b64 == null) {
			this.closed = true;
			throw new IOException("connection lost");
		}
		if (b64.length() == 0) {
			return;
		}
		byte[] more = Base64.getDecoder().decode(b64);
		int left = this.buffered();
		byte[] grown = new byte[left + more.length];
		System.arraycopy(this.pending, this.pos, grown, 0, left);
		System.arraycopy(more, 0, grown, left, more.length);
		this.pending = grown;
		this.pos = 0;
	}

	public InputStream getInputStream() {
		return new InputStream() {
			public int read() throws IOException {
				while (buffered() == 0) {
					fill(50);
				}
				return pending[pos++] & 0xff;
			}

			public int read(byte[] dst, int off, int len) throws IOException {
				if (len == 0) {
					return 0;
				}
				while (buffered() == 0) {
					fill(50);
				}
				int take = Math.min(len, buffered());
				System.arraycopy(pending, pos, dst, off, take);
				pos += take;
				return take;
			}

			// TOPPED UP EVERY TIME, not only when the buffer has run dry. OnDemand.read() asks for
			// available() and then wants a whole 500-byte part in one go; with a few hundred bytes
			// already here and the rest of the part still sitting in the page, a refill that only
			// happened at zero would answer the same short number forever, the part would never be
			// taken, and after 750 cycles of that the client would close the socket and start the
			// file again - which is what "Connecting to update server" forever looked like.
			public int available() throws IOException {
				if (!closed && buffered() < TOP_UP_BELOW) {
					fill(0);
				}
				return buffered();
			}

			public void close() {
				WsSocket.this.closeQuietly();
			}
		};
	}

	public OutputStream getOutputStream() {
		return new OutputStream() {
			public void write(int b) throws IOException {
				write(new byte[] { (byte) b }, 0, 1);
			}

			public void write(byte[] src, int off, int len) throws IOException {
				if (closed) {
					throw new IOException("socket closed");
				}
				byte[] copy = new byte[len];
				System.arraycopy(src, off, copy, 0, len);
				wsWrite(handle, Base64.getEncoder().encodeToString(copy));
			}

			public void flush() {
				// the page sends every write as it arrives; there is nothing held back to flush
			}

			public void close() {
				WsSocket.this.closeQuietly();
			}
		};
	}

	private void closeQuietly() {
		if (!this.closed) {
			this.closed = true;
			wsClose(this.handle);
		}
	}

	public void close() {
		this.closeQuietly();
	}

	public boolean isClosed() {
		return this.closed;
	}

	// The client sets both on the game socket. A WebSocket has no equivalent of either - it is
	// already message-framed, so there is no Nagle to turn off - and the read timeout is the one in
	// fill() above, so these are accepted and ignored rather than left to throw on a null impl.
	public void setSoTimeout(int timeout) {
	}

	public void setTcpNoDelay(boolean on) {
	}
}

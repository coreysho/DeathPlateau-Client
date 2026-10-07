package jagex2.client.plugin.hub;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON to read the plugin index.
 *
 * WHY NOT A LIBRARY. The client has no dependencies and that is worth keeping: it is a single jar
 * a player downloads and runs on whatever Java they have. Pulling in Gson to read one small file
 * would be the first.
 *
 * WHY NOT REGEX, which is how the launcher reads GitHub's API. That works for pulling two known
 * fields out of a response whose shape never changes. An index is a list of objects with optional
 * fields, written by hand by whoever publishes plugins, and a regex over that quietly matches the
 * wrong brace the first time someone puts a "}" in a description.
 *
 * THIS IS A PARSER FOR UNTRUSTED INPUT. The index is fetched over the network, so every error has
 * to be an exception rather than a wrong answer, and it must not be possible to make it loop
 * forever or recurse until the stack goes. Depth is capped; every loop consumes input or throws.
 *
 * Objects come back as Map, arrays as List, strings as String, numbers as Double, and true/false/
 * null as themselves. Duplicate keys: last wins.
 */
public final class Json {

	/** How deep objects and arrays may nest. Deeper than any index, shallower than the stack. */
	private static final int MAX_DEPTH = 32;

	private final String text;
	private int pos;
	private int depth;

	private Json(String text) {
		this.text = text;
	}

	/** Parses one JSON value. Throws IllegalArgumentException on anything malformed. */
	public static Object parse(String text) {
		if (text == null) {
			throw new IllegalArgumentException("no text");
		}
		Json json = new Json(text);
		json.skipWhitespace();
		Object value = json.readValue();
		json.skipWhitespace();
		if (json.pos != text.length()) {
			throw new IllegalArgumentException("trailing text at " + json.pos);
		}
		return value;
	}

	// ------------------------------------------------------------------ typed reads for callers

	/** A string field, or the fallback when it is absent, null or not a string. */
	public static String string(Map<?, ?> object, String key, String fallback) {
		Object value = object == null ? null : object.get(key);
		return value instanceof String ? (String) value : fallback;
	}

	/** An int field, or the fallback. JSON has one number type, so this rounds. */
	public static int integer(Map<?, ?> object, String key, int fallback) {
		Object value = object == null ? null : object.get(key);
		return value instanceof Double ? (int) Math.round((Double) value) : fallback;
	}

	/** A list field, or an empty list. */
	public static List<?> array(Map<?, ?> object, String key) {
		Object value = object == null ? null : object.get(key);
		return value instanceof List ? (List<?>) value : new ArrayList<Object>();
	}

	// ------------------------------------------------------------------ the parser

	private Object readValue() {
		if (this.pos >= this.text.length()) {
			throw new IllegalArgumentException("ended early");
		}
		char c = this.text.charAt(this.pos);
		if (c == '{') {
			return this.readObject();
		}
		if (c == '[') {
			return this.readArray();
		}
		if (c == '"') {
			return this.readString();
		}
		if (this.text.startsWith("true", this.pos)) {
			this.pos += 4;
			return Boolean.TRUE;
		}
		if (this.text.startsWith("false", this.pos)) {
			this.pos += 5;
			return Boolean.FALSE;
		}
		if (this.text.startsWith("null", this.pos)) {
			this.pos += 4;
			return null;
		}
		return this.readNumber();
	}

	private Map<String, Object> readObject() {
		this.enter();
		Map<String, Object> object = new LinkedHashMap<String, Object>();
		this.pos++;                                              // {
		this.skipWhitespace();
		if (this.peek() == '}') {
			this.pos++;
			this.depth--;
			return object;
		}
		while (true) {
			this.skipWhitespace();
			if (this.peek() != '"') {
				throw new IllegalArgumentException("expected a key at " + this.pos);
			}
			String key = this.readString();
			this.skipWhitespace();
			this.expect(':');
			this.skipWhitespace();
			object.put(key, this.readValue());
			this.skipWhitespace();
			char c = this.peek();
			this.pos++;
			if (c == '}') {
				this.depth--;
				return object;
			}
			if (c != ',') {
				throw new IllegalArgumentException("expected , or } at " + (this.pos - 1));
			}
		}
	}

	private List<Object> readArray() {
		this.enter();
		List<Object> list = new ArrayList<Object>();
		this.pos++;                                              // [
		this.skipWhitespace();
		if (this.peek() == ']') {
			this.pos++;
			this.depth--;
			return list;
		}
		while (true) {
			this.skipWhitespace();
			list.add(this.readValue());
			this.skipWhitespace();
			char c = this.peek();
			this.pos++;
			if (c == ']') {
				this.depth--;
				return list;
			}
			if (c != ',') {
				throw new IllegalArgumentException("expected , or ] at " + (this.pos - 1));
			}
		}
	}

	private String readString() {
		this.pos++;                                              // opening quote
		StringBuilder out = new StringBuilder();
		while (true) {
			if (this.pos >= this.text.length()) {
				throw new IllegalArgumentException("unterminated string");
			}
			char c = this.text.charAt(this.pos++);
			if (c == '"') {
				return out.toString();
			}
			if (c != '\\') {
				out.append(c);
				continue;
			}
			if (this.pos >= this.text.length()) {
				throw new IllegalArgumentException("unterminated escape");
			}
			char escape = this.text.charAt(this.pos++);
			if (escape == 'n') {
				out.append('\n');
			} else if (escape == 't') {
				out.append('\t');
			} else if (escape == 'r') {
				out.append('\r');
			} else if (escape == 'b') {
				out.append('\b');
			} else if (escape == 'f') {
				out.append('\f');
			} else if (escape == 'u') {
				if (this.pos + 4 > this.text.length()) {
					throw new IllegalArgumentException("short \\u escape");
				}
				out.append((char) Integer.parseInt(this.text.substring(this.pos, this.pos + 4), 16));
				this.pos += 4;
			} else if (escape == '"' || escape == '\\' || escape == '/') {
				out.append(escape);
			} else {
				throw new IllegalArgumentException("bad escape \\" + escape);
			}
		}
	}

	private Double readNumber() {
		int start = this.pos;
		while (this.pos < this.text.length() && "+-.eE0123456789".indexOf(this.text.charAt(this.pos)) >= 0) {
			this.pos++;
		}
		if (start == this.pos) {
			throw new IllegalArgumentException("expected a value at " + start);
		}
		try {
			return Double.valueOf(this.text.substring(start, this.pos));
		} catch (NumberFormatException error) {
			throw new IllegalArgumentException("bad number at " + start);
		}
	}

	private void enter() {
		if (++this.depth > MAX_DEPTH) {
			throw new IllegalArgumentException("nested too deep");
		}
	}

	private char peek() {
		if (this.pos >= this.text.length()) {
			throw new IllegalArgumentException("ended early");
		}
		return this.text.charAt(this.pos);
	}

	private void expect(char c) {
		if (this.peek() != c) {
			throw new IllegalArgumentException("expected " + c + " at " + this.pos);
		}
		this.pos++;
	}

	private void skipWhitespace() {
		while (this.pos < this.text.length()) {
			char c = this.text.charAt(this.pos);
			if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
				return;
			}
			this.pos++;
		}
	}
}

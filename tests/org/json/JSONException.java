package org.json;

/** Minimal host-test equivalent of Android's checked JSONException. */
public class JSONException extends Exception {
    public JSONException(String message) {
        super(message);
    }

    public JSONException(String message, Throwable cause) {
        super(message, cause);
    }
}

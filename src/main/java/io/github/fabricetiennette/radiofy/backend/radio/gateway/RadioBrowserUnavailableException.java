package io.github.fabricetiennette.radiofy.backend.radio.gateway;

/// Radio Browser could not be reached or answered with something unusable.
///
/// Distinct from an unexpected bug on our side: the request was well formed and the
/// code did its job, the upstream directory is simply down. The client deserves to
/// be told that rather than receiving a blank 500.
public class RadioBrowserUnavailableException extends RuntimeException {

    public RadioBrowserUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RadioBrowserUnavailableException(String message) {
        super(message);
    }
}

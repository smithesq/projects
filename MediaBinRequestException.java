package com.eu.interflow.livesite.mediabin;

@SuppressWarnings("serial")
public class MediaBinRequestException extends Exception {

	public MediaBinRequestException() {
	}

	public MediaBinRequestException(String message) {
		super(message);
	}

	public MediaBinRequestException(Throwable cause) {
		super(cause);
	}

	public MediaBinRequestException(String message, Throwable cause) {
		super(message, cause);
	}

}

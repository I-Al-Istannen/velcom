package de.aaaaaaah.velcom.runner.entity.execution.output;

/**
 * An exception detailing an error parsing the script output.
 */
public class OutputParseException extends RuntimeException {

	public OutputParseException(String message) {
		super(message);
	}

	public OutputParseException(String message, Throwable cause) {
		super(message, cause);
	}
}

package de.aaaaaaah.velcom.runner.entity;

import de.aaaaaaah.velcom.runner.shared.protocol.runnerbound.entities.RunnerWorkOrder;
import java.nio.file.Path;

/**
 * Starts execution of whatever work the server sent.
 */
public interface WorkExecutor {

	/**
	 * Aborts execution and discards all (potential) results.
	 *
	 * @return the result
	 */
	AbortionResult abortExecution();

	/**
	 * Starts execution.
	 *
	 * @param workPath the path to the work (probably a tar)
	 * @param workOrder the work order
	 * @param configuration the runner configuration
	 */
	void startExecution(Path workPath, RunnerWorkOrder workOrder,
		RunnerConfiguration configuration);

	/**
	 * The result of aborting.
	 */
	enum AbortionResult {
		/**
		 * Program was successfully cancelled when the method returns and any pending data was
		 * sent.
		 */
		CANCEL_RIGHT_NOW,
		/**
		 * The program will be cancelled in the future.
		 */
		CANCEL_IN_FUTURE
	}
}

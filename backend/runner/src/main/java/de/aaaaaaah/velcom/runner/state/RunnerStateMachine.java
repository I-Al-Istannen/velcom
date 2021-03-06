package de.aaaaaaah.velcom.runner.state;

import de.aaaaaaah.velcom.runner.entity.RunnerConfiguration;
import de.aaaaaaah.velcom.runner.entity.WorkExecutor.AbortionResult;
import de.aaaaaaah.velcom.runner.shared.protocol.StatusCodeMappings;
import de.aaaaaaah.velcom.runner.shared.protocol.runnerbound.entities.RunnerWorkOrder;
import de.aaaaaaah.velcom.runner.shared.protocol.serverbound.entities.BenchmarkResults;
import de.aaaaaaah.velcom.runner.shared.protocol.serverbound.entities.RunnerInformation;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main state machine for the runner.
 */
public class RunnerStateMachine {

	private static final Logger LOGGER = LoggerFactory.getLogger(RunnerStateMachine.class);

	private RunnerState state;
	private BenchmarkResults lastResults;

	/**
	 * Creates a new state machine.
	 */
	public RunnerStateMachine() {
		this.state = new IdleState();
	}

	/**
	 * A connection was established. Called for reconnects or the first connection.
	 *
	 * @param configuration the runner configuration
	 */
	public void onConnectionEstablished(RunnerConfiguration configuration) {
		doWithErrorAndSwitch(
			() -> {
				LOGGER.info("Established connection with status {}", state.getStatus());
				sendRunnerInformation(configuration);
				sendResultsIfAny(configuration);
				return state;
			},
			configuration
		);
	}

	/**
	 * Sends runner information out to the server.
	 *
	 * @param configuration the runner configuration
	 * @throws IOException if an error occurs
	 */
	private void sendRunnerInformation(RunnerConfiguration configuration) throws IOException {
		if (!configuration.getConnectionManager().isConnected()) {
			return;
		}

		configuration.getConnectionManager().sendEntity(new RunnerInformation(
			configuration.getRunnerName(),
			System.getProperty("os.name")
				+ " " + System.getProperty("os.arch")
				+ " " + System.getProperty("os.version"),
			Runtime.getRuntime().availableProcessors(),
			Runtime.getRuntime().maxMemory(),
			state.getStatus(),
			configuration.getBenchmarkRepoOrganizer().getHeadHash().orElse(null)
		));
	}

	/**
	 * Called with the saved path when a file was received.
	 *
	 * @param path the path to the received file. Most likely a temporary directory and deleting it
	 * 	is explicitly allowed.
	 * @param configuration the runner configuration
	 */
	public void onFileReceived(Path path, RunnerConfiguration configuration) {
		doWithErrorAndSwitch(
			() -> state.onFileReceived(path, configuration),
			configuration
		);
	}

	/**
	 * Called when the runner receives some work order from the server.
	 *
	 * @param workOrder the work to complete
	 * @param configuration the runner configuration
	 */
	public void onWorkArrived(RunnerWorkOrder workOrder, RunnerConfiguration configuration) {
		doWithErrorAndSwitch(
			() -> state.onWorkArrived(workOrder, configuration),
			configuration
		);
	}

	/**
	 * Called when the server requests a reset.
	 *
	 * @param reason the reason for the request
	 * @param configuration the runner configuration
	 */
	public void onResetRequested(String reason, RunnerConfiguration configuration) {
		LOGGER.info("Aborting current benchmark ('{}')", reason);
		AbortionResult abortionResult = configuration.getWorkExecutor().abortExecution(reason);

		if (abortionResult == AbortionResult.CANCEL_RIGHT_NOW) {
			if (StatusCodeMappings.discardResults(reason)) {
				lastResults = null;
			}
			LOGGER.info("Abort already done, starting to idle!");
			doWithErrorAndSwitch(IdleState::new, configuration);
		} else {
			LOGGER.info("Waiting for the executor to kill the program before idling!");
		}
	}

	/**
	 * Called when a benchmark was finished.
	 *
	 * @param results the benchmark results. Null if none.
	 * @param configuration the runner configuration
	 */
	public void onWorkDone(BenchmarkResults results, RunnerConfiguration configuration) {
		doWithErrorAndSwitch(
			() -> {
				this.lastResults = results;
				sendResultsIfAny(configuration);
				if (!(state instanceof IdleState)) {
					return new IdleState();
				}
				LOGGER.info("Resuing existing idle state...");
				return state;
			},
			configuration
		);
	}

	/**
	 * Called when the runner receives a message telling it to update its benchmark repo copy.
	 *
	 * @param newCommitHash the hash of the new head commit
	 * @param configuration the runner configuration
	 */
	public void onUpdateBenchmarkRepo(String newCommitHash, RunnerConfiguration configuration) {
		doWithErrorAndSwitch(() -> new UpdateBenchmarkRepoState(newCommitHash), configuration);
	}

	/**
	 * Goes back to the idle state, if it isn't already idling.
	 *
	 * @param configuration the runner configuration
	 */
	public void backToIdle(RunnerConfiguration configuration) {
		if (!(state instanceof IdleState)) {
			LOGGER.info("Skipped back to idle!");
			doWithErrorAndSwitch(IdleState::new, configuration);
		} else {
			LOGGER.info("Would have skipped back to idle, but was already!");
		}
	}


	private void sendResultsIfAny(RunnerConfiguration configuration) throws IOException {
		if (lastResults != null && configuration.getConnectionManager().isConnected()) {
			LOGGER.info("Sending results...");
			configuration.getConnectionManager().sendEntity(lastResults);
			lastResults = null;
		}
	}

	private void doWithErrorAndSwitch(IOErrorCallable action, RunnerConfiguration configuration) {
		try {
			RunnerState newState = action.run();
			// Reference comparison is wanted here! Even if a new state of the same type is returned
			// we want to init it
			if (newState != state) {
				LOGGER.debug("Switching from {} to {}", state, newState);
				this.state = newState;
			}
			if (newState instanceof IdleState) {
				sendRunnerInformation(configuration);
			}
			newState.onSelected(configuration);
		} catch (Exception e) {
			LOGGER.warn("Got an exception while switching stages. Disconnecting myself!", e);
			configuration.getConnectionManager().disconnect();
		}
	}

	private interface IOErrorCallable {

		RunnerState run() throws IOException;
	}
}

package de.aaaaaaah.velcom.runner.shared;

import de.aaaaaaah.velcom.runner.shared.protocol.exceptions.ProgramCancelledException;
import de.aaaaaaah.velcom.runner.shared.util.StringOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A small utility for executing programs.
 */
public class ProgramExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProgramExecutor.class);

	private final long timeToForceKillMillis;

	public ProgramExecutor() {
		this.timeToForceKillMillis = TimeUnit.SECONDS.toMillis(10);
	}

	public ProgramExecutor(long timeToForceKillMillis) {
		this.timeToForceKillMillis = timeToForceKillMillis;
	}

	/**
	 * Executes a program using the passed command. If the future is cancelled <em>with
	 * interruption</em>, the process is forcefully killed.
	 *
	 * @param command the command to execute
	 * @return a future representing the result
	 */
	public FutureProgramResult execute(String... command) {
		AtomicReference<ProgramResult> reference = new AtomicReference<>();
		AtomicReference<RuntimeException> collectedException = new AtomicReference<>();

		Thread thread = new Thread(() -> {
			Instant startTime = Instant.now();

			Process process;
			try {
				process = new ProcessBuilder(command).start();
			} catch (IOException e) {
				collectedException.set(new UncheckedIOException(e));
				return;
			}
			ProcessHandle processHandle = process.toHandle();

			StringOutputStream stdOut = new StringOutputStream();
			StringOutputStream stdErr = new StringOutputStream();

			// Needs to be in a new thread as it is non-interruptable
			Thread readerThread = new Thread(() -> {
				try (InputStream inputStream = process.getInputStream()) {
					inputStream.transferTo(stdOut);
				} catch (IOException e) {
					collectedException.set(new UncheckedIOException(e));
					return;
				}

				try (InputStream errorStream = process.getErrorStream()) {
					errorStream.transferTo(stdErr);
				} catch (IOException e) {
					collectedException.set(new UncheckedIOException(e));
				}
			}, "ProgramExecutor-Reader");
			readerThread.start();

			try {
				process.waitFor();
			} catch (InterruptedException e) {
				LOGGER.info("Killing process (SIGTERM)");
				// We were cancelled
				processHandle.destroy();
				// Allow time for graceful shutdown
				try {
					LOGGER.info(
						"Waiting for {} millis for the thread to die",
						timeToForceKillMillis
					);
					process.waitFor(timeToForceKillMillis, TimeUnit.MILLISECONDS);
				} catch (InterruptedException ignored) {
				}
				if (processHandle.isAlive()) {
					LOGGER.info("Nuking uncooperative process (SIGKILL)");
					processHandle.destroyForcibly();
				} else {
					LOGGER.info("Process was nice and died properly!");
				}
				collectedException.set(new ProgramCancelledException("Killed process!"));
			}
			try {
				if (collectedException.get() == null) {
					LOGGER.debug("Waiting for reader thread to die...");
					readerThread.join();
					LOGGER.debug("Reader thread dead!");
				} else {
					LOGGER.debug("Let reader die by itself, I already died with an exception!");
				}
			} catch (InterruptedException ignore) {
			}
			if (collectedException.get() != null) {
				LOGGER.debug("Got a deferred exception", collectedException.get());
			}
			if (collectedException.get() != null) {
				return;
			}

			reference.set(
				new ProgramResult(
					process.exitValue(),
					stdOut.getString(),
					stdErr.getString(),
					Duration.between(startTime, Instant.now())
				)
			);
		}, "ProgramExecutor-Main");
		thread.start();
		return new FutureProgramResult() {
			private volatile boolean done;
			private String cancelReason;

			@Override
			public void cancel(String reason) {
				thread.interrupt();
				this.cancelReason = reason;
			}

			@Override
			public ProgramResult get() throws InterruptedException, ProgramCancelledException {
				thread.join();
				done = true;
				if (collectedException.get() != null) {
					if (collectedException.get() instanceof ProgramCancelledException) {
						throw new ProgramCancelledException(this.cancelReason);
					}
					throw collectedException.get();
				}
				return reference.get();
			}

			@Override
			public boolean isDone() {
				return done;
			}
		};
	}

	/**
	 * An eventual program result.
	 */
	public interface FutureProgramResult {

		/**
		 * Cancels the result.
		 *
		 * @param reason the reason
		 */
		void cancel(String reason);

		/**
		 * Returns the program result.
		 *
		 * @return the program result
		 * @throws InterruptedException if the thread was interrupted
		 * @throws UncheckedIOException if an IO error occurs
		 * @throws ProgramCancelledException if a cancel request is dutifully enforced
		 */
		@SuppressWarnings("CheckStyle")
		ProgramResult get() throws InterruptedException;

		/**
		 * Returns true if the computation is done.
		 *
		 * @return true if the computation is done
		 */
		boolean isDone();
	}


	/**
	 * The result of executing a program.
	 */
	public static class ProgramResult {

		private final int exitCode;
		private final String stdOut;
		private final String stdErr;
		private final Duration runtime;

		public ProgramResult(int exitCode, String stdOut, String stdErr, Duration runtime) {
			this.exitCode = exitCode;
			this.stdOut = stdOut;
			this.stdErr = stdErr;
			this.runtime = runtime;
		}

		public int getExitCode() {
			return exitCode;
		}

		public String getStdOut() {
			return stdOut;
		}

		public String getStdErr() {
			return stdErr;
		}

		public Duration getRuntime() {
			return runtime;
		}
	}
}

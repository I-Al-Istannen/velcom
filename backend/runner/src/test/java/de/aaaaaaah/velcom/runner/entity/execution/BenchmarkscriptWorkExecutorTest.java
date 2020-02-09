package de.aaaaaaah.velcom.runner.entity.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.aaaaaaah.velcom.runner.entity.BenchmarkRepoOrganizer;
import de.aaaaaaah.velcom.runner.entity.RunnerConfiguration;
import de.aaaaaaah.velcom.runner.shared.protocol.runnerbound.entities.RunnerWorkOrder;
import de.aaaaaaah.velcom.runner.shared.protocol.serverbound.entities.BenchmarkResults;
import de.aaaaaaah.velcom.runner.state.RunnerStateMachine;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;

class BenchmarkscriptWorkExecutorTest {

	@TempDir
	Path tempDir;

	Path workTar;

	private BenchmarkscriptWorkExecutor executor;
	private RunnerStateMachine stateMachine;
	private BenchmarkRepoOrganizer benchmarkRepoOrganizer;
	private RunnerConfiguration runnerConfiguration;
	private Path benchScriptPath;

	@BeforeEach
	void setUp() throws IOException {
		executor = new BenchmarkscriptWorkExecutor();
		benchScriptPath = tempDir.resolve("bench");
		Path workDir = tempDir.resolve("work");
		Files.createDirectory(workDir);
		workTar = workDir.resolve("work.tar");
		try (OutputStream outputStream = Files.newOutputStream(workTar)) {
			var tarStream = new TarArchiveOutputStream(outputStream);
			tarStream.putArchiveEntry(new TarArchiveEntry("Hello"));
			tarStream.closeArchiveEntry();
		}

		this.stateMachine = mock(RunnerStateMachine.class);
		this.benchmarkRepoOrganizer = mock(BenchmarkRepoOrganizer.class);
		this.runnerConfiguration = mock(RunnerConfiguration.class);

		when(benchmarkRepoOrganizer.getBenchmarkScript()).thenReturn(benchScriptPath);
		when(benchmarkRepoOrganizer.getHeadHash()).thenReturn(Optional.empty());

		when(runnerConfiguration.getRunnerName()).thenReturn("Name");
		when(runnerConfiguration.getBenchmarkRepoOrganizer()).thenReturn(benchmarkRepoOrganizer);
		when(runnerConfiguration.getRunnerStateMachine()).thenReturn(stateMachine);
	}

	@Test
	void executesBenchScript() throws IOException {
		writeScript("echo -n {}");
		executor.startExecution(workTar, dummyWorkOrder(), runnerConfiguration);

		verify(benchmarkRepoOrganizer).getBenchmarkScript();
		verify(stateMachine).onWorkDone(any(), Matchers.eq(runnerConfiguration));
	}

	@Test
	void benchscriptReturnsError() throws IOException {
		var value = executeScript("echo -ne '{ \"error\": \"Hello world\" }'");

		assertThat(value.isError()).isTrue();
	}

	@Test
	void benchscriptReturnsHelpfulError() throws IOException {
		var value = executeScript("echo -ne '{ \"error\": \"Hello world\" }'");

		assertThat(value.isError()).isTrue();
		assertThat(value.getError()).contains("{ \"error\": \"Hello world\" }");
		assertThat(value.getError()).contains("Reason");
		assertThat(value.getError()).contains("General");
		assertThat(value.getError()).containsIgnoringCase("Stdout");
		assertThat(value.getError()).containsIgnoringCase("Stderr");
		assertThat(value.getError()).contains(benchScriptPath.toAbsolutePath().toString());
		assertThat(value.getError()).containsIgnoringCase("Runner name");
		assertThat(value.getError()).containsIgnoringCase("machine info");
		assertThat(value.getError()).containsIgnoringCase("java version");
		assertThat(value.getError()).containsIgnoringCase("exit code");
		assertThat(value.getError()).containsIgnoringCase("command");
	}

	@Test
	void benchscriptExitsWithExitCode() throws IOException {
		var value = executeScript("exit 142");

		assertThat(value.isError()).isTrue();
		assertThat(value.getError()).containsIgnoringCase("142");
	}

	@Test
	void internalRunnerError() throws IOException {
		when(benchmarkRepoOrganizer.getBenchmarkScript()).thenAnswer(i -> {
			throw new RuntimeException("AYYY 42");
		});
		var value = executeScript("exit 142");

		assertThat(value.isError()).isTrue();
		assertThat(value.getError()).containsIgnoringCase("internal runner error");
		assertThat(value.getError()).containsIgnoringCase("AYYY 42");
		assertThat(value.getError()).containsIgnoringCase("java.lang.RuntimeException: AYYY 42");
		assertThat(value.getError()).containsIgnoringCase("at de.aaaaaaah");
	}

	@Test
	void abortWorks() throws IOException {
		new Thread(() -> {
			try {
				Thread.sleep(400);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			executor.abortExecution();
		}).start();
		var value = executeScript("sleep 5");

		assertThat(value.isError()).isTrue();
		assertThat(value.getError()).containsIgnoringCase("aborted");
	}

	@Test
	void notExecutableBenchmarkScript() throws IOException {
		writeScript("echo Hey");
		var perms = new HashSet<>(Files.getPosixFilePermissions(benchScriptPath));
		perms.remove(PosixFilePermission.OWNER_EXECUTE);
		Files.setPosixFilePermissions(benchScriptPath, perms);

		executor.startExecution(workTar, dummyWorkOrder(), runnerConfiguration);
		var value = verifyAndReturnResults();

		assertThat(value.isError()).isTrue();
		assertThat(value.getError()).containsIgnoringCase("internal runner error");
		assertThat(value.getError()).containsIgnoringCase("executable");
		assertThat(value.getError()).containsIgnoringCase("at ");
	}

	private BenchmarkResults verifyAndReturnResults() {
		var resultCaptor = ArgumentCaptor.forClass(BenchmarkResults.class);
		verify(stateMachine).onWorkDone(resultCaptor.capture(), eq(runnerConfiguration));

		return resultCaptor.getValue();
	}

	private BenchmarkResults executeScript(String code) throws IOException {
		writeScript(code);
		executor.startExecution(workTar, dummyWorkOrder(), runnerConfiguration);
		return verifyAndReturnResults();
	}

	private void writeScript(String code) throws IOException {
		Files.writeString(benchScriptPath, scriptHeader() + code);
		var perms = new HashSet<>(Files.getPosixFilePermissions(benchScriptPath));
		perms.add(PosixFilePermission.OWNER_EXECUTE);
		Files.setPosixFilePermissions(benchScriptPath, perms);
	}

	private String scriptHeader() {
		return "#!/usr/bin/env bash\n\n";
	}

	private RunnerWorkOrder dummyWorkOrder() {
		return new RunnerWorkOrder(UUID.randomUUID(), "Hash");
	}
}
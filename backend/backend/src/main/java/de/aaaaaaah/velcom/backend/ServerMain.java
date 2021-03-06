package de.aaaaaaah.velcom.backend;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import de.aaaaaaah.velcom.backend.access.BenchmarkWriteAccess;
import de.aaaaaaah.velcom.backend.access.CommitReadAccess;
import de.aaaaaaah.velcom.backend.access.KnownCommitWriteAccess;
import de.aaaaaaah.velcom.backend.access.RepoWriteAccess;
import de.aaaaaaah.velcom.backend.access.TokenWriteAccess;
import de.aaaaaaah.velcom.backend.access.entities.AuthToken;
import de.aaaaaaah.velcom.backend.access.entities.RemoteUrl;
import de.aaaaaaah.velcom.backend.data.commitcomparison.CommitComparer;
import de.aaaaaaah.velcom.backend.data.linearlog.CommitAccessBasedLinearLog;
import de.aaaaaaah.velcom.backend.data.linearlog.LinearLog;
import de.aaaaaaah.velcom.backend.data.queue.PolicyManualFilo;
import de.aaaaaaah.velcom.backend.data.queue.Queue;
import de.aaaaaaah.velcom.backend.data.repocomparison.RepoComparison;
import de.aaaaaaah.velcom.backend.data.repocomparison.TimesliceComparison;
import de.aaaaaaah.velcom.backend.listener.Listener;
import de.aaaaaaah.velcom.backend.restapi.RepoAuthenticator;
import de.aaaaaaah.velcom.backend.restapi.RepoUser;
import de.aaaaaaah.velcom.backend.restapi.endpoints.AllReposEndpoint;
import de.aaaaaaah.velcom.backend.restapi.endpoints.CommitCompareEndpoint;
import de.aaaaaaah.velcom.backend.restapi.endpoints.CommitHistoryEndpoint;
import de.aaaaaaah.velcom.backend.restapi.endpoints.MeasurementsEndpoint;
import de.aaaaaaah.velcom.backend.restapi.endpoints.QueueEndpoint;
import de.aaaaaaah.velcom.backend.restapi.endpoints.RecentlyBenchmarkedCommitsEndpoint;
import de.aaaaaaah.velcom.backend.restapi.endpoints.RepoComparisonGraphEndpoint;
import de.aaaaaaah.velcom.backend.restapi.endpoints.RepoEndpoint;
import de.aaaaaaah.velcom.backend.restapi.endpoints.TestTokenEndpoint;
import de.aaaaaaah.velcom.backend.restapi.exception.CommitAccessExceptionMapper;
import de.aaaaaaah.velcom.backend.restapi.exception.NoSuchCommitExceptionMapper;
import de.aaaaaaah.velcom.backend.restapi.exception.NoSuchRepoExceptionMapper;
import de.aaaaaaah.velcom.backend.runner.Dispatcher;
import de.aaaaaaah.velcom.backend.runner.DispatcherImpl;
import de.aaaaaaah.velcom.backend.storage.db.DatabaseStorage;
import de.aaaaaaah.velcom.backend.storage.repo.RepoStorage;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.MetricsServlet;
import java.nio.file.Paths;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The backend's main class. Contains the core initialisation routines for the web server.
 */
public class ServerMain extends Application<GlobalConfig> {

	private static final Logger LOGGER = LoggerFactory.getLogger(ServerMain.class);

	private static MetricRegistry metricRegistry;

	/**
	 * @return the global metric registry
	 */
	public static MetricRegistry getMetricRegistry() {
		// Called before `run` was called (in a Test`)
		if (metricRegistry == null) {
			LOGGER.warn("Returning bogus metrics factory!");
			return new MetricRegistry();
		}
		return metricRegistry;
	}

	/**
	 * The backend's main class's main method. Starts the web server.
	 *
	 * @param args the command line arguments
	 * @throws Exception if the web server can not be started
	 */
	public static void main(String[] args) throws Exception {
		new ServerMain().run(args);
	}

	@Override
	public void initialize(Bootstrap<GlobalConfig> bootstrap) {
		bootstrap.addCommand(new HashPerformanceTestCommand());
	}

	@Override
	public void run(GlobalConfig configuration, Environment environment) throws Exception {
		metricRegistry = environment.metrics();

		environment.getObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
		configureCors(environment);

		// Exception mappers
		environment.jersey().register(new NoSuchRepoExceptionMapper());
		environment.jersey().register(new NoSuchCommitExceptionMapper());
		environment.jersey().register(new CommitAccessExceptionMapper());

		CollectorRegistry collectorRegistry = new CollectorRegistry();
		collectorRegistry.register(new DropwizardExports(environment.metrics()));
		environment.admin()
			.addServlet("prometheusMetrics", new MetricsServlet(collectorRegistry))
			.addMapping("/prometheusMetrics");

		// Storage layer
		RepoStorage repoStorage = new RepoStorage(configuration.getRepoDir());
		DatabaseStorage databaseStorage = new DatabaseStorage(configuration);

		// Access layer
		BenchmarkWriteAccess benchmarkAccess = new BenchmarkWriteAccess(databaseStorage);
		CommitReadAccess commitAccess = new CommitReadAccess(repoStorage);
		KnownCommitWriteAccess knownCommitAccess = new KnownCommitWriteAccess(databaseStorage);
		RepoWriteAccess repoAccess = new RepoWriteAccess(
			databaseStorage,
			repoStorage,
			new RemoteUrl(configuration.getBenchmarkRepoRemoteUrl()),
			Paths.get(configuration.getArchivesRootDir())
		);
		TokenWriteAccess tokenAccess = new TokenWriteAccess(
			databaseStorage,
			new AuthToken(configuration.getWebAdminToken()),
			configuration.getHashMemory(),
			configuration.getHashIterations()
		);

		// Data layer
		CommitComparer commitComparer = new CommitComparer(configuration.getSignificantFactor());
		LinearLog linearLog = new CommitAccessBasedLinearLog(commitAccess, repoAccess);
		RepoComparison repoComparison = new TimesliceComparison(commitAccess, benchmarkAccess);

		Queue queue = new Queue(knownCommitAccess, new PolicyManualFilo());
		knownCommitAccess.getAllCommitsRequiringBenchmark()
			.stream()
			.map((repoIdHashPair ->
				commitAccess.getCommit(repoIdHashPair.getFirst(), repoIdHashPair.getSecond()))
			)
			.forEach(queue::addCommit);

		// Listener
		Listener listener = new Listener(
			configuration, repoAccess, commitAccess, knownCommitAccess, queue
		);

		// Dispatcher
		Dispatcher dispatcher = new DispatcherImpl(
			queue,
			repoAccess,
			benchmarkAccess,
			configuration.getDisconnectedRunnerGracePeriod()
		);
		RunnerAwareServerFactory.getInstance().setDispatcher(dispatcher);

		// API authentication
		environment.jersey().register(
			new AuthDynamicFeature(
				new BasicCredentialAuthFilter.Builder<RepoUser>()
					.setAuthenticator(new RepoAuthenticator(tokenAccess))
					.buildAuthFilter()
			)
		);
		environment.jersey().register(new AuthValueFactoryProvider.Binder<>(RepoUser.class));

		// API endpoints
		environment.jersey().register(
			new AllReposEndpoint(repoAccess, benchmarkAccess, tokenAccess));
		environment.jersey().register(
			new CommitCompareEndpoint(benchmarkAccess, commitAccess, commitComparer, linearLog));
		environment.jersey().register(
			new CommitHistoryEndpoint(benchmarkAccess, repoAccess, linearLog, commitComparer));
		environment.jersey().register(new MeasurementsEndpoint(benchmarkAccess));
		environment.jersey()
			.register(new QueueEndpoint(commitAccess, queue, dispatcher, linearLog, repoAccess));
		environment.jersey().register(
			new RecentlyBenchmarkedCommitsEndpoint(repoAccess,
				benchmarkAccess, commitAccess, commitComparer, linearLog));
		environment.jersey().register(new RepoComparisonGraphEndpoint(repoComparison));
		environment.jersey().register(new RepoEndpoint(
			repoAccess, tokenAccess, queue, listener, benchmarkAccess));
		environment.jersey().register(new TestTokenEndpoint());
	}

	private void configureCors(Environment environment) {
		var filter = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
		filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
		filter.setInitParameter(
			CrossOriginFilter.ALLOWED_METHODS_PARAM,
			"GET,PUT,POST,DELETE,OPTIONS,PATCH"
		);
		filter.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
		filter.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
		filter.setInitParameter(
			"allowedHeaders",
			"Content-Type,Authorization,X-Requested-With,Content-Length,Accept-Encoding,Origin"
		);
		filter.setInitParameter("allowCredentials", "true");
	}


}

package de.aaaaaaah.velcom.backend.storage.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.aaaaaaah.velcom.backend.GlobalConfig;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteDataSource;

/**
 * Provides access to a database.
 */
public class DatabaseStorage {

	private final HikariDataSource dataSource;

	/**
	 * Initializes the database storage.
	 *
	 * <p>
	 * Also performs database migrations, if necessary.
	 *
	 * @param config the config used to get the connection information for the database from
	 */
	public DatabaseStorage(GlobalConfig config) {
		SQLiteConfig sqliteConfig = new SQLiteConfig();
		sqliteConfig.enforceForeignKeys(true);
		sqliteConfig.setJournalMode(JournalMode.WAL);

		SQLiteDataSource sqliteDataSource = new SQLiteDataSource(sqliteConfig);
		sqliteDataSource.setUrl(config.getJdbcUrl());

		HikariConfig hikariConfig = new HikariConfig();
		hikariConfig.setDataSource(sqliteDataSource);

		config.getJdbcUsername().ifPresent(hikariConfig::setUsername);
		config.getJdbcPassword().ifPresent(hikariConfig::setPassword);

		dataSource = new HikariDataSource(hikariConfig);

		migrate();
	}

	private void migrate() {
		Flyway flyway = Flyway.configure()
			.dataSource(this.dataSource)
			.load();

		flyway.migrate();
	}

	/**
	 * @return a {@link DataSource} instance providing access to the database
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	/**
	 * @return a {@link DSLContext} instance providing jooq functionality along with a connection to
	 * 	the database
	 */
	public DSLContext acquireContext() {
		return new TrackedDSLContext(DSL.using(dataSource, SQLDialect.SQLITE));
	}

	/**
	 * Closes the database storage.
	 */
	public void close() {
		this.dataSource.close();
	}

}

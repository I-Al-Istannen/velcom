package de.aaaaaaah.velcom.backend.data.recentbenchmarks;

import de.aaaaaaah.velcom.backend.data.commitcomparison.CommitComparer;
import de.aaaaaaah.velcom.backend.data.commitcomparison.CommitComparison;
import de.aaaaaaah.velcom.backend.data.linearlog.LinearLog;
import de.aaaaaaah.velcom.backend.data.linearlog.LinearLogException;
import de.aaaaaaah.velcom.backend.newaccess.BenchmarkReadAccess;
import de.aaaaaaah.velcom.backend.newaccess.CommitReadAccess;
import de.aaaaaaah.velcom.backend.newaccess.RepoReadAccess;
import de.aaaaaaah.velcom.backend.newaccess.entities.Commit;
import de.aaaaaaah.velcom.backend.newaccess.entities.CommitHash;
import de.aaaaaaah.velcom.backend.newaccess.entities.RepoId;
import de.aaaaaaah.velcom.backend.newaccess.entities.Run;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RecentBenchmarkCollector {

	private static final int BATCH_SIZE = 100;
	private static final int MAX_ROUNDS = 100;

	private final RepoReadAccess repoAccess;
	private final BenchmarkReadAccess benchmarkAccess;
	private final CommitReadAccess commitaccess;
	private final LinearLog linearLog;
	private final CommitComparer comparer;

	public RecentBenchmarkCollector(RepoReadAccess repoAccess,
		BenchmarkReadAccess benchmarkAccess,
		CommitReadAccess commitaccess, LinearLog linearLog,
		CommitComparer comparer) {

		this.repoAccess = repoAccess;
		this.benchmarkAccess = benchmarkAccess;
		this.commitaccess = commitaccess;
		this.linearLog = linearLog;
		this.comparer = comparer;
	}

	static boolean repeatOn = false;
	static ScheduledExecutorService executor;

	public List<CommitComparison> collect(int amount, boolean onlySignificant) {
		if (!repeatOn) {
			repeatOn = true;
			executor = Executors.newSingleThreadScheduledExecutor();
			executor.scheduleAtFixedRate(
				() -> collect(20, true),
				10000,
				10000,
				TimeUnit.MILLISECONDS
			);
		}

		List<CommitComparison> resultList = new ArrayList<>();
		ParentMapper parentMapper = new ParentMapper(repoAccess, linearLog);

		try {
			for (int i = 0; i < MAX_ROUNDS && resultList.size() < amount; i++) {
				processBatch(resultList, i * BATCH_SIZE, parentMapper, onlySignificant);
			}
		}
		catch (Exception e) {
			throw new BenchmarkCollectorException(e);
		}

		return resultList;
	}

	private void processBatch(List<CommitComparison> resultList, int skip,
		ParentMapper parentMapper, boolean onlySignificant) throws LinearLogException {
		long start = System.currentTimeMillis();

		// 0.) Get runs for this batch
		long aas = System.currentTimeMillis();
		List<Run> runs = benchmarkAccess.getRecentRuns(skip, BATCH_SIZE);
		long aae = System.currentTimeMillis();

		// 1.) Filter out runs that belong to the same commit
		long as = System.currentTimeMillis();

		Map<RepoId, Map<CommitHash, Run>> commitToRunMap = new HashMap<>();

		Iterator<Run> iterator = runs.iterator();
		while (iterator.hasNext()) {
			Run run = iterator.next();

			if (!commitToRunMap.containsKey(run.getRepoId())) {
				commitToRunMap.put(run.getRepoId(), new HashMap<>());
			}

			if (commitToRunMap.get(run.getRepoId()).containsKey(run.getCommitHash())) {
				// There is already an earlier run (more recent based on the lists ordering)
				// for this commit => older run can be discarded
				iterator.remove();
			}
			else {
				commitToRunMap.get(run.getRepoId()).put(run.getCommitHash(), run);
			}
		}

		long ae = System.currentTimeMillis();

		// 2.) Find out which parent runs need to be loaded from database
		long bs = System.currentTimeMillis();

		Map<RepoId, List<CommitHash>> missingParentRuns = new HashMap<>();

		iterator = runs.iterator();

		while (iterator.hasNext()) {
			Run run = iterator.next();

			missingParentRuns.computeIfAbsent(run.getRepoId(), i -> new ArrayList<>());

			CommitHash parentHash = parentMapper.getParent(run.getRepoId(), run.getCommitHash())
				.orElse(null);

			if (parentHash == null && onlySignificant) {
				// parent commit does not exist for this run/commit => no comparison can be made
				// => if onlySignificant is true this run can never be significant => remove run
				iterator.remove();
				commitToRunMap.get(run.getRepoId()).remove(run.getCommitHash());
			}
			else if (parentHash != null) {
				// Only insert into missingParentRuns if parent commit actually exists
				// (since if it does not exist, no run for that commit can exist => not missing)

				// Check if run of parent commit is already in memory
				if (!commitToRunMap.get(run.getRepoId()).containsKey(parentHash)) {
					// sadly, parent run either does not exist or is not in memory => need to load
					missingParentRuns.get(run.getRepoId()).add(parentHash);
				}
			}
		}

		long be = System.currentTimeMillis();

		// 3.) Load missing parent runs
		long cs = System.currentTimeMillis();

		if (!missingParentRuns.isEmpty()) {
			// Maybe a universal identifier for (repoid+commithash pair) would be nice
			// so we don't have to do this for each repository...
			missingParentRuns.forEach((repoId, runHashes) -> {
				Map<CommitHash, Run> latestRuns = benchmarkAccess.getLatestRuns(repoId, runHashes);

				for (Run run : latestRuns.values()) {
					commitToRunMap.get(run.getRepoId()).put(run.getCommitHash(), run);
				}
			});
		}

		long ce = System.currentTimeMillis();

		// 4.) Load commit data
		long ds = System.currentTimeMillis();

		Map<RepoId, Map<CommitHash, Commit>> commitDataMap = new HashMap<>();

		commitToRunMap.forEach((repoId, hashToRunMap) -> {
			Set<CommitHash> commitHashes = hashToRunMap.keySet();

			List<Commit> commits = commitaccess.getCommits(repoId, commitHashes);

			for (Commit commit : commits) {
				commitDataMap.computeIfAbsent(repoId, i -> new HashMap<>());
				commitDataMap.get(repoId).put(commit.getHash(), commit);
			}
		});

		long de = System.currentTimeMillis();

		// 5.) Do the actual comparisons
		long es = System.currentTimeMillis();

		iterator = runs.iterator();
		while (iterator.hasNext()) {
			Run run = iterator.next();

			CommitHash commitHash = run.getCommitHash();
			CommitHash parentHash = parentMapper.getParent(run.getRepoId(), commitHash)
				.orElse(null);
			Commit commit = commitDataMap.get(run.getRepoId()).get(commitHash);

			if (commit == null) {
				// No commit data available => no comparison possible
				iterator.remove();
				commitToRunMap.get(run.getRepoId()).remove(commitHash);
			}
			else {
				CommitComparison comparison;

				if (parentHash == null) {
					// Parent not available, but since this run still exists, it is okay
					comparison = comparer.compare(null, null, commit, run);
				}
				else {
					// Parent is available => load it and its run
					Commit parent = commitDataMap.get(run.getRepoId()).get(parentHash);
					Run parentRun = commitToRunMap.get(run.getRepoId()).get(parentHash);

					if (parent == null) {
						// parent commit data not available => no comparison can be made with it
						comparison = comparer.compare(null, null, commit, run);
					}
					else if (parentRun == null) {
						// parent run not available
						comparison = comparer.compare(parent, null, commit, run);
					}
					else {
						// parent run & parent commit data available
						comparison = comparer.compare(parent, parentRun, commit, run);
					}
				}

				if (!onlySignificant || comparison.isSignificant()) {
					resultList.add(comparison);
				}
			}
		}

		long ee = System.currentTimeMillis();

		long end = System.currentTimeMillis();
		System.err.println(
			(end - start) + "|"
			+ (aae - aas) + "|"
			+ (ae - as) + "|"
			+ (be - bs) + "|"
			+ (ce - cs) + "|"
			+ (de - ds) + "|"
			+ (ee - es)
		);
	}

}
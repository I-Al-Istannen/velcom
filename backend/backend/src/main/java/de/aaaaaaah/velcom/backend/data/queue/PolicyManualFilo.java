package de.aaaaaaah.velcom.backend.data.queue;

import de.aaaaaaah.velcom.backend.access.entities.Commit;
import de.aaaaaaah.velcom.backend.access.entities.CommitHash;
import de.aaaaaaah.velcom.backend.access.entities.RepoId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;

/**
 * A policy where manual tasks are ordered with a FiLo principle.
 *
 * <p> If there are manual tasks, those are executed before any other tasks. The last manual task
 * to be added is executed first. The other tasks are grouped by repo and ordered from recent to
 * old. Round-robin is performed on the repos: The most recent task of each repo is executed before
 * moving on to the next repo.
 */
public class PolicyManualFilo implements QueuePolicy {

	private final Deque<Commit> manualTasks;
	private final Map<RepoId, Deque<Commit>> tasks;
	private final Queue<RepoId> repoIdQueue;

	public PolicyManualFilo() {
		this(new ArrayDeque<>(), new HashMap<>(), new ArrayDeque<>());
	}

	private PolicyManualFilo(Deque<Commit> manualTasks, Map<RepoId, Deque<Commit>> tasks,
		Queue<RepoId> repoIdQueue) {

		this.manualTasks = manualTasks;
		this.tasks = tasks;
		this.repoIdQueue = repoIdQueue;
	}

	private Optional<Deque<Commit>> getRepoStack(RepoId repoId) {
		return Optional.ofNullable(tasks.get(repoId));
	}

	private Optional<Commit> findTaskInStack(Deque<Commit> stack, RepoId repoId, CommitHash hash) {
		for (Commit commit : stack) {
			if (commit.getRepoId().equals(repoId) && commit.getHash().equals(hash)) {
				return Optional.of(commit);
			}
		}
		return Optional.empty();
	}

	private Optional<Commit> findTask(RepoId repoId, CommitHash hash) {
		return getRepoStack(repoId).flatMap(stack -> findTaskInStack(stack, repoId, hash));
	}

	private Optional<Commit> findManualTask(RepoId repoId, CommitHash hash) {
		return findTaskInStack(manualTasks, repoId, hash);
	}

	private void removeTaskFromStack(Deque<Commit> stack, RepoId repoId, CommitHash hash) {
		stack.removeIf(
			commit -> commit.getRepoId().equals(repoId) && commit.getHash().equals(hash));
	}

	private Collection<Commit> removeTaskFromStack(Deque<Commit> stack, RepoId repoId) {
		final List<Commit> removedTasks = stack.stream()
			.filter(commit -> commit.getRepoId().equals(repoId))
			.collect(Collectors.toUnmodifiableList());
		stack.removeIf(commit -> commit.getRepoId().equals(repoId));
		return removedTasks;
	}

	private void removeTask(RepoId repoId, CommitHash hash) {
		getRepoStack(repoId).ifPresent(stack -> removeTaskFromStack(stack, repoId, hash));
	}

	private Collection<Commit> removeTask(RepoId repoId) {
		final Optional<Deque<Commit>> stack = getRepoStack(repoId);
		if (stack.isEmpty()) {
			return List.of();
		}

		return removeTaskFromStack(stack.get(), repoId);
	}

	private void removeManualTask(RepoId repoId, CommitHash hash) {
		removeTaskFromStack(manualTasks, repoId, hash);
	}

	private Collection<Commit> removeManualTask(RepoId repoId) {
		return removeTaskFromStack(manualTasks, repoId);
	}

	/**
	 * Adds task to stack of its repo and if necessary creates new stack for that repo.
	 *
	 * @param commit the commit that has been added as task
	 */
	@Override
	public boolean addTask(Commit commit) {
		if (findManualTask(commit.getRepoId(), commit.getHash()).isPresent()
			|| findTask(commit.getRepoId(), commit.getHash()).isPresent()) {
			return false;
		}

		Deque<Commit> stack = tasks.get(commit.getRepoId());
		if (stack == null) {
			stack = new ArrayDeque<>();
			tasks.put(commit.getRepoId(), stack);
			repoIdQueue.add(commit.getRepoId());
		}
		stack.addFirst(commit);
		return true;
	}

	/**
	 * Adds task to manual stack.
	 *
	 * @param commit the commit that has been added as task
	 */
	@Override
	public boolean addManualTask(Commit commit) {
		abortTask(commit.getRepoId(), commit.getHash());
		manualTasks.addFirst(commit);
		return true;
	}

	/**
	 * Returns next commit.
	 * <ol>
	 *     <li>Top of manual stack</li>
	 *     <li>Top of next repo stack</li>
	 *     <li>Empty</li>
	 * </ol>
	 */
	@Override
	public Optional<Commit> getNextTask() {
		//Return manual task
		if (!manualTasks.isEmpty()) {
			return Optional.of(manualTasks.removeFirst());
		}

		final RepoId startRepoId = repoIdQueue.peek();
		if (startRepoId == null) {
			// The queue is empty.
			return Optional.empty();
		}

		do {
			repoIdQueue.add(repoIdQueue.remove());

			final Optional<Deque<Commit>> stack = getRepoStack(repoIdQueue.peek());
			if (stack.isPresent() && !stack.get().isEmpty()) {
				return Optional.of(stack.get().removeFirst());
			}
		} while (!startRepoId.equals(repoIdQueue.peek()));

		//If stacks are empty, return empty
		return Optional.empty();
	}

	/**
	 * @return sorted list of all current tasks
	 */
	@Override
	public List<Commit> viewAllCurrentTasks() {
		PolicyManualFilo pmf = copy();
		List<Commit> currentTasks = new ArrayList<>();
		while (true) {
			Optional<Commit> next = pmf.getNextTask();
			if (next.isEmpty()) {
				break;
			}
			currentTasks.add(next.get());
		}
		return currentTasks;
	}

	/**
	 * Removes commit from queue.
	 *
	 * @param repoId the repo the commit is in
	 * @param hash the commit hash of the commit to be removed
	 */
	@Override
	public void abortTask(RepoId repoId, CommitHash hash) {
		removeTask(repoId, hash);
		removeManualTask(repoId, hash);
	}

	private PolicyManualFilo copy() {
		Deque<Commit> newManualTasks = new ArrayDeque<>(manualTasks);

		Map<RepoId, Deque<Commit>> newTasks = new HashMap<>();
		tasks.forEach((repoId, stack) -> newTasks.put(repoId, new ArrayDeque<>(stack)));

		Queue<RepoId> newRepoIdQueue = new ArrayDeque<>(repoIdQueue);

		return new PolicyManualFilo(newManualTasks, newTasks, newRepoIdQueue);
	}

	@Override
	public Collection<Commit> abortAllTasksOfRepo(RepoId repoId) {
		List<Commit> abortedTasks = new ArrayList<>();
		abortedTasks.addAll(removeTask(repoId));
		abortedTasks.addAll(removeManualTask(repoId));
		return abortedTasks;
	}
}

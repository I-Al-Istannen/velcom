package de.aaaaaaah.velcom.backend.access.filter;

import java.time.Instant;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

class Before extends AuthorTimeRevFilter {

	private final Instant time;

	public Before(Instant time) {
		this.time = time;
	}

	@Override
	public boolean include(RevWalk walker, RevCommit cmit)
		throws StopWalkException {

		return cmit.getAuthorIdent().getWhen().toInstant().isBefore(time);
	}
}

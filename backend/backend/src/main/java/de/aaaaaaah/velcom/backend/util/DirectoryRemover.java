package de.aaaaaaah.velcom.backend.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Provides the ability to completely remove directories with all its content.
 */
public final class DirectoryRemover {

	private DirectoryRemover() {
		throw new UnsupportedOperationException("No instantiation");
	}


	/**
	 * Deletes all files (direct and indirect) inside the given directory and the directory itself.
	 *
	 * @param directory the directory to delete
	 * @throws IOException if an exception occurs trying to delete the directory
	 */
	public static void deleteDirectoryRecursive(Path directory) throws IOException {
		Files.walkFileTree(directory, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
				throws IOException {

				file.toFile().setWritable(true);

				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
				throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}

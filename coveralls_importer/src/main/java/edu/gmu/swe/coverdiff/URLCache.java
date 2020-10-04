package edu.gmu.swe.coverdiff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class URLCache {
	private final Path dir;
	private final Set<String> files = new HashSet<>();

	public URLCache(Path dir) {
		this.dir = dir;
		try {
			Files.list(dir).forEach(f -> files.add(f.getFileName().toString()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String toCacheFileName(String url) {
		return url.replace('/', '-').replace('.', '-');
	}

	public synchronized String get(String url) throws IOException {
		if (files.contains(toCacheFileName(url))) {
			return new String(Files.readAllBytes(dir.resolve(toCacheFileName(url))));
		}
		return null;
	}

	public synchronized void save(String url, String d) throws IOException {
		files.add(toCacheFileName(url));
		Files.write(dir.resolve(toCacheFileName(url)), d.getBytes());
	}
}
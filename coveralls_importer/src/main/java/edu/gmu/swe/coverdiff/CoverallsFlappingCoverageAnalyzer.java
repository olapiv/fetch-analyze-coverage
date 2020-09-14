package edu.gmu.swe.coverdiff;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

public class CoverallsFlappingCoverageAnalyzer implements Callable<Void> {

	@CommandLine.Option(names = {"-debug"}, description = "Debug mode (prints out file-level coverage too)")
	boolean debug = false;
	@CommandLine.Option(names = {"-proj"}, description = "Single project slug to use")
	private String projectSlug; //"square-retrofit";
	@CommandLine.Option(names = {"-cache"}, description = "Path to serialized cache input directory")
	private String cacheDir = "serialized-cache";
	@CommandLine.Option(names = {"-repos"})
	private String reposDir = "repos";
	@CommandLine.Option(names = {"-output"})
	private String outputFile = "flapping_coveralls.csv";
	private PrintWriter outputWriter;

	@CommandLine.Option(names = {"-shaOrders"})
	private String shaOrdersFile = "shaOrders.csv";

	public static void main(String[] args) {
		CommandLine.call(new CoverallsFlappingCoverageAnalyzer(), System.err, args);
	}

	static JaCoCoBuild getNthParent(int n, JaCoCoBuild from, HashMap<Integer, JaCoCoBuild> map) {
		JaCoCoBuild parent = map.get(from.parentInternalID);
		n--;
		while (parent != null && n > 0) {
			parent = map.get(parent.parentInternalID);
		}
		return parent;
	}

	@Override
	public Void call() throws Exception {


		//Read in the testExecSummary to build the buildlist
		outputWriter = new PrintWriter(outputFile);
		outputWriter.print("commit,file,line,covered\n");
		PrintWriter debugWriter = null;
		if (debug) {
			debugWriter = new PrintWriter("flapping_jacoco_debug.csv");
			debugWriter.println(CoverallsImporter.SrcTestGeneralDiffResult.toCSVDebugHeader());
		}


		int lineNumber = 0;
		HashMap<String, HashMap<String, CoverallsImporter.Build>> buildMap = new HashMap<>();
		HashMap<String, String> mostRecentBuilds = new HashMap<>();
		HashMap<String, HashSet<String>> validBuilds = new HashMap<>();

		try (Scanner s = new Scanner(new File(shaOrdersFile))) {
			while (s.hasNextLine()) {
				String l = s.nextLine();
				String d[] = l.split(",");
				if (d[1].equals("0")) {
					mostRecentBuilds.put(d[0].replace('/','-'),d[2]);
				}
				if(!validBuilds.containsKey(d[0]))
					validBuilds.put(d[0],new HashSet<>());
				validBuilds.get(d[0]).add(d[2]);
			}
		}
		for (File f : new File(cacheDir).listFiles()) {
//			if(!f.toString().contains("bitcoin-s"))
//				continue;
			try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
				HashMap<String, CoverallsImporter.Build> proj = new HashMap<>();
				CoverallsImporter.BuildList bl = (CoverallsImporter.BuildList) ois.readObject();
				String name = bl.builds.get(0).repo_name.replace('/','-');
				buildMap.put(name, proj);
				for (CoverallsImporter.Build b : bl.builds) {
					if(validBuilds.get(b.repo_name).contains(b.commit_sha))
						proj.put(b.commit_sha, b);
				}

			}
		}

		for (String repoName : buildMap.keySet()) {
			HashMap<String, CoverallsImporter.Build> builds = buildMap.get(repoName);
			CoverallsImporter.Build latest = builds.get(mostRecentBuilds.get(repoName));
			Path localRepo = Paths.get(reposDir, repoName, ".git");
			System.out.println("Working on " + repoName);
			if (!Files.exists(localRepo)) {
				String forURL[] = repoName.split("-", 2);
				String projectURL = "https://github.com/" + forURL[0] + "/" + forURL[1];
//				projectURL = "https://github.com/bitcoin-s/bitcoin-s-core";
				System.out.println("Cloning " + projectURL);
				Git.cloneRepository().setURI(projectURL).setBare(true).setDirectory(localRepo.toFile()).call();

			}
			FileRepositoryBuilder frb = new FileRepositoryBuilder();
			frb.setGitDir(localRepo.toFile());
			frb.setBare();
			Repository repo = frb.build();
			HashMap<String, Integer> flappingLines = new HashMap<>();
			for (CoverallsImporter.Build cur : builds.values()) {
				//Have to find the parent builds, annoying.

				cur.parent_sha = CoverallsImporter.getLeftParentSHA(repo,cur.commit_sha);
				CoverallsImporter.Build prev = builds.get(cur.parent_sha);
				if (prev != null)
					for (String k : cur.flappingLines(prev, repo, latest)) {
						outputWriter.print(cur.commit_sha);
						outputWriter.print(',');
						outputWriter.println(k);
					}
			}
			outputWriter.flush();
		}

		if (debug)
			debugWriter.close();

		outputWriter.close();
		return null;
	}

	String findFileNameFromJavaName(LinkedList<String> fileNames, String javaName) {
		javaName = javaName + ".java";
		for (String s : fileNames) {
			if (s.endsWith(javaName))
				return s;
		}
//		System.out.println("Unable to find backing file for " + javaName);
		return null;
	}

	static class JaCoCoSourceFileInfo {
		List<Integer> hitLinesThisClass;
		List<Integer> allStatmentLinesThisClass;
	}

	static class JaCoCoSourceFileList {
		Map<String, JaCoCoSourceFileInfo> files;
	}

	class JaCoCoBuild extends CoverallsImporter.Build {
		int internalID;
		int parentInternalID;
		int order;

		public void load(Repository repo) throws IOException {
			JaCoCoBuild b = this;
//			String json = new String(Files.readAllBytes(Paths.get(jsonDir, b.internalID + ".json")));
////			Map<String, JaCoCoSourceFileInfo> data = sourceFileJsonAdapter.fromJson(json);
//			b.sourceFileList = new CoverallsImporter.SourceFileList();
//			b.sourceFileList.total = data.size();


			LinkedList<String> javaFiles = new LinkedList<>();
			try (RevWalk walk = new RevWalk(repo)) {
				RevCommit commit = walk.parseCommit(repo.resolve(b.commit_sha));

				TreeWalk treeWalk = new TreeWalk(repo);
				treeWalk.addTree(commit.getTree());
				treeWalk.setRecursive(true);
				while (treeWalk.next()) {
					if (treeWalk.getPathString().endsWith(".java"))
						javaFiles.add(treeWalk.getPathString());
				}
				treeWalk.close();
			}

			b.sourceFileList.parsedFiles = new LinkedList<>();
//			for (Map.Entry<String, JaCoCoSourceFileInfo> e : data.entrySet()) {
//				if (e.getKey().contains("$"))
//					continue; //Defer for simplicity
//				CoverallsImporter.SourceFile f = new CoverallsImporter.SourceFile();
//				f.name = findFileNameFromJavaName(javaFiles, e.getKey());
//				if (f.name == null)
//					continue;
//				b.sourceFileList.parsedFiles.add(f);
//				int max = 0;
//				for (int i : e.getValue().allStatmentLinesThisClass)
//					if (i > max)
//						max = i;
//				f.coverage = new ArrayList<Integer>(max + 1);
//				for (int i = 0; i <= 1 + max; i++)
//					f.coverage.add(null);
//				for (Integer i : e.getValue().allStatmentLinesThisClass) {
//					f.coverage.set(i, 0);
//					f.relevant_line_count++;
//				}
//				for (Integer i : e.getValue().hitLinesThisClass) {
//					f.coverage.set(i, 1);
//					f.covered_line_count++;
//				}
//				f.covered_percent = (double) f.covered_line_count / ((double) f.relevant_line_count);
////			}
//			for (Map.Entry<String, JaCoCoSourceFileInfo> e : data.entrySet()) {
//				if (!e.getKey().contains("$"))
//					continue;
//				String name = findFileNameFromJavaName(javaFiles, e.getKey().substring(0, e.getKey().indexOf('$')));
//				if (name == null)
//					continue;
//				boolean found = false;
//				for (CoverallsImporter.SourceFile f : b.sourceFileList.parsedFiles)
//					if (f.name.equals(name)) {
//						found = true;
//						int max = 0;
//						for (int i : e.getValue().allStatmentLinesThisClass)
//							if (i > max)
//								max = i;
//						for (int i = f.coverage.size(); i <= 1 + max; i++)
//							f.coverage.add(null);
//						for (Integer i : e.getValue().allStatmentLinesThisClass) {
//							if (f.coverage.get(i) == null) {
//								f.relevant_line_count++;
//								f.coverage.set(i, 0);
//							}
//						}
//						for (Integer i : e.getValue().hitLinesThisClass) {
//							if (f.coverage.get(i) == null || f.coverage.get(i) == 0) {
//								f.covered_line_count++;
//								f.coverage.set(i, 1);
//							}
//						}
//						break;
//					}
//				if (!found)
//					System.out.println("PROBLEM2");
//
//			}
		}

	}
}

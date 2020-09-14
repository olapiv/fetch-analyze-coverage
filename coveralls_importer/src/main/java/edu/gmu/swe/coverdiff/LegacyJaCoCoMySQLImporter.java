package edu.gmu.swe.coverdiff;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import picocli.CommandLine;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

public class LegacyJaCoCoMySQLImporter implements Callable<Void> {


	private final Moshi moshi = new Moshi.Builder().build();
	private final JsonAdapter<Map<String, JaCoCoSourceFileInfo>> sourceFileJsonAdapter = moshi.adapter(Types.newParameterizedType(Map.class, String.class, JaCoCoSourceFileInfo.class));
	@CommandLine.Option(names = {"-jacoco"}, description = "Enable jacoco mode (must be specified first, unlocks other options)")
	private boolean jacoco = false;
	@CommandLine.Option(names = {"-proj"}, description = "Single project slug to use")
	private String projectSlug; //"square-retrofit";
	@CommandLine.Option(names = {"-summaryFile"}, description = "Path to test_exec_summary.csv")
	private String testExecSummaryFile = "test_exec_summary.csv";
	@CommandLine.Option(names = {"-json"}, description = "Path to JSON input directory")
	private String jsonDir = "json";
	@CommandLine.Option(names = {"-repos"})
	private String reposDir = "repos";

	@CommandLine.Option(names = { "-debug" }, description = "Debug mode (prints out file-level coverage too)")
	boolean debug = false;

	@CommandLine.Option(names = {"-output"})
	private String outputFile = "coverage_jacoco.csv";

	private PrintWriter outputWriter;

	@Override
	public Void call() throws Exception {

		//Read in the testExecSummary to build the buildlist
		outputWriter = new PrintWriter(outputFile);
		outputWriter.print("date," + CoverallsImporter.SrcTestGeneralDiffResult.toCSVHeader());
		PrintWriter debugWriter = null;
		if (debug) {
			debugWriter = new PrintWriter("coverage_jacoco_debug.csv");
			debugWriter.println(CoverallsImporter.SrcTestGeneralDiffResult.toCSVDebugHeader());
		}


		HashMap<String,HashMap<Integer, JaCoCoBuild>> buildsMap = new HashMap<>();
		int lineNumber = 0;
		try (Scanner s = new Scanner(new File(testExecSummaryFile))) {
			s.nextLine();//skip header
			while (s.hasNextLine()) {
				lineNumber++;
				String l = s.nextLine().replace("\"", "");
				String[] d = l.split(",");
				if (projectSlug != null && !d[3].equals(projectSlug))
					continue;

				if (d[12].equals("NULL"))
					continue;
				JaCoCoBuild b = new JaCoCoBuild();
				b.internalID = Integer.valueOf(d[0]);
				b.commit_sha = d[2];
				b.repo_name = d[3];
				b.date = d[4];
				b.branch = "master";
				b.coverage_percent = (double) Integer.valueOf(d[10]) / ((double) Integer.valueOf(d[11]));
				b.parentInternalID = Integer.valueOf(d[12]);
				if(!buildsMap.containsKey(b.repo_name))
					buildsMap.put(b.repo_name,new HashMap<>());
				buildsMap.get(b.repo_name).put(b.internalID, b);
			}
		}catch(Throwable t){
			System.err.println("Error at line " + lineNumber);
			throw t;
		}
		for(String repoName : buildsMap.keySet()) {
			Path localRepo = Paths.get(reposDir, repoName, ".git");
			System.out.println("Working on " + repoName);
			if (!Files.exists(localRepo)) {
				String forURL[] = repoName.split("-",2);
				String projectURL = "https://github.com/" + forURL[0]+"/"+forURL[1];
				System.out.println("Cloning " + projectURL);
				Git.cloneRepository().setURI(projectURL).setBare(true).setDirectory(localRepo.toFile()).call();

			}
			FileRepositoryBuilder frb = new FileRepositoryBuilder();
			frb.setGitDir(localRepo.toFile());
			frb.setBare();
			Repository repo = frb.build();
			for (JaCoCoBuild b : buildsMap.get(repoName).values()) {
				String json = new String(Files.readAllBytes(Paths.get(jsonDir, b.internalID + ".json")));
				Map<String, JaCoCoSourceFileInfo> data = sourceFileJsonAdapter.fromJson(json);
				b.sourceFileList = new CoverallsImporter.SourceFileList();
				b.sourceFileList.total = data.size();


				LinkedList<String> javaFiles = new LinkedList<>();
				try (RevWalk walk = new RevWalk(repo)) {
					RevCommit commit = walk.parseCommit(repo.resolve(b.commit_sha));
					b.commitTime = commit.getCommitTime();

					TreeWalk treeWalk = new TreeWalk(repo);
					treeWalk.addTree(commit.getTree());
					treeWalk.setRecursive(true);
					while (treeWalk.next()) {
						if (treeWalk.getPathString().endsWith(".java") && !treeWalk.getPathString().contains("/test/"))
							javaFiles.add(treeWalk.getPathString());
					}
					treeWalk.close();
				}

				b.sourceFileList.parsedFiles = new LinkedList<>();
				for (Map.Entry<String, JaCoCoSourceFileInfo> e : data.entrySet()) {
					if (e.getKey().contains("$"))
						continue; //Defer for simplicity
					CoverallsImporter.SourceFile f = new CoverallsImporter.SourceFile();
					f.name = findFileNameFromJavaName(javaFiles, e.getKey());
					if (f.name == null)
						continue;
					b.sourceFileList.parsedFiles.add(f);
					int max = 0;
					for (int i : e.getValue().allStatmentLinesThisClass)
						if (i > max)
							max = i;
					f.coverage = new ArrayList<Integer>(max + 1);
					for (int i = 0; i <= 1 + max; i++)
						f.coverage.add(null);
					for (Integer i : e.getValue().allStatmentLinesThisClass) {
						f.coverage.set(i, 0);
						f.relevant_line_count++;
					}
					for (Integer i : e.getValue().hitLinesThisClass) {
						f.coverage.set(i, 1);
						f.covered_line_count++;
					}
					f.covered_percent = (double) f.covered_line_count / ((double) f.relevant_line_count);
				}
				for (Map.Entry<String, JaCoCoSourceFileInfo> e : data.entrySet()) {
					if (!e.getKey().contains("$"))
						continue;
					String name = findFileNameFromJavaName(javaFiles, e.getKey().substring(0, e.getKey().indexOf('$')));
					if (name == null)
						continue;
					boolean found = false;
					for (CoverallsImporter.SourceFile f : b.sourceFileList.parsedFiles)
						if (f.name.equals(name)) {
							found = true;
							int max = 0;
							for (int i : e.getValue().allStatmentLinesThisClass)
								if (i > max)
									max = i;
							for (int i = f.coverage.size(); i <= 1 + max; i++)
								f.coverage.add(null);
							for (Integer i : e.getValue().allStatmentLinesThisClass) {
								if (f.coverage.get(i) == null) {
									f.relevant_line_count++;
									f.coverage.set(i, 0);
								}
							}
							for (Integer i : e.getValue().hitLinesThisClass) {
								if (f.coverage.get(i) == null || f.coverage.get(i) == 0) {
									f.covered_line_count++;
									f.coverage.set(i, 1);
								}
							}
							break;
						}
					if (!found)
						System.out.println("PROBLEM2");

				}


			}

			for (JaCoCoBuild b : buildsMap.get(repoName).values()) {
				JaCoCoBuild parent = buildsMap.get(repoName).get(b.parentInternalID);
				if (parent != null) {
					CoverallsImporter.DiffResult res = b.diffAgainst(parent, repo, debugWriter);
					outputWriter.print(b.date + "," + b.repo_name + "," + b.commit_sha + "," + parent.commit_sha + "," + b.branch + "," + b.commitTime + "," + res.toCSV());
				} else {
					System.out.println("Dangling parent for " + b.internalID + ", wanted " + b.parentInternalID);
				}
			}
			buildsMap.get(repoName).clear();
			outputWriter.flush();
		}
		if(debug)
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

	static class JaCoCoBuild extends CoverallsImporter.Build {
		int internalID;
		int parentInternalID;

	}
}

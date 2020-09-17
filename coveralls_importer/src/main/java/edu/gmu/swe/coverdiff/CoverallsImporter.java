package edu.gmu.swe.coverdiff;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.util.concurrent.RateLimiter;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;


import picocli.CommandLine;
import picocli.CommandLine.Option;

public class CoverallsImporter implements Callable<Void> {

    static boolean fixFencePostInt = false;
    static boolean exception = false;
    private static boolean VERBOSE = false;
    final RateLimiter rateLimiter = RateLimiter.create(1.4);
    private final Moshi moshi = new Moshi.Builder().build();
    private final JsonAdapter<BuildList> buildListJsonAdapter = moshi.adapter(BuildList.class);
    private final JsonAdapter<SourceFileList> sourceFileListJsonAdapter = moshi.adapter(SourceFileList.class);
    private final JsonAdapter<List<SourceFile>> sourceFileJsonAdapter = moshi.adapter(Types.newParameterizedType(List.class, SourceFile.class));
    private final JsonAdapter<List<Integer>> coverageListJsonAdapter = moshi.adapter(Types.newParameterizedType(List.class, Integer.class));
    @Option(names = {"-d"}, description = "Directory to store repos in")
    File repoBaseDirectory = new File("repos");
    @Option(names = {"-c"}, description = "Directory to cache JSON in")
    File cacheDirectory = new File("cache");
    @Option(names = {"-sc"}, description = "Directory to cache parsed JSON in")
    File serialCachedDirectory = new File("serialized-cache");
    @Option(names = {"-offline"}, description = "Offline mode (uses cache only)")
    boolean offline = false;
    @Option(names = {"-fixFence"}, description = "Fix Coveralls data to be 1-indexed instead of 0 from serialized file (do not use normally unless importing old serialized files that don't have this fixed... that includes the original ASE data files")
    boolean fixFencepost = false;
    @Option(names = {"-shaLists"}, description = "Download only the SHAs listed in shaLists/slug for each project slug; -page becomes a limit on # of builds not pages")
    File shaListsDir;
    URLCache cache;
    @Option(names = {"-p"}, description = "URL of git repository to analyze")
    String singleProjectURL;
    @Option(names = {"-i"}, description = "Path to a .csv where the first column is a list of GH project URLs")
    String inputFile;
    @Option(names = {"-o"}, description = "Output file")
    String outputFile = "coverage.csv";
    @Option(names = {"-page"}, description = "Number of pages of coveralls builds to fetch, -1 to fetch all")
    int pages = 1;
    @Option(names = {"-debug"}, description = "Debug mode (prints out file-level coverage too)")
    boolean debug = true;
    @Option(names = {"--ignore-serialized"}, description = "Ignore any pre-processed, serialized data and re-construct from JSON (using JSON cache if available)")
    boolean skipSerialized = false;
    @Option(names = {"-noCoverage"}, description = "Skip downloading coverage, just get build info")
    boolean skipCoverage = false;
    AtomicInteger outstandingRequests = new AtomicInteger(0);
    ExecutorService executorService = Executors.newFixedThreadPool(16);
    HashSet<String> shasToFetch;
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
    private boolean helpRequested;
    private boolean hasHeader = false;
    @Option(names = {"-jacoco"}, description = "Enable jacoco mode (must be specified first, unlocks other options)")
    private boolean jacoco = false;
    private HttpRequestFactory requestFactory;
    private AtomicInteger requestsMade = new AtomicInteger(0);
    private AtomicInteger requestsServedFromCache = new AtomicInteger(0);
    private PrintWriter outputWriter;

    public CoverallsImporter() {
        /*
         * 5000 requests/hr -> 83 requests/minute -> ~1.4 rq/sec
         */
        requestFactory = new NetHttpTransport().createRequestFactory();
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("-jacoco"))
            CommandLine.call(new LegacyJaCoCoMySQLImporter(), System.err, args);
        else
            CommandLine.call(new CoverallsImporter(), System.err, args);

    }

    static String getLeftParentSHA(Repository repo, String commit) throws IOException {
//		System.out.println("FINDING PARENT OF: "+ commit);
        try (RevWalk revWalk = new RevWalk(repo)) {
            RevCommit revcom = revWalk.parseCommit(repo.resolve(commit));
            if (revcom.getParentCount() > 1) {
//				System.out.println("MULTIPLE PARENTS!!!");
//				return null;
                return revcom.getParent(0).getName();
            } else if (revcom.getParentCount() == 0) {
                return null;
            } else {
//			System.out.println("PARENT is: "+ revcom.getParent(0).getName());
                return revcom.getParent(0).getName();
            }
        } catch (MissingObjectException ex) {
            // ex.printStackTrace();
//			System.out.println("Missing Parent for:"+commit);
            return null;
        }
    }

    private static String output(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(inputStream));
            String line = null;
            while ((line = br.readLine()) != null) {
                sb.append(line + System.getProperty("line.separator"));
            }
        } finally {
            br.close();
        }
        return sb.toString();
    }

    private static String systemCall(List<String> command) throws IOException {
        String output = "";
        // public static void main (String[]args) throws InterruptedException,
        // IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (VERBOSE) {
            System.out.println("Run ");
            for (String s : command) {
                System.out.print(s + " ");
            }
        }
        Process process = null;
        try {
            process = pb.start();
            output = output(process.getInputStream());

            boolean exited = process.waitFor(30, TimeUnit.SECONDS);
            int errCode = -1;
            if (!exited) {
                process.destroyForcibly();
                System.out.println("Process timed out");
            } else
                errCode = process.exitValue();
            if (VERBOSE)
                System.out.println("Echo command executed, any errors? " + (errCode == 0 ? "No" : "Yes"));
            if (errCode != 0) {
                System.out.println("Tried to run ");
                for (String s : command) {
                    System.out.print(s + " ");
                }
                System.out.println("Error code was " + errCode);
                throw new IOException("Unable to read result of call");
            }
            // System.out.println("Echo Output:\n" + output);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return output;
        // }
    }

    CompletableFuture<String> fetch(String url) {
        return CompletableFuture.supplyAsync(() -> {
            boolean cacheable = url.startsWith("https://coveralls.io/builds/");
            if (cacheable) {

                try {
                    String ret = cache.get(url);
                    if (ret != null) {
                        return ret;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            rateLimiter.acquire();

            int n = requestsMade.incrementAndGet();
            if (n % 100 == 0) {
                System.out.println("[Benchmark] Total requests made: " + n);
            }

            try {
                HttpRequest request = requestFactory.buildGetRequest(new GenericUrl(url));
                String rawResponse = request.execute().parseAsString();
                if (cacheable) {
                    System.out.println("Caching response of: " + url);
                    cache.save(url, rawResponse);
                }
                return rawResponse;
            } catch (IOException e) {
                throw new IllegalStateException("While fetching " + url, e);
            }

        }, executorService);
    }

    void fetchSourceFileInfo(final Build b, int page) throws IOException {
        if (exception)
            throw new IOException();
        outstandingRequests.incrementAndGet();
        String urlToFetch = "https://coveralls.io/builds/" + b.commit_sha + "/source_files.json";
        System.out.println("Fetching url: " + urlToFetch);
        CompletableFuture<String> rq = fetch(urlToFetch);
        rq.handleAsync((String ret, Throwable ex) -> {
            try {
                if (ret == null) {
                    System.err.println("No result!");
                } else if (ex != null) {
                    ex.printStackTrace();
                } else {
                    SourceFileList sfl = sourceFileListJsonAdapter.fromJson(ret);
                    sfl.parsedFiles = sourceFileJsonAdapter.fromJson(sfl.source_files);
                    sfl.source_files = null;
                    b.appendSFL(sfl);
                    if (sfl.current_page == 1 && sfl.total_pages > 1) {
                        for (int i = 2; i < sfl.total_pages; i++)
                            fetchSourceFileInfo(b, i);
                    }

                    // Now fetch data on each file
                    for (SourceFile sf : sfl.parsedFiles) {
                        getCoverageArray(b.commit_sha, sf);
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                synchronized (outstandingRequests) {
                    outstandingRequests.decrementAndGet();
                    outstandingRequests.notify();
                }
            }
            return null;

        });
    }

    boolean shouldFetchBuild(Build b) {
        if (b.commit_sha.equals("HEAD"))
            return true;
        if (shasToFetch == null)
            return true;
        synchronized (shasToFetch) {
            return shasToFetch.remove(b.commit_sha);
        }
    }

    BuildList fetchBuilds(String coverallsPath, int page) throws IOException {
        if (exception)
            throw new IOException();
        // Don't bother with async here
        String urlToFetch = "https://coveralls.io/" + coverallsPath + ".json?page=" + page;
        CompletableFuture<String> res = fetch(urlToFetch);
        System.out.println("Fetch start: " + urlToFetch);
        try {
            String bl = res.get();
            BuildList buildList = buildListJsonAdapter.fromJson(bl);
            if (!skipCoverage) {
                List<Build> existing = buildList.builds;
                buildList.builds = new LinkedList<>();
                for (Build b : existing) {
                    if (shouldFetchBuild(b)) {
                        fetchSourceFileInfo(b, 1);
                        buildList.builds.add(b);
                    }
                }
            } else {
                for (Build b : buildList.builds) {
                    System.out.println(b.commit_sha);
                    outputWriter.print(b.commit_sha + "," + b.branch + "\n");
                    outputWriter.flush();
                }
            }
            System.out.println("Fetch end: " + urlToFetch + "; Number of builds fetched: " + buildList.builds.size());
            return buildList;

        } catch (Throwable e) {
            throw new IOException("While fetching builds for " + urlToFetch, e);
        }
    }

    /**
     * Fetches coverage array from coveralls.io and adds it to SourceFile.coverage (ArrayList)
     * Sample url: https://coveralls.io/builds/2ea77ec5eeea2351de50b268994ba69f876b815c/source.json?filename=lib%2Fcoveralls%2Fsimplecov.rb
     * @param commit
     * @param sf
     * @throws IOException
     */
    void getCoverageArray(String commit, final SourceFile sf) throws IOException {
        if (exception) throw new IOException();
        outstandingRequests.incrementAndGet();
        String urlToFetch = "https://coveralls.io/builds/" + commit + "/source.json?filename=" + sf.name;
        System.out.println("Fetching url: " + urlToFetch);
        CompletableFuture<String> rq = fetch(urlToFetch);
        rq.handleAsync((String ret, Throwable ex) -> {
            try {
                if (ret == null) {
                    System.err.println("No result!");
                } else if (ex != null) {
                    ex.printStackTrace();
                } else {
                    sf.coverage = new ArrayList<>();
                    sf.coverage.add(-1);
                    sf.coverage.addAll(coverageListJsonAdapter.fromJson(ret));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                synchronized (outstandingRequests) {
                    outstandingRequests.decrementAndGet();
                    outstandingRequests.notify();
                }
            }
            return null;

        });
    }

    boolean shouldKeepFetching(BuildList bl) {
        if (shaListsDir == null) {
            // Decide to stop based on # of pages fetched
            return bl.page < bl.pages && (pages < 0 || bl.page <= pages);
        } else {
            bl.builds = bl.nonEmptyBuilds();
            // Decide to stop based on # of builds
            return bl.page < bl.pages && bl.builds.size() < pages;
        }
    }

    /**
     * If shaListsDir is specified, we will only download the SHAs listed
     * in shaLists/slug for each project slug; (see options for callable)
     */
    HashSet<String> getShasToFetch(String projectSlug){
        if (shaListsDir == null) return null;
        File shaFile = new File(shaListsDir, projectSlug);

        shasToFetch = new HashSet<>();
        try (Scanner s = new Scanner(shaFile)) {
            while (s.hasNextLine())
                shasToFetch.add(s.nextLine());
        } catch (FileNotFoundException e) {
            return null;
        }
        return shasToFetch;
    }

    BuildList processBuilds(String projectURL) throws Exception {
        System.out.println("Fetching and processing builds from: " + projectURL);
        exception = false;

        // Calculating coveralls path of Github repo
        String githubPath = projectURL.replace("https://github.com/", "");
        String projectSlug = githubPath.replace("/", "-");
        String coverallsPath = "/github/" + githubPath;

        shasToFetch = getShasToFetch(projectSlug);

        File cachedParsed = new File(serialCachedDirectory, projectSlug);
        BuildList bl;
        if (!cachedParsed.exists() || skipSerialized) {
            if (offline) return null;

            System.out.println("Fetching builds from coveralls.io");
            bl = fetchBuilds(coverallsPath, 1);
            while (shouldKeepFetching(bl)) {
                BuildList _bl = fetchBuilds(coverallsPath, bl.page + 1);
                bl.page = _bl.page;
                bl.builds.addAll(_bl.builds);
            }
            System.out.println("Made all async requests. Waiting for requests to finish.");
            while (outstandingRequests.get() > 0)
                synchronized (outstandingRequests) {
                    outstandingRequests.wait();
                }
            System.out.println("Done waiting. Builds fetched: " + bl.builds.size());

            System.out.println("Caching builds to file.");
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cachedParsed));
            oos.writeObject(bl);
            oos.close();

        } else {

            System.out.println("Not fetching from coveralls.io. Parsing cached builds from file: " + cachedParsed);
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cachedParsed));
            bl = (BuildList) ois.readObject();
            ois.close();

        }

        if (skipCoverage) return bl;

        HashMap<String, Build> builds = new HashMap<>();
        for (Build b : bl.builds) builds.put(b.commit_sha, b);

        PrintWriter debugWriter = null;
        if (debug) {
            debugWriter = new PrintWriter(new FileWriter("coverage_class.csv"));
            debugWriter.print(DiffResult.toCSVDebugHeader());
        }

        outputWriter = new PrintWriter(new FileWriter(outputFile, true));
        if (!hasHeader) {
            outputWriter.print(SrcTestGeneralDiffResult.toCSVHeader());
            hasHeader = true;
        }

        File repoDir = new File(repoBaseDirectory, projectSlug);
        if (!repoDir.exists()) {
            Git.cloneRepository().setURI(projectURL).setBare(true).setDirectory(repoDir).call();
        }

        FileRepositoryBuilder frb = new FileRepositoryBuilder();
        frb.setGitDir(repoDir);
        frb.setBare();
        Repository repo = frb.build();
        int parentsFound = 0;
        int parentNotFound = 0;
        for (Build b : bl.builds) {

            Build ancestorBuild = findAncestor(b.commit_sha, builds, repo);

            if (ancestorBuild == null) {
                parentNotFound++;
                System.out.println("Can't find ancestor build for commit sha:" + b.commit_sha + " in repo: "  + b.repo_name );
                continue;
            }

            parentsFound++;
            try {
                SrcTestGeneralDiffResult sum = b.diffAgainst(ancestorBuild, repo, debugWriter);

                // Printing in the specified csv output format:
                outputWriter.print(b.repo_name + "," + b.commit_sha + "," + ancestorBuild.commit_sha + "," + b.branch + "," + b.commitTime + "," + sum.toCSV());
            } catch (Throwable t) {
                t.printStackTrace();
            }

        }

        System.out.println("Parents found: " + parentsFound + "; Parents not found: " + parentNotFound + "; Total parents: " + bl.builds.size());

        if (debug) debugWriter.close();
        outputWriter.close();

        return bl;
    }

    /*
    * Find the nearest ancestor of a given commit.
    * */
    private Build findAncestor(String childCommit, HashMap<String, Build> builds, Repository repo) throws IOException {
        String parent = getLeftParentSHA(repo, childCommit);
        if (parent == null) {
            // Can't find any ancestor for commit. Giving up search.
            return null;
        }
        Build parentBuild = builds.get(parent);
        if (parentBuild == null) {
            // Missing this build's parent. Try to find it's grandparent instead.
            return findAncestor(parent, builds, repo);
        } else {
            // Found build parent
            return parentBuild;
        }

    }

    // Entry function of CoverallsImporter (Callable)
    @Override
    public Void call() throws Exception {
        fixFencePostInt = fixFencepost;
        File file = new File(outputFile);
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (skipCoverage) {
            skipSerialized = true;
            outputWriter = new PrintWriter(new FileWriter(outputFile, true));
            outputWriter.println("SHA,branch");
        }
        this.cache = new URLCache(cacheDirectory.toPath());
        if (inputFile != null) {
            try (Scanner scanner = new Scanner(new File(inputFile))) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    String url = line.split(",")[0];
                    try {
                        BuildList ret = processBuilds(url);
                        if (ret != null)
                            System.out.println(ret.summarize());
                    } catch (Throwable ex) {
                        System.err.println("Exception on " + url);
                        ex.printStackTrace();
                    }
                }
                System.out.println("Done, shutting down");
                executorService.shutdown();
                if (skipCoverage)
                    outputWriter.close();
            }
            return null;
        } else if (singleProjectURL != null) {
            BuildList ret = processBuilds(singleProjectURL);
            if (ret != null)
                System.out.println(ret.summarize());
            return null;
        }
        throw new IllegalArgumentException("Specify either projectURL or inputFile");
    }

    static class BuildList implements Serializable {
        /**
         *
         */
        private static final long serialVersionUID = 7227397267135178861L;
        int page;
        int pages;
        int total;
        List<Build> builds;


        public List<Build> nonEmptyBuilds() {
            LinkedList<Build> ret = new LinkedList<>();
            for (Build b : builds) {
                if (b.sourceFileList != null && b.sourceFileList.parsedFiles != null)
                    ret.add(b);
            }
            return ret;
        }

        @Override
        public String toString() {
            return "BuildList [page=" + page + ", pages=" + pages + ", total=" + total + ", builds=" + builds + "]";
        }

        public String summarize() {
            int nBuilds = 0;
            int nSourceFiles = 0;
            for (Build b : builds) {
                if (b.sourceFileList != null && b.sourceFileList.parsedFiles != null)
                    nSourceFiles += b.sourceFileList.parsedFiles.size();
                nBuilds++;
            }
            return nBuilds + " builds fetched, " + nSourceFiles + " total source files coverage files fetched";
        }

    }

    static class Build implements Serializable {

        /**
         *
         */
        private static final long serialVersionUID = -5689754868054900876L;
        public SourceFileList sourceFileList;
        String url;
        String date;
        String repo_name;
        double coverage_change;
        double coverage_percent;
        String commit_sha;
        String branch;
        transient String parent_sha;
        transient int commitTime;
        private transient boolean namesFixed;

        static final boolean isCodeFile(String s) {
            return s.endsWith(".java") || s.endsWith(".go") || s.endsWith(".scala") || s.endsWith(".js") || s.endsWith(".cs") || s.endsWith(".ex") || s.endsWith(".exs") || s.endsWith(".py") || s.endsWith(".ts");
        }

        @Override
        public String toString() {
            return "Build [url=" + url + ", date=" + date + ", repo_name=" + repo_name + ", coverage_change=" + coverage_change + ", coverage_percent=" + coverage_percent + ", commit_sha=" + commit_sha + "]";
        }

        public synchronized void appendSFL(SourceFileList sfl) {
            if (sourceFileList == null) {
                sourceFileList = sfl;
            } else {
                sourceFileList.parsedFiles.addAll(sfl.parsedFiles);
            }
        }

        public HashSet<String> flappingLines(Build parentBuild, Repository repo, Build mostRecent) throws IOException {
            HashSet<String> ret = new HashSet<>();

            ObjectReader reader = repo.newObjectReader();
            CanonicalTreeParser thisCommParser = new CanonicalTreeParser();
            SrcTestGeneralDiffResult summary = new SrcTestGeneralDiffResult();

            try (RevWalk revWalk = new RevWalk(repo)) {
                // OK, compare all of the files
                // System.out.println(commit_sha);
                // System.out.println(parentBuild.commit_sha);
                HashMap<String, SourceFile> filesInFuture = new HashMap<>();
                for (SourceFile sf : mostRecent.sourceFileList.parsedFiles)
                    filesInFuture.put(sf.name, sf);
                HashMap<String, SourceFile> filesInNew = new HashMap<>();
                for (SourceFile sf : sourceFileList.parsedFiles)
                    if (filesInFuture.containsKey(sf.name))
                        filesInNew.put(sf.name, sf);
                HashMap<String, SourceFile> filesInParent = new HashMap<>();
                for (SourceFile sf : parentBuild.sourceFileList.parsedFiles)
                    if (filesInFuture.containsKey(sf.name))
                        filesInParent.put(sf.name, sf);

                RevCommit curCommit = revWalk.parseCommit(repo.resolve(commit_sha));
                RevTree curCommitTree = curCommit.getTree();

                RevCommit prevCommit = revWalk.parseCommit(repo.resolve(parentBuild.commit_sha));
                RevTree prevCommitTree = prevCommit.getTree();


                RevCommit futureCommit = revWalk.parseCommit(repo.resolve(mostRecent.commit_sha));
                RevTree futureCommitTree = futureCommit.getTree();

                thisCommParser.reset(reader, curCommitTree);

                CanonicalTreeParser parentParser = new CanonicalTreeParser();
                parentParser.reset(reader, prevCommitTree);

                CanonicalTreeParser futureParser = new CanonicalTreeParser();
                futureParser.reset(reader, futureCommitTree);

                HashSet<String> modifiedFiles = new HashSet<>();
                HashSet<String> modifiedFilesFuture = new HashSet<>();


                try (DiffFormatter f = new DiffFormatter(System.out)) {
                    f.setRepository(repo);
                    List<DiffEntry> entries = f.scan(parentParser, thisCommParser);
                    for (DiffEntry e : entries) {
                        if (e.getChangeType() == ChangeType.MODIFY) {
                            modifiedFiles.add(e.getNewPath());
                        }
                    }
                    thisCommParser.reset(reader, curCommitTree);
                    entries = f.scan(thisCommParser, futureParser);
                    for (DiffEntry e : entries) {
                        if (e.getChangeType() == ChangeType.MODIFY) {
                            modifiedFilesFuture.add(e.getNewPath());
                        }
                    }
                }

                HashMap<String, HashMap<Integer, Integer>> prevLineMaps = new HashMap<>();
                HashMap<String, HashMap<Integer, Integer>> futureLineMaps = new HashMap<>();
                // Find all of the files that changed - those are the
                // only ones we need to open to map
                for (String modifiedFile : modifiedFiles) {
                    SourceFile p = filesInParent.get(modifiedFile);
                    if (p == null) continue;

                    // Do the diff
                    // repo.
                    try (TreeWalk treeWalk = TreeWalk.forPath(repo, modifiedFile, curCommitTree, prevCommitTree)) {
                        byte[] data = reader.open(treeWalk.getObjectId(0)).getBytes();
                        byte[] dataOld = reader.open(treeWalk.getObjectId(1)).getBytes();
                        File newTmp = File.createTempFile("linematcher", "newFile");
                        File oldTmp = File.createTempFile("linematcher", "oldFile");
                        // File newTmp = new File("new");
                        // File oldTmp = new File("old");
                        FileOutputStream fos = new FileOutputStream(newTmp);
                        fos.write(data);
                        fos.close();
                        fos = new FileOutputStream(oldTmp);
                        fos.write(dataOld);
                        fos.close();

                        String diff1 = systemCall(Arrays.asList("/bin/bash", "-c", "diff --unchanged-line-format=\"%dn,%c'\\12'\" --new-line-format=\"n%c'\\12'\" --old-line-format=\"\"  " + newTmp.getAbsolutePath() + " " + oldTmp.getAbsolutePath() + " | awk '/,/{n++;print $0n} /n/{n++}'"));
                        String[] offsetStrings = diff1.split("\n");
                        newTmp.delete();
                        oldTmp.delete();
                        HashMap<Integer, Integer> lineOffsets = new HashMap<>();
                        for (String offset : offsetStrings) {
                            String[] offsetSplit = offset.split(",");
                            try {
                                if (offsetSplit.length == 2)
                                    lineOffsets.put(Integer.parseInt(offsetSplit[0]), Integer.parseInt(offsetSplit[1]));
                            } catch (NumberFormatException ex) {
                                // Random other lines show up here,
                                // f that
                            }
                        }
                        prevLineMaps.put(modifiedFile, lineOffsets);
                    }

                }

                for (String modifiedFile : modifiedFilesFuture) {
                    SourceFile p = filesInNew.get(modifiedFile);
                    if (p != null) {
                        // Do the diff
                        // repo.
                        try (TreeWalk treeWalk = TreeWalk.forPath(repo, modifiedFile, futureCommitTree, curCommitTree)) {
                            byte[] data = reader.open(treeWalk.getObjectId(0)).getBytes();
                            byte[] dataOld = reader.open(treeWalk.getObjectId(1)).getBytes();
                            File newTmp = File.createTempFile("linematcher", "newFile");
                            File oldTmp = File.createTempFile("linematcher", "oldFile");
//							 File newTmp = new File("new");
//							 File oldTmp = new File("old");
                            FileOutputStream fos = new FileOutputStream(newTmp);
                            fos.write(data);
                            fos.close();
                            fos = new FileOutputStream(oldTmp);
                            fos.write(dataOld);
                            fos.close();

                            String diff1 = systemCall(Arrays.asList("/bin/bash", "-c", "diff --unchanged-line-format=\"%dn,%c'\\12'\" --new-line-format=\"n%c'\\12'\" --old-line-format=\"\"  " + newTmp.getAbsolutePath() + " " + oldTmp.getAbsolutePath() + " | awk '/,/{n++;print $0n} /n/{n++}'"));
                            String[] offsetStrings = diff1.split("\n");
                            newTmp.delete();
                            oldTmp.delete();
                            HashMap<Integer, Integer> lineOffsets = new HashMap<>();
                            for (String offset : offsetStrings) {
                                String[] offsetSplit = offset.split(",");
                                try {
                                    if (offsetSplit.length == 2)
                                        lineOffsets.put(Integer.parseInt(offsetSplit[1]), Integer.parseInt(offsetSplit[0]));
                                } catch (NumberFormatException ex) {
                                    // Random other lines show up here,
                                    // f that
                                }
                            }
                            futureLineMaps.put(modifiedFile, lineOffsets);
                        }
                    }
                }

                // OK, now any file that changed have a line map
                // attached. Diff the coverage.
                for (Entry<String, SourceFile> e : filesInNew.entrySet()) {
                    SourceFile prev = filesInParent.get(e.getKey());
                    HashSet<String> flaps = e.getValue().getFlappingLines(prev, prevLineMaps.get(e.getKey()), futureLineMaps.get(e.getKey()));
                    ret.addAll(flaps);
                }
            }
            return ret;

        }

        /*
        * Checking sourceFileList, making sure file names are correct and deleting them in case they are not existent
        * in the Git repository.
        * */
        private void checkAndFixNames(HashSet<String> validNames) {

            if (namesFixed) return;

            System.out.println("Fixing file names in sourceFileList");
            if (this.repo_name.equals("ilovepi/Compiler")) {
                for (SourceFile sf : sourceFileList.parsedFiles) {
                    sf.name = "compiler" + sf.name;
                }
            } else if (this.repo_name.equals("ManageIQ/ui-components")) {
                for (SourceFile sf : sourceFileList.parsedFiles) {
                    sf.name = sf.name.replace("../", "");
                }
            } else if (this.repo_name.equals("SteamDatabase/ValveResourceFormat")) {
                for (SourceFile sf : sourceFileList.parsedFiles) {
                    sf.name = sf.name.replace("C/projects/valveresourceformat/", "");
                }
            } else if (this.repo_name.equals("ShiftForward/apso")) {
                for (SourceFile sf : sourceFileList.parsedFiles) {
                    if (sf.name.startsWith("src/")) {
                        if (validNames.contains("core/" + sf.name))
                            sf.name = "core/" + sf.name;
                        else if (validNames.contains("testkit/" + sf.name))
                            sf.name = "testkit/" + sf.name;
                    }
                }
            }
            namesFixed = true;

            System.out.println("Removing non-existent files from sourceFileList");
            HashSet<SourceFile> filesToIgnore = new HashSet<>();
            for (SourceFile sf : sourceFileList.parsedFiles) {
                if (!validNames.contains(sf.name)) {
                    System.out.println("Git repo does not contain file: " + sf.name);
                    filesToIgnore.add(sf);
                }
            }
            sourceFileList.parsedFiles.removeAll(filesToIgnore);
        }

        HashSet<String> getFilesInGit(Repository repo, RevTree commitTree){
            TreeWalk treeWalk = new TreeWalk(repo);
            HashSet<String> filesInGit = new HashSet<>();
            try {
                treeWalk.addTree(commitTree);
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    filesInGit.add(treeWalk.getPathString());
                }
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
            treeWalk.close();
            return filesInGit;
        }

        public SrcTestGeneralDiffResult diffAgainst(Build parentBuild, Repository repo, PrintWriter debugWriter) throws IOException {
            ObjectReader reader = repo.newObjectReader();
            CanonicalTreeParser thisCommParser = new CanonicalTreeParser();
            CanonicalTreeParser parentParser = new CanonicalTreeParser();
            SrcTestGeneralDiffResult summary = new SrcTestGeneralDiffResult();

            try (RevWalk revWalk = new RevWalk(repo)) {
                // Compare all of the files

                RevCommit curCommit = revWalk.parseCommit(repo.resolve(commit_sha));
                RevTree curCommitTree = curCommit.getTree();

                commitTime = curCommit.getCommitTime();

                RevCommit prevCommit = revWalk.parseCommit(repo.resolve(parentBuild.commit_sha));
                RevTree prevCommitTree = prevCommit.getTree();

                HashSet<String> filesInCurGit = getFilesInGit(repo, curCommitTree);
                HashSet<String> filesInPrevGit = getFilesInGit(repo, prevCommitTree);

                this.checkAndFixNames(filesInCurGit);
                parentBuild.checkAndFixNames(filesInPrevGit);

                thisCommParser.reset(reader, curCommitTree);
                parentParser.reset(reader, prevCommitTree);

                List<DiffEntry> entries;
                HashSet<String> modifiedFiles = new HashSet<>();

                try (DiffFormatter f = new DiffFormatter(System.out)) {
                    f.setRepository(repo);
                    entries = f.scan(parentParser, thisCommParser);
                    for (DiffEntry e : entries) {
                        if (e.getNewPath() != null && (e.getNewPath().startsWith("vendor/") || e.getNewPath().startsWith("node_modules/") || e.getNewPath().startsWith("gen/") || e.getNewPath().contains("generated")))
                            continue;
                        EditList el = f.toFileHeader(e).toEditList();
                        switch (e.getChangeType()) {
                            case MODIFY:
                                if (isCodeFile(e.getNewPath())) {
                                    for (Edit ed : el) {
                                        switch (ed.getType()) {
                                            case INSERT:
                                                for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                                    if (e.getNewPath().toLowerCase().contains("test") || e.getNewPath().contains(".spec"))
                                                        summary.insLinesTest++;
                                                    else
                                                        summary.insLinesSrc++;
                                                break;
                                            case DELETE:
                                                for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                                    if (e.getNewPath().toLowerCase().contains("test") || e.getNewPath().contains(".spec"))
                                                        summary.delLinesTest++;
                                                    else
                                                        summary.delLinesSrc++;
                                                break;
                                            case REPLACE:
                                                for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                                    if (e.getNewPath().toLowerCase().contains("test") || e.getNewPath().contains(".spec"))
                                                        summary.delLinesTest++;
                                                    else
                                                        summary.delLinesSrc++;
                                                for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                                    if (e.getNewPath().toLowerCase().contains("test") || e.getNewPath().contains(".spec"))
                                                        summary.insLinesTest++;
                                                    else
                                                        summary.insLinesSrc++;
                                                break;
                                            default:
                                                break;
                                        }
                                    }
                                    if (e.getNewPath().toLowerCase().contains("test") || e.getNewPath().contains(".spec"))
                                        summary.modFilesTest++;
                                    else
                                        summary.modFilesSrc++;
                                }
                                break;
                            case ADD:
                                if (isCodeFile(e.getNewPath())) {
                                    for (Edit ed : el) {
                                        for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                            if (e.getNewPath().toLowerCase().contains("test") || e.getNewPath().contains(".spec"))
                                                summary.insLinesTest++;
                                            else
                                                summary.insLinesSrc++;
                                    }
                                    if (e.getNewPath().toLowerCase().contains("test") || e.getNewPath().contains(".spec"))
                                        summary.insFilesTest++;
                                    else
                                        summary.insFilesSrc++;
                                }
                                break;
                            case DELETE:
                                if (isCodeFile(e.getOldPath())) {
                                    for (Edit ed : el) {
                                        for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                            if (e.getNewPath().toLowerCase().contains("test") || e.getNewPath().contains(".spec"))
                                                summary.delLinesTest++;
                                            else
                                                summary.delLinesSrc++;
                                    }
                                    if (e.getOldPath().toLowerCase().contains("test") || e.getOldPath().contains(".spec"))
                                        summary.delFilesTest++;
                                    else
                                        summary.delFilesSrc++;
                                }
                                break;
                        }

                        switch (e.getChangeType()) {
                            case MODIFY:
                                for (Edit ed : el) {
                                    switch (ed.getType()) {
                                        case INSERT:
                                            for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                                summary.insLinesAllFiles++;
                                            break;
                                        case DELETE:
                                            for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                                summary.delLinesAllFiles++;
                                            break;
                                        case REPLACE:
                                            for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                                summary.delLinesAllFiles++;
                                            for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                                summary.insLinesAllFiles++;
                                            break;
                                        default:
                                            break;
                                    }
                                }
                                break;
                            case ADD:
                                for (Edit ed : el) {
                                    switch (ed.getType()) {
                                        case INSERT:
                                            for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                                summary.insLinesAllFiles++;
                                            break;
                                        case DELETE:
                                            for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                                summary.delLinesAllFiles++;
                                            break;
                                        case REPLACE:
                                            for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                                summary.delLinesAllFiles++;
                                            for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                                summary.insLinesAllFiles++;
                                            break;
                                        default:
                                            break;
                                    }
                                }
                                break;
                            case DELETE:
                                for (Edit ed : el) {
                                    switch (ed.getType()) {
                                        case INSERT:
                                            for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                                summary.insLinesAllFiles++;
                                            break;
                                        case DELETE:
                                            for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                                summary.delLinesAllFiles++;
                                            break;
                                        case REPLACE:
                                            for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                                summary.delLinesAllFiles++;
                                            for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                                summary.insLinesAllFiles++;
                                            break;
                                        default:
                                            break;
                                    }
                                    break;
                                }
                        }
                        if (e.getChangeType() == ChangeType.MODIFY) {
                            modifiedFiles.add(e.getNewPath());
                            /*
                             * Find which lines are new, modified, deleted
                             */
                            SourceFile p = filesInNew.get(e.getNewPath());
                            if (p == null) {
                                // Maybe because the file is never called
                                // System.out.println("Skipping missing file: "
                                // + e.getNewPath());
                                continue;
                            }
                            for (Edit ed : el) {
                                switch (ed.getType()) {
                                    case INSERT:
                                        for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                            p.newLines.add(l);
                                        break;
                                    case DELETE:
                                        for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                            p.deletedLines.add(l);
                                        break;
                                    case REPLACE:
                                        for (int l = ed.getBeginA() + 1; l <= ed.getEndA(); l++)
                                            p.deletedLines.add(l);
                                        for (int l = ed.getBeginB() + 1; l <= ed.getEndB(); l++)
                                            p.newLines.add(l);
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                    }
                }

                HashMap<String, SourceFile> filesInNew = new HashMap<>();
                for (SourceFile sf : sourceFileList.parsedFiles)
                    filesInNew.put(sf.name, sf);
                HashMap<String, SourceFile> filesInParent = new HashMap<>();
                for (SourceFile sf : parentBuild.sourceFileList.parsedFiles)
                    filesInParent.put(sf.name, sf);

                // Find all of the files that changed - those are the
                // only ones we need to open to map
                for (String modifiedFile : modifiedFiles) {
                    SourceFile p = filesInParent.get(modifiedFile);
                    if (p != null && filesInNew.containsKey(modifiedFile)) {
                        // Do the diff
                        // repo.
                        try (TreeWalk treeWalk = TreeWalk.forPath(repo, modifiedFile, curCommitTree, prevCommitTree)) {
                            byte[] data = reader.open(treeWalk.getObjectId(0)).getBytes();
                            byte[] dataOld = reader.open(treeWalk.getObjectId(1)).getBytes();
                            File newTmp = File.createTempFile("linematcher", "newFile");
                            File oldTmp = File.createTempFile("linematcher", "oldFile");
                            // File newTmp = new File("new");
                            // File oldTmp = new File("old");
                            FileOutputStream fos = new FileOutputStream(newTmp);
                            fos.write(data);
                            fos.close();
                            fos = new FileOutputStream(oldTmp);
                            fos.write(dataOld);
                            fos.close();

                            String diff1 = systemCall(Arrays.asList("/bin/bash", "-c", "diff --unchanged-line-format=\"%dn,%c'\\12'\" --new-line-format=\"n%c'\\12'\" --old-line-format=\"\"  " + newTmp.getAbsolutePath() + " " + oldTmp.getAbsolutePath() + " | awk '/,/{n++;print $0n} /n/{n++}'"));
                            String[] offsetStrings = diff1.split("\n");
                            newTmp.delete();
                            oldTmp.delete();
                            HashMap<Integer, Integer> lineOffsets = new HashMap<>();
                            for (String offset : offsetStrings) {
                                String[] offsetSplit = offset.split(",");
                                try {
                                    if (offsetSplit.length == 2)
                                        lineOffsets.put(Integer.parseInt(offsetSplit[0]), Integer.parseInt(offsetSplit[1]));
                                } catch (NumberFormatException ex) {
                                    // Random other lines show up here,
                                    // f that
                                }
                            }
                            filesInNew.get(modifiedFile).lineMapping = lineOffsets;
                        }
                    }
                }

                // OK, now any file that changed have a line map
                // attached. Diff the coverage.
                for (Entry<String, SourceFile> e : filesInNew.entrySet()) {
                    SourceFile prev = filesInParent.get(e.getKey());
//					System.out.println("currCommit: "+curCommit);
                    DiffResult diff = e.getValue().diff(prev);
                    if (debugWriter != null)
                        debugWriter.print(this.repo_name + ',' + this.commit_sha + ',' + parentBuild.commit_sha + ',' + this.branch + ',' + e.getKey() + ',' + diff.toCSV());
                    summary.accumulate(diff, e.getKey());
                }
                HashSet<String> filesInPrevNotCoveredInNew = new HashSet<>();
                filesInPrevNotCoveredInNew.addAll(filesInParent.keySet());
                filesInPrevNotCoveredInNew.removeAll(filesInNew.keySet());
                for (String sf : filesInPrevNotCoveredInNew) {
                    SourceFile empty = new SourceFile();
                    empty.coverage = new ArrayList<>();
                    DiffResult diff = empty.diff(filesInParent.get(sf));
                    if (debugWriter != null)
                        debugWriter.print(this.repo_name + ',' + this.commit_sha + ',' + parentBuild.commit_sha + ',' + this.branch + ',' + sf + ',' + diff.toCSV());
                    summary.accumulate(diff, sf);
                }
            }
            return summary;
        }

    }

    static class SrcTestGeneralDiffResult extends DiffResult {
        DiffResult nonTest = new DiffResult();
        DiffResult test = new DiffResult();
        DiffResult all = new DiffResult();
        int modFiles = 0;
        int delFiles = 0;
        int insFiles = 0;
        int modFilesSrc = 0;
        int delFilesSrc = 0;
        int insFilesSrc = 0;
        int modFilesTest = 0;
        int delFilesTest = 0;
        int insFilesTest = 0;

        int delLinesTest = 0;
        int delLinesSrc = 0;
        int insLinesTest = 0;
        int insLinesSrc = 0;

        int insLinesAllFiles = 0;
        int delLinesAllFiles = 0;


        static void appendHeader(StringBuilder sb, String prefix) {
            sb.append(',');
            if (prefix != null)
                sb.append(prefix);
            sb.append("newHitLines,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("newNonHitLines,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("newFileHitLines,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("newFileNonHitLines,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("deletedLinesTested,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("deletedLinesNotTested,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("deletedFileLinesTested,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("deletedFileLinesNotTested,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("oldLinesNewlyTested,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("oldLinesNoLongerTested,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("modifiedLinesNewlyHit,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("modifiedLinesStillHit,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("modifiedLinesNotHit,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("nStatementsInBoth,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("nStatementsInEither,");
            // sb.append(",nStatementsThis");
            if (prefix != null)
                sb.append(prefix);
            sb.append("totalStatementsHitNow,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("totalStatementsHitPrev,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("totalStatementsNow,");
            if (prefix != null)
                sb.append(prefix);
            sb.append("totalStatementsPrev");
        }

        public static String toCSVHeader() {
            StringBuilder sb = new StringBuilder();
            sb.append("repo");
            sb.append(",childSha");
            sb.append(",parentSha");
            sb.append(",childBranch");
            sb.append(",timestamp");
            appendHeader(sb, null);
//			appendHeader(sb,"test_");
//			appendHeader(sb,"src_");
            sb.append(",insFilesSrc,insFilesTest,modFilesSrc,modFilesTest,delFilesSrc,delFilesTest,newLinesSrc,newLinesTest,delLinesSrc,delLinesTest,insLinesAllFiles,delLinesAllFiles");
            sb.append('\n');
            return sb.toString();
        }

        public static String toCSVDebugHeader() {
            StringBuilder sb = new StringBuilder();
            sb.append("repo");
            sb.append(",childSha");
            sb.append(",parentSha");
            sb.append(",childBranch,file");
            appendHeader(sb, null);
//			appendHeader(sb,"test");
//			appendHeader(sb,"src");
            return sb.toString();
        }

        public void accumulate(DiffResult o, String fileName) {
//			if(fileName.toLowerCase().contains("test"))
//				test.accumulate(o);
//			else
//				nonTest.accumulate(o);
            all.accumulate(o);
        }

        @Override
        public String toCSV() {
            StringBuilder sb = new StringBuilder();
            all.toCSV(sb);
            sb.append(',');
//			test.toCSV(sb);
//			sb.append(',');
//			nonTest.toCSV(sb);
//			sb.append(',');
            sb.append(insFilesSrc);
            sb.append(',');
            sb.append(insFilesTest);
            sb.append(',');
            sb.append(modFilesSrc);
            sb.append(',');
            sb.append(modFilesTest);
            sb.append(',');
            sb.append(delFilesSrc);
            sb.append(',');
            sb.append(delFilesTest);
            sb.append(',');
            sb.append(insLinesSrc);
            sb.append(',');
            sb.append(insLinesTest);
            sb.append(',');
            sb.append(delLinesSrc);
            sb.append(',');
            sb.append(delLinesTest);
            sb.append(',');
            sb.append(insLinesAllFiles);
            sb.append(',');
            sb.append(delLinesAllFiles);
            sb.append(',');
            sb.append('\n');
            return sb.toString();
        }

    }

    /**
     * This class describes a summary of one or multiple diffs in terms of aggregated numbers. It does not go into any
     *  details with respect to which lines have been changed and how they have been changed.
     */
    static class DiffResult implements Serializable {

        /**
         *
         */
        private static final long serialVersionUID = -766592368068201095L;
        int modifiedLinesNewlyHit;
        int modifiedLinesNoLongerHit;
        int modifiedLinesStillHit;
        int newLinesHit;
        int newLinesNotHit;
        int newFileLinesHit;
        int newFileLinesNotHit;
        int deletedLinesHit;
        int deletedLinesNotHit;
        int deletedFileLinesHit;
        int deletedFileLinesNotHit;
        int oldLinesNewlyHit;
        int oldLinesNoLongerHit;
        int nStatementsHitInBoth;
        int nStatementsHitInEither;
        int totalStatementsHitNow;
        int totalStatementsNow;
        int totalStatementsHitPrev;
        int totalStatementsPrev;

        public static String toCSVHeader() {
            StringBuilder sb = new StringBuilder();
            sb.append("repo");
            sb.append(",childSha");
            sb.append(",parentSha");
            sb.append(",childBranch");
            sb.append(",newHitLines");
            sb.append(",newNonHitLines");
            sb.append(",newFileHitLines");
            sb.append(",newFileNonHitLines");
            sb.append(",deletedLinesTested");
            sb.append(",deletedLinesNotTested");
            sb.append(",deletedFileLinesTested");
            sb.append(",deletedFileLinesNotTested");
            sb.append(",oldLinesNewlyTested");
            sb.append(",oldLinesNoLongerTested");
            sb.append(",modifiedLinesNewlyHit");
            sb.append(",modifiedLinesStillHit");
            sb.append(",modifiedLinesNotHit");
            sb.append(",nStatementsInBoth");
            sb.append(",nStatementsInEither");
            // sb.append(",nStatementsThis");
            sb.append(",totalStatementsHitNow");
            sb.append(",totalStatementsHitPrev");
            sb.append(",totalStatementsNow");
            sb.append(",totalStatementsPrev");
            sb.append('\n');
            return sb.toString();
        }

        public static String toCSVDebugHeader() {
            StringBuilder sb = new StringBuilder();
            sb.append("repo");
            sb.append(",childSha");
            sb.append(",parentSha");
            sb.append(",childBranch");
            sb.append(",file");
            sb.append(",newHitLines");
            sb.append(",newNonHitLines");
            sb.append(",newFileHitLines");
            sb.append(",newFileNonHitLines");
            sb.append(",deletedLinesTested");
            sb.append(",deletedLinesNotTested");
            sb.append(",deletedFileLinesTested");
            sb.append(",deletedFileLinesNotTested");
            sb.append(",oldLinesNewlyTested");
            sb.append(",oldLinesNoLongerTested");
            sb.append(",modifiedLinesNewlyHit");
            sb.append(",modifiedLinesStillHit");
            sb.append(",modifiedLinesNotHit");
            sb.append(",nStatementsInBoth");
            sb.append(",nStatementsInEither");
            // sb.append(",nStatementsThis");
            sb.append(",totalStatementsHitNow");
            sb.append(",totalStatementsHitPrev");
            sb.append(",totalStatementsNow");
            sb.append(",totalStatementsPrev");
            sb.append('\n');
            return sb.toString();
        }

        public void accumulate(DiffResult o) {
            if (o == null)
                return;
            this.modifiedLinesNewlyHit += o.modifiedLinesNewlyHit;
            this.modifiedLinesNoLongerHit += o.modifiedLinesNoLongerHit;
            this.modifiedLinesStillHit += o.modifiedLinesStillHit;
            this.newLinesHit += o.newLinesHit;
            this.newLinesNotHit += o.newLinesNotHit;
            this.deletedLinesHit += o.deletedLinesHit;
            this.deletedLinesNotHit += o.deletedLinesNotHit;
            this.newFileLinesHit += o.newFileLinesHit;
            this.newFileLinesNotHit += o.newFileLinesNotHit;
            this.deletedFileLinesHit += o.deletedFileLinesHit;
            this.deletedFileLinesNotHit += o.deletedFileLinesNotHit;
            this.oldLinesNewlyHit += o.oldLinesNewlyHit;
            this.oldLinesNoLongerHit += o.oldLinesNoLongerHit;
            this.nStatementsHitInBoth += o.nStatementsHitInBoth;
            this.nStatementsHitInEither += o.nStatementsHitInEither;
            this.totalStatementsHitNow += o.totalStatementsHitNow;
            this.totalStatementsNow += o.totalStatementsNow;
            this.totalStatementsHitPrev += o.totalStatementsHitPrev;
            this.totalStatementsPrev += o.totalStatementsPrev;
        }

        public String toCSV() {
            StringBuilder sb = new StringBuilder();
            toCSV(sb);
            sb.append('\n');
            return sb.toString();
        }

        public void toCSV(StringBuilder sb) {
            sb.append(newLinesHit);
            sb.append(',');
            sb.append(newLinesNotHit);
            sb.append(',');
            sb.append(newFileLinesHit);
            sb.append(',');
            sb.append(newFileLinesNotHit);
            sb.append(',');
            sb.append(deletedLinesHit);
            sb.append(',');
            sb.append(deletedLinesNotHit);
            sb.append(',');
            sb.append(deletedFileLinesHit);
            sb.append(',');
            sb.append(deletedFileLinesNotHit);
            sb.append(',');
            sb.append(oldLinesNewlyHit);
            sb.append(',');
            sb.append(oldLinesNoLongerHit);
            sb.append(',');
            sb.append(modifiedLinesNewlyHit);
            sb.append(',');
            sb.append(modifiedLinesStillHit);
            sb.append(',');
            sb.append(modifiedLinesNoLongerHit);
            sb.append(',');
            sb.append(nStatementsHitInBoth);
            sb.append(',');
            sb.append(nStatementsHitInEither);
            sb.append(',');
            sb.append(totalStatementsHitNow);
            sb.append(',');
            sb.append(totalStatementsHitPrev);
            sb.append(',');
            sb.append(totalStatementsNow);
            sb.append(',');
            sb.append(totalStatementsPrev);
        }

        @Override
        public String toString() {
            return "DiffResult [modifiedLinesHit=" + modifiedLinesNewlyHit +
                    ", modifiedLinesNoLongerHit=" + modifiedLinesNoLongerHit +
                    ", newLinesHit=" + newLinesHit +
                    ", newLinesNotHit=" + newLinesNotHit +
                    ", newFileLinesHit=" + newFileLinesHit +
                    ", newFileLinesNotHit=" + newFileLinesNotHit +
                    ", deletedLinesHit=" + deletedLinesHit +
                    ", deletedLinesNotHit=" + deletedLinesNotHit +
                    ", deletedFileLinesHit=" + deletedFileLinesHit +
                    ", deletedFileLinesNotHit=" + deletedFileLinesNotHit +
                    ", oldLinesNewlyHit=" + oldLinesNewlyHit +
                    ", oldLinesNoLongerHit=" + oldLinesNoLongerHit +
                    ", nStatementsInBoth=" + nStatementsHitInBoth +
                    ", nStatementsHitInEither=" + nStatementsHitInEither +
                    ", totalStatementsHitNow=" + totalStatementsHitNow +
                    ", totalStatementsNow=" + totalStatementsNow +
                    ", totalStatementsHitPrev=" + totalStatementsHitPrev +
                    ", totalStatementsPrev=" + totalStatementsPrev + "]";
        }

    }

    static class SourceFileList implements Serializable {
        private static final long serialVersionUID = 174237175589062016L;
        int total;
        int total_pages;
        int current_page;
        String source_files;
        List<SourceFile> parsedFiles;

        @Override
        public String toString() {
            return "SourceFileList [total=" + total + ", total_pages=" + total_pages + ", current_page=" + current_page + ", parsedFiles=" + parsedFiles + "]";
        }

    }

    static class SourceFile implements Serializable {
        private static final long serialVersionUID = 8951794222372644260L;
        String name;
        int relevant_line_count;
        int covered_line_count;
        int missed_line_count;
        double covered_percent;
        List<Integer> coverage;
        transient HashMap<Integer, Integer> lineMapping;
        transient HashSet<Integer> newLines = new HashSet<>();
        transient HashSet<Integer> deletedLines = new HashSet<>();


        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            newLines = new HashSet<>();
            deletedLines = new HashSet<>();
            if (fixFencePostInt && coverage != null) {
                ArrayList<Integer> newCoverage = new ArrayList<>(coverage.size());
                newCoverage.add(-1);
                newCoverage.addAll(coverage);
                coverage = newCoverage;
            }
        }

        @Override
        public String toString() {
            return "SourceFile [name=" + name + ", relevant_line_count=" + relevant_line_count + ", covered_line_count=" + covered_line_count + ", missed_line_count=" + missed_line_count + ", covered_percent=" + covered_percent + ", coverage=" + coverage + "]";
        }

        public HashSet<String> getFlappingLines(SourceFile prev, HashMap<Integer, Integer> lineMapping, HashMap<Integer, Integer> futureMap) {
            HashSet<String> ret = new HashSet<>();

            if (prev != null && prev.coverage != null) {
                //existedBefore, but not now
                if (this.coverage == null || (this.name == null && this.coverage.size() == 0)) {
                } else {
                    // File in both
                    HashSet<Integer> linesOnlyInPrev = new HashSet<>();
                    for (int i = 0; i < prev.coverage.size(); i++)
                        linesOnlyInPrev.add(i); //after we successfully map a line from NOW to PREV we remove from this list
                    for (int line = 0; line < coverage.size(); line++) {
                        Integer curHits = coverage.get(line);
                        Integer prevHits = null;
                        int lineInFuture = line;
                        if (futureMap != null) {
                            if (futureMap.containsKey(line))
                                lineInFuture = futureMap.get(line);
                            else
                                continue;
                        }
                        if (lineMapping == null) {
                            if (prev.coverage.size() > line) {
                                prevHits = prev.coverage.get(line);
                                linesOnlyInPrev.remove(line);
                            }
                        } else if (lineMapping.containsKey(line) && lineMapping.get(line) < prev.coverage.size()) {
                            int mapping = lineMapping.get(line);
                            linesOnlyInPrev.remove(mapping);
                            prevHits = prev.coverage.get(mapping);
                            //else
                            //	linesOnlyInPrev.add(line);
                        }
                        if (curHits != null) {
                            // Is a statement now.
                            if ((prevHits == null || prevHits == 0)) {
                                // Wasnt hit before
                                if (curHits > 0) {
                                    // Line not hit before, hit now
                                    if (newLines.contains(line)) {
                                    } else {
                                        ret.add(this.name + "," + lineInFuture + ",1");
                                    }
                                }
                            } else if (prevHits != null && prevHits > 0) {
                                // Was hit before
                                if (curHits == 0) {
                                    ret.add(this.name + "," + lineInFuture + ",0");
                                }
                            }
                        }
                    }
                }
            }
            return ret;
        }

        public DiffResult diff(SourceFile prev) {
            DiffResult ret = new DiffResult();

            if (prev != null && prev.coverage != null) {
                //existedBefore, but not now
                if (this.coverage == null || (this.name == null && this.coverage.size() == 0)) {
                    for (int line = 1; line < prev.coverage.size(); line++) {
                        Integer prevHits = prev.coverage.get(line);
                        if (prevHits != null) {
                            if (prevHits > 0) {
                                ret.deletedFileLinesHit++;
                            } else {
                                ret.deletedFileLinesNotHit++;
                            }
                        }
                    }
                } else {
                    // File in both
                    HashSet<Integer> linesOnlyInPrev = new HashSet<>();
                    for (int i = 1; i < prev.coverage.size(); i++)
                        linesOnlyInPrev.add(i); //after we successfully map a line from NOW to PREV we remove from this list
                    for (int line = 1; line < coverage.size(); line++) {
                        Integer curHits = coverage.get(line);
                        Integer prevHits = null;
                        if (lineMapping == null) {
                            if (prev.coverage.size() > line) {
                                prevHits = prev.coverage.get(line);
                                linesOnlyInPrev.remove(line);
                            }
                        } else if (lineMapping.containsKey(line) && lineMapping.get(line) < prev.coverage.size()) {
                            int mapping = lineMapping.get(line);
                            linesOnlyInPrev.remove(mapping);
                            prevHits = prev.coverage.get(mapping);
                            //else
                            //	linesOnlyInPrev.add(line);
                        }
                        if (curHits != null) {
                            // Is a statement now.
                            ret.totalStatementsNow++;
                            if (prevHits != null)
                                ret.totalStatementsPrev++;
                            if ((prevHits == null || prevHits == 0)) {
                                // Wasnt hit before
                                if (curHits > 0) {
                                    // Line not hit before, hit now
                                    ret.nStatementsHitInEither++;
                                    ret.totalStatementsHitNow++;
                                    if (newLines.contains(line)) {
                                        // And new
                                        ret.newLinesHit++;
                                    } else {
                                        ret.oldLinesNewlyHit++;
                                    }
                                } else if (curHits < 1) {
                                    if (prevHits == null) {
                                        ret.newLinesNotHit++;
                                    }
                                }
                            } else if (prevHits != null && prevHits > 0) {
                                // Was hit before
                                ret.nStatementsHitInEither++;
                                ret.totalStatementsHitPrev++;
                                if (curHits > 0) {
                                    // Was hit before AND now
                                    ret.nStatementsHitInBoth++;
                                    ret.totalStatementsHitNow++;
                                } else {
                                    // Was hit before but NOT now
                                    ret.oldLinesNoLongerHit++;
                                }
                            }
                        } else if (prevHits != null) {
                            ret.totalStatementsPrev++;
                            if (prevHits > 0) {
                                ret.totalStatementsHitPrev++;
                                ret.nStatementsHitInEither++;
                            }
                        }
                    }

                    // Now collect deleted lines
                    for (Integer i : deletedLines) {
                        //System.out.println(i);
                        int prevLineNumber = i;
                        linesOnlyInPrev.remove(i);
//					System.out.println("i:"+i);
//					System.out.println("Prev.Cov Size:"+ prev.coverage.size());
//					System.out.println("prevLineNumber: "+ prevLineNumber);
                        if (prev.coverage.size() <= prevLineNumber) {
                            if (prev.coverage.size() == 0)
                                System.out.println("PROBLEM " + prevLineNumber + " vs " + prev.coverage.size() + " in " + this.name);
                        }
                        if (prev.coverage.size() > prevLineNumber) {
                            Integer deletedLineCov = prev.coverage.get(prevLineNumber);
                            if (deletedLineCov != null && deletedLineCov > 0) {
                                ret.deletedLinesHit++;
                                ret.nStatementsHitInEither++;
                                ret.totalStatementsHitPrev++;
                            } else if (deletedLineCov != null) {
                                ret.deletedLinesNotHit++;
                                ret.totalStatementsPrev++;
                            }
                        }
                    }
                    for (Integer line : linesOnlyInPrev) {
                        Integer hit = prev.coverage.get(line);
                        if (hit != null) {
                            ret.totalStatementsPrev++;
                            if (hit > 0) {
                                ret.totalStatementsHitPrev++;
                                ret.nStatementsHitInEither++;
                            }
                        }
                    }
                }
            } else {
                // Had 0 coverage prev
                // Might still be a modified file, but did not get any coverage
                // last run
                if (coverage != null)
                    for (int line = 1; line < coverage.size(); line++) {
                        Integer curHits = coverage.get(line);
                        if (curHits != null) {
                            ret.totalStatementsNow++;
                            if (curHits > 0) {
                                // Wasnt hit before, hit now
                                ret.nStatementsHitInEither++;
//							ret.totalStatementsHitNow++;
                                if (newLines.contains(line)) {
                                    // And new
                                    ret.newLinesHit++;
                                } else {
                                    if (prev != null) {
                                        //THIS IS UNREACHABLE
                                        ret.oldLinesNewlyHit++;
                                    } else {
                                        ret.newFileLinesHit++;
                                    }
                                    ret.totalStatementsHitNow++;
                                }
                            } else if (curHits == 0) {
                                // Was hit before
                                if (prev != null) {
                                    //THIS IS UNREACHABLE
                                    ret.oldLinesNoLongerHit++;
                                } else {
                                    ret.newFileLinesNotHit++;
                                }
                            }
                        }
                    }
                if (prev != null)
                    for (Integer i : deletedLines) {
                        if (i < lineMapping.size()) {
                            ret.deletedLinesNotHit++;
                        }
                    }
//				 throw new UnsupportedOperationException();
            }
            return ret;
        }

    }
}

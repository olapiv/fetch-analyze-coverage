package edu.gmu.swe.coverdiff;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
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
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Scanner;

public class getShaData {
    public static void main(String[] args) {
        File[] files = new File("./repos/").listFiles();


        WindowCacheConfig wcc = new WindowCacheConfig();
        wcc.setPackedGitLimit(128 * WindowCacheConfig.KB);
        wcc.setPackedGitWindowSize(8 * WindowCacheConfig.KB);
        wcc.setDeltaBaseCacheLimit(8 * WindowCacheConfig.KB);
        wcc.install();

        File csvFile = new File("./ShaAndTime.csv");
        try {
            /* This logic is to create the file if the
             * file is not already present
             */
            if (!csvFile.exists()) {
                csvFile.delete();
                csvFile.createNewFile();
            }
            //Here true is to append the content to file
            FileWriter fw = null;
            fw = new FileWriter(csvFile, true);

            //BufferedWriter writer give better performance
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("SHA,timeStamp\n");
            bw.flush();
            for (File file : files) {
                if (file.isDirectory()) {
                    System.out.println("Directory: " + file.getName());
                    try {
                        if (file.getName().equals("nanobox-io-nanobox-pkgsrc-lite")) {
                            System.out.println("skipping: " + file.getName());
                        } else {
                            getAllShaDates(file, bw); // Calls same method again.
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (GitAPIException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("File: " + file.getName());
                }
            }


            //Closing BufferedWriter Stream
            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private static void getAllShaDates(File directory, BufferedWriter bw) throws IOException, GitAPIException {

        Git git = Git.open(directory);
        Iterable<RevCommit> iterable = git.log().call();

        FileRepositoryBuilder frb = new FileRepositoryBuilder();
        frb.setGitDir(directory);
        frb.setBare();
        Repository repo = frb.build();

        ObjectReader reader = repo.newObjectReader();
        CanonicalTreeParser thisCommParser = new CanonicalTreeParser();
        CanonicalTreeParser parentParser = new CanonicalTreeParser();

        try (RevWalk revWalk = new RevWalk(repo)) {
            for (RevCommit commit : iterable) {

               if(!commit
                       .toString().contains("db5e073893e7ca98101ecde6c58c316673f78506"))
                   continue;
                int totalAdded = 0;
                int totalRemoved = 0;
                if (commit.getParentCount() > 0) {
                    RevTree curCommitTree = commit.getTree();
                    RevCommit prevCommit = revWalk.parseCommit(commit.getParent(0));
                    RevTree prevCommitTree = prevCommit.getTree();

                    thisCommParser.reset(reader, curCommitTree);
                    parentParser.reset(reader, prevCommitTree);
                    try (DiffFormatter f = new DiffFormatter(System.out)) {
                        f.setRepository(repo);
                        List<DiffEntry> entries = f.scan(parentParser, thisCommParser);
                        for (DiffEntry e : entries) {
                            EditList el = f.toFileHeader(e).toEditList();
                            if (e.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                                for (Edit ed : el) {
                                    totalAdded += ed.getEndB() - ed.getBeginB();
                                    totalRemoved += ed.getEndA() - ed.getBeginA();
                                }
                            } else if (e.getChangeType() == DiffEntry.ChangeType.ADD) {
                                for (Edit ed : el)
                                    totalAdded += ed.getEndB() - ed.getBeginB();
                            } else if (e.getChangeType() == DiffEntry.ChangeType.DELETE) {
                                for (Edit ed : el)
                                    totalRemoved += ed.getEndA() - ed.getBeginA();
                            }
                        }
                    }
                }
                String[] splitCommit = commit.toString().split(" ");
                //    System.out.println(splitCommit[1]+","+splitCommit[2]);
                bw.write(splitCommit[1] + "," + splitCommit[2] + "," + totalAdded + "," + totalRemoved + "\n");
            }
        }
    }


}

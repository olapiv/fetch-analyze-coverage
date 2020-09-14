package edu.gmu.swe.coverdiff;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;

public class StatGenerator {
	public static void main(String[] args) throws Exception{
		File[] files = new File("./coveralls_importer/serialized-cache-no-branch/").listFiles();
		for(File f : files)
		{
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
			CoverallsImporter.BuildList bl = (CoverallsImporter.BuildList) ois.readObject();
			int nLines = 0;
			int nCovered = 0;
			int nFiles = 0;
			if(bl.builds.size() > 0)
			{
				CoverallsImporter.SourceFileList fl = bl.builds.get(0).sourceFileList;
				if(fl != null && fl.parsedFiles != null){
					for(CoverallsImporter.SourceFile c : fl.parsedFiles)
					{
						nLines += c.relevant_line_count;
						nCovered += c.covered_line_count;
						nFiles++;
					}
				}
			}
			System.out.println(f.getName() +","+bl.total+","+nLines+","+nCovered+","+nFiles);
			ois.close();
		}
	}
}

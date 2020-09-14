package edu.gmu.swe.coverdiff;

import java.io.*;
import java.util.*;

public class OrderSha {
	public static void main(String[] args) throws Exception {

		String csvFile = "../r_scripts/coverage.csv";
		BufferedReader br = null;
		String line = "";
		String cvsSplitBy = ",";

		File of = new File("shaOrders.csv");
		boolean printHeader = of.exists();
		PrintWriter outputWriter = new PrintWriter(new FileOutputStream(of, true));
		if (printHeader)
			outputWriter.println("project,idx,sha");
		int offset = 0;
		if (csvFile.contains("jacoco"))
			offset = 1;
		HashMap<String, HashMap<String, String>> hash = new HashMap<>();

		try {
			br = new BufferedReader(new FileReader(csvFile));

			while ((line = br.readLine()) != null) {

				// use comma as separator
				String[] commit = line.split(cvsSplitBy);
				String project = commit[0 + offset];
				String childSha = commit[1 + offset];
				String parentSha = commit[2 + offset];
				String pair = childSha.concat("#").concat(parentSha);
				if (hash.containsKey(project)) {
					HashMap currHashTable = hash.get(project);
					currHashTable.put(childSha, parentSha);
					hash.put(project, currHashTable);
				} else {
					HashMap currHashTable = new HashMap();
					currHashTable.put(childSha, parentSha);
					hash.put(project, currHashTable);
				}

			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		for (String project : hash.keySet()) {
			//First find the root


			Graph g = new Graph();
			HashMap<String, String> shas = hash.get(project);
			for (Map.Entry<String, String> childToParent : shas.entrySet()) {
				Vertex childV = g.getVertex(childToParent.getKey());
				if (!shas.containsKey(childToParent.getValue())) {
					g.roots.add(childV);
				} else {
					Vertex parentV = g.getVertex(childToParent.getValue());
					parentV.addEdge(childV);
				}

			}
			int deepest = 0;
			Vertex longestRoot = null;
			for (Vertex v : g.roots) {
				int depth = v.getMaxDepth();
				if (depth > deepest) {
					deepest = depth;
					longestRoot = v;
				} else {
					System.out.println("Discarding root depth " + depth + " in " + project);
				}
			}
			//print out a traversal of the longest leg
			Vertex v = longestRoot;
			int i = 0;
			while (v != null) {
				outputWriter.println(project + "," + i + "," + v.sha);
				if (v.deepestEdge != null)
					v = v.deepestEdge.child;
				else
					v = null;
				i++;
			}
		}

		outputWriter.close();
	}

	static int getLongestPathDepth(String sha, HashMap<String, LinkedList<String>> graph) {
		LinkedList<String> successors = graph.get(sha);
		if (successors == null)
			return 0;
		int d = 0;
		for (String s : successors) {

		}

		return d;
	}

	static class Graph {
		LinkedList<Vertex> roots = new LinkedList<>();
		HashMap<String, Vertex> vertexMap = new HashMap<>();

		Vertex getVertex(String sha) {
			if (!vertexMap.containsKey(sha)) {
				Vertex v = new Vertex();
				v.sha = sha;
				vertexMap.put(sha, v);
			}
			return vertexMap.get(sha);
		}
	}

	static class Vertex {
		HashSet<Edge> edges = new HashSet<>();
		String sha;
		int maxDepth = -1;
		Edge deepestEdge = null;

		void addEdge(Vertex child) {
			Edge e = new Edge();
			e.child = child;
			edges.add(e);
		}

		int getMaxDepth() {
			if (maxDepth >= 0)
				return maxDepth;
			for (Edge e : edges) {
				e.length = e.child.getMaxDepth() + 1;
				if (e.length > maxDepth) {
					maxDepth = e.length;
					deepestEdge = e;
				}
			}
			if (maxDepth < 0)
				maxDepth = 0;
			return maxDepth;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Vertex vertex = (Vertex) o;
			return Objects.equals(sha, vertex.sha);
		}

		@Override
		public int hashCode() {

			return Objects.hash(sha);
		}
	}

	static class Edge {
		Vertex child;
		int length = -1;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Edge edge = (Edge) o;
			return Objects.equals(child, edge.child);
		}

		@Override
		public int hashCode() {

			return Objects.hash(child);
		}
	}
}

package it.cnr.istc.stlab.felg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MosemousUtils {

	public static Map<String, String> getMonosemousVerbs(String file) {
		Map<String, String> result = new HashMap<>();

//		List<String[]> rows = CSVToJavaUtils.toStringMatrix(file, '\t');
		List<String[]> rows = new ArrayList<>();

		for (String[] row : rows) {
			String word = row[0].replace("\"", "").replace('_', ' ');
			String synset = row[2];
			result.put(word, synset);
		}

		return result;
	}
	
	public static void main(String[] args) {
		System.out.println(getMonosemousVerbs("/Users/lgu/Dropbox/repository/workspace/FELG/felg.wiki.filter/src/main/resources/bnMonosemousVerbs.tsv").size());
	}
}

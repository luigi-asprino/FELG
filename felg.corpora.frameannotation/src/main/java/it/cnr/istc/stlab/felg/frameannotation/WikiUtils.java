package it.cnr.istc.stlab.felg.frameannotation;

import it.cnr.istc.stlab.felg.frameannotation.model.AnnotatedArticle;

public class WikiUtils {
	public static boolean isList(AnnotatedArticle a) {
		return a.getTitle().startsWith("List");
	}

	public static boolean isDisambiguation(AnnotatedArticle a) {
		return a.getSentences().iterator().next().getSentence().contains("may refer to");
	}
	
	public static boolean isCategory(AnnotatedArticle a) {
		return a.getTitle().startsWith("Category:");
	}
}

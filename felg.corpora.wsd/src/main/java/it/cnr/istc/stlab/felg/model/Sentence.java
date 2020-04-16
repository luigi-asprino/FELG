package it.cnr.istc.stlab.felg.model;

import java.util.ArrayList;
import java.util.List;

public class Sentence {

	private List<Word> words;
	private String articleTitle;

	public Sentence(getalp.wsd.ufsac.core.Sentence s) {
		super();
		List<Word> words = new ArrayList<>();
		s.getWords().forEach(w -> words.add(new Word(w)));
		this.words = words;
		this.articleTitle = s.getAnnotationValue("art");
	}

	public Sentence() {
		super();
	}

	public List<Word> getWords() {
		return words;
	}

	public void setWords(List<Word> words) {
		this.words = words;
	}

	@Override
	public String toString() {
		return "Sentence [words=" + words + "]";
	}

	public getalp.wsd.ufsac.core.Sentence getUFSACSentence() {
		List<getalp.wsd.ufsac.core.Word> ufsacWords = new ArrayList<>();
		words.forEach(w -> ufsacWords.add(w.getUFSACWord()));
		getalp.wsd.ufsac.core.Sentence s = new getalp.wsd.ufsac.core.Sentence(ufsacWords);
		s.setAnnotation("art", articleTitle);
		return s;
	}

	public String getArticleTitle() {
		return articleTitle;
	}

	public void setArticleTitle(String articleTitle) {
		this.articleTitle = articleTitle;
	}

}

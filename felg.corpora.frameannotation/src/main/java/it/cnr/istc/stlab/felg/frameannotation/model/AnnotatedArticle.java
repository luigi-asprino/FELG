package it.cnr.istc.stlab.felg.frameannotation.model;

import java.util.ArrayList;
import java.util.List;

public class AnnotatedArticle {

	private String title;
	private String url;
	private String text;
	private List<AnnotatedSentence> sentences=new ArrayList<>();

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public List<AnnotatedSentence> getSentences() {
		return sentences;
	}

	public void setSentences(List<AnnotatedSentence> sentences) {
		this.sentences = sentences;
	}

}

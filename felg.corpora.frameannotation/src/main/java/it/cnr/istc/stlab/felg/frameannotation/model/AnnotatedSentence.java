package it.cnr.istc.stlab.felg.frameannotation.model;

import java.util.ArrayList;
import java.util.List;

public class AnnotatedSentence {

	private String sentence;
	private List<AnnotatedMultiWord> annotations = new ArrayList<>();
	private int begin, end;

	public String getSentence() {
		return sentence;
	}

	public void setSentence(String sentence) {
		this.sentence = sentence;
	}

	public List<AnnotatedMultiWord> getAnnotations() {
		return annotations;
	}

	public void setAnnotations(List<AnnotatedMultiWord> annotations) {
		this.annotations = annotations;
	}

	public int getBegin() {
		return begin;
	}

	public void setBegin(int begin) {
		this.begin = begin;
	}

	public int getEnd() {
		return end;
	}

	public void setEnd(int end) {
		this.end = end;
	}
	
	
}

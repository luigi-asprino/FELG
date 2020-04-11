package it.cnr.istc.stlab.felg.model;

public class AnnotatedWord {

	private String word, senseKey;
	private boolean last;

	public AnnotatedWord(String word, String senseKey) {
		super();
		this.word = word;
		this.senseKey = senseKey;
	}
	
	public AnnotatedWord(String word) {
		this(word,null);
	}

	public String getWord() {
		return word;
	}

	public String getSenseKey() {
		return senseKey;
	}

	public void setWord(String word) {
		this.word = word;
	}

	public void setSenseKey(String senseKey) {
		this.senseKey = senseKey;
	}

	public boolean isLast() {
		return last;
	}

	public void setLast(boolean last) {
		this.last = last;
	}
	
	

}

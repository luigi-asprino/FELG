package it.cnr.istc.stlab.felg.model;

import com.jsoniter.annotation.JsonIgnore;

public class Word {

	private String token, lemma, pos;

	public Word(String token, String pos, String lemma) {
		super();
		this.token = token;
		this.lemma = lemma;
		this.pos = pos;
	}

	public Word(getalp.wsd.ufsac.core.Word w) {
		this(w.getValue(), w.getAnnotationValue("pos"), w.getAnnotationValue("lemma"));
	}

	public Word() {

	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public String getLemma() {
		return lemma;
	}

	public void setLemma(String lemma) {
		this.lemma = lemma;
	}

	public String getPos() {
		return pos;
	}

	public void setPos(String pos) {
		this.pos = pos;
	}

	@JsonIgnore
	public getalp.wsd.ufsac.core.Word getUFSACWord() {
		getalp.wsd.ufsac.core.Word result = new getalp.wsd.ufsac.core.Word(token);
		result.setAnnotation("pos", pos);
		result.setAnnotation("lemma", lemma);
		return result;
	}

	@Override
	public String toString() {
		return "Word [token=" + token + ", lemma=" + lemma + ", pos=" + pos + "]";
	}

}

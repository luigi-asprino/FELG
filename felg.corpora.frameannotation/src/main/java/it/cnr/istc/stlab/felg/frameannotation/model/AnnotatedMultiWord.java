package it.cnr.istc.stlab.felg.frameannotation.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AnnotatedMultiWord {

	private String mention, lemma, pos, bnSynset, dbPediaUri, annotationType, frame, fsyn;
	private List<String> frames = new ArrayList<>(), classes = new ArrayList<>(), fsyns = new ArrayList<>();
	private int beg, end;

	public String getMention() {
		return mention;
	}

	public void setMention(String mention) {
		this.mention = mention;
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

	public String getBnSynset() {
		return bnSynset;
	}

	public void setBnSynset(String bnSynset) {
		this.bnSynset = bnSynset;
	}

	public String getDbPediaUri() {
		return dbPediaUri;
	}

	public void setDbPediaUri(String dbPediaUri) {
		this.dbPediaUri = dbPediaUri;
	}

	public String getAnnotationType() {
		return annotationType;
	}

	public void setAnnotationType(String annotationType) {
		this.annotationType = annotationType;
	}

	public Collection<String> getFrames() {
		return frames;
	}

	public void setFrames(List<String> frames) {
		this.frames = frames;
	}

	public Collection<String> getClasses() {
		return classes;
	}

	public void setClasses(List<String> classes) {
		this.classes = classes;
	}

	public int getBeg() {
		return beg;
	}

	public void setBeg(int beg) {
		this.beg = beg;
	}

	public int getEnd() {
		return end;
	}

	public void setEnd(int end) {
		this.end = end;
	}

	public String getFrame() {
		return frame;
	}

	public void setFrame(String frame) {
		this.frame = frame;
	}

	public String getFsyn() {
		return fsyn;
	}

	public void setFsyn(String fsyn) {
		this.fsyn = fsyn;
	}

	public List<String> getFsyns() {
		return fsyns;
	}

	public void setFsyns(List<String> fsyns) {
		this.fsyns = fsyns;
	}

}

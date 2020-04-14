package it.cnr.istc.stlab.felg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import getalp.wsd.ufsac.core.Sentence;
import getalp.wsd.ufsac.core.Word;
import getalp.wsd.ufsac.utils.CorpusLemmatizer;

public class WSDWorker implements Runnable {

	private List<String> filepaths;
	private String outputFolder;
	private static final Logger logger = LogManager.getLogger(WSDWorker.class);
	private AtomicLong count;
	private final long t0;
	private StanfordCoreNLP pipeline;
	private CorpusLemmatizer lemmatizer;
	private NeuralWSDDecode nwd;
	private boolean useOnlyAbstract, excludeWrite;

	public WSDWorker(List<String> filepaths, NeuralWSDDecode nwd, String outputFolder, AtomicLong count,
			StanfordCoreNLP pipeline, CorpusLemmatizer lemmatizer, long t0, boolean useOnlyAbstract,
			boolean excludeWrite) {
		super();
		this.filepaths = filepaths;
		this.outputFolder = outputFolder;
		this.nwd = nwd;
		this.count = count;
		this.pipeline = pipeline;
		this.lemmatizer = lemmatizer;
		this.t0 = t0;
		this.useOnlyAbstract = useOnlyAbstract;
		this.excludeWrite = excludeWrite;
	}

	@Override
	public void run() {
		long t0article = 0;
		long t1article = 0;
		long timePerArticle = 0;
		long elapsed = 0;
		try {

			for (String filepath : filepaths) {
				logger.trace("Processing " + filepath);
				if (!FilenameUtils.getExtension(filepath).equals("bz2")) {
					continue;
				}
				ArchiveReader ar = new ArchiveReader(filepath);
				ArticleReader aar;
				while ((aar = ar.nextArticle()) != null) {
					try {
						t0article = System.currentTimeMillis();
						Annotation annotation;

						if (useOnlyAbstract) {
							annotation = new Annotation(aar.getAbstract(true));
						} else {
							annotation = new Annotation(aar.getText(true));
						}

						pipeline.annotate(annotation);

						List<Sentence> sentenceBatch = new ArrayList<>();

						annotation.get(SentencesAnnotation.class).forEach(sentence -> {
							List<CoreLabel> t = sentence.get(TokensAnnotation.class);
							CoreLabel[] tokens = t.toArray(new CoreLabel[t.size()]);
							List<Word> words = new ArrayList<>();
							for (int i = 0; i < tokens.length; i++) {
								Word word = new Word(tokens[i].word());
								word.setAnnotation("pos", tokens[i].get(PartOfSpeechAnnotation.class));
								words.add(word);
							}

							if (words.size() > Main.SENTENCE_THRESHOLD) {
								for (int i = 0; i < words.size(); i += Main.SENTENCE_THRESHOLD) {
									if (((i + 1) * Main.SENTENCE_THRESHOLD) < words.size()) {
										Sentence wsdSentence = new Sentence(
												words.subList(i, (i + 1) * Main.SENTENCE_THRESHOLD));
										lemmatizer.tag(wsdSentence.getWords());
										sentenceBatch.add(wsdSentence);
									} else {
										Sentence wsdSentence = new Sentence(words.subList(i, words.size()));
										lemmatizer.tag(wsdSentence.getWords());
										sentenceBatch.add(wsdSentence);
									}
								}
							} else {
								Sentence wsdSentence = new Sentence(words);
								lemmatizer.tag(wsdSentence.getWords());
								sentenceBatch.add(wsdSentence);
							}

						});

						nwd.disambiguateBatch(sentenceBatch);

						if (!excludeWrite) {
							FileOutputStream fos = new FileOutputStream(new File(outputFolder + "/" + aar.getTitle()));
							sentenceBatch.forEach(wsdSentence -> {
								try {
									StringBuilder sb = new StringBuilder();
									wsdSentence.getWords().forEach(w -> {
										sb.append(w.getValue());
										if (w.hasAnnotation("wsd")) {
											sb.append('|');
											sb.append(w.getAnnotationValue("wsd"));
										}
										sb.append(' ');
									});
									sb.append('\n');
									fos.write(sb.toString().getBytes());
									fos.flush();
								} catch (IOException e) {
									e.printStackTrace();
								}
							});
							fos.flush();
							fos.close();
						}

						t1article = System.currentTimeMillis();
						elapsed = System.currentTimeMillis() - t0;
						timePerArticle = (long) ((double) elapsed / (double) count.incrementAndGet());
						logger.trace("Processed " + aar.getTitle() + " " + timePerArticle + "ms "
								+ (t1article - t0article) + "ms");
					} catch (Exception e) {
						logger.error("Error processing " + aar.getTitle());
						e.printStackTrace();
					}
				}
			}
			// closing wsd
//			nwd.close();
		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}

}

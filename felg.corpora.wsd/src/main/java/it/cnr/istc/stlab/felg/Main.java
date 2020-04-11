package it.cnr.istc.stlab.felg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
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
import it.cnr.istc.stlab.lgu.commons.files.FileUtils;

public class Main {

	private static final Logger logger = LogManager.getLogger(Main.class);

	public static void main(String[] args) throws CompressorException, IOException {
		logger.info("Running FELG");

		try {
			Configurations configs = new Configurations();
			Configuration config;
			if (args.length > 0) {
				config = configs.properties(args[0]);
			} else {
				config = configs.properties("config.properties");
			}

			logger.info("Reading folder " + config.getString("wikiFolder"));
			logger.info("Output Folder folder " + config.getString("outputFolder"));
			logger.debug("Absolute path " + (new File(config.getString("wikiFolder"))).getAbsolutePath());

			Properties props = new Properties();
			props.setProperty("annotators", "tokenize, ssplit, pos");
			StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

			CorpusLemmatizer lemmatizer = new CorpusLemmatizer();
//			CorpusPOSTagger posTagger = new CorpusPOSTagger(false);

			String outputFolder = config.getString("outputFolder");
			String python_path = config.getString("python_path");
			String data_path = config.getString("data_path");
			List<String> weights = new ArrayList<>();
			weights.add(config.getString("weights"));

			// initialize WSD
			NeuralWSDDecode nwd = new NeuralWSDDecode(python_path, data_path, weights);

			logger.info("WSD initialized");

			List<String> filepaths = FileUtils.getFilesUnderTreeRec(config.getString("wikiFolder"));
			for (String filepath : filepaths) {
				logger.trace("Processing " + filepath);
				if (!FilenameUtils.getExtension(filepath).equals("bz2")) {
					continue;
				}
				ArchiveReader ar = new ArchiveReader(filepath);
				ArticleReader aar;
				while ((aar = ar.nextArticle()) != null) {
					logger.trace("Processing " + aar.getTitle());

					Annotation annotation = new Annotation(aar.getAbstract(true));
					pipeline.annotate(annotation);
					FileOutputStream fos = new FileOutputStream(new File(outputFolder + "/" + aar.getTitle()));
					List<Sentence> sentenceBatch = new ArrayList<>();

					annotation.get(SentencesAnnotation.class).forEach(sentence -> {
						List<CoreLabel> t = sentence.get(TokensAnnotation.class);
						CoreLabel[] tokens = t.toArray(new CoreLabel[t.size()]);
						List<Word> words = new ArrayList<>();
						StringBuilder inputSentence = new StringBuilder();
						for (int i = 0; i < tokens.length; i++) {
							Word word = new Word(tokens[i].word());
							word.setAnnotation("pos", tokens[i].get(PartOfSpeechAnnotation.class));
							inputSentence.append(
									(tokens[i].word()) + "_" + tokens[i].get(PartOfSpeechAnnotation.class) + " ");
							words.add(word);
						}
						Sentence wsdSentence = new Sentence(words);
						lemmatizer.tag(wsdSentence.getWords());
						sentenceBatch.add(wsdSentence);

//						try {
//							nwd.disambiguate(wsdSentence);
//							StringBuilder sb = new StringBuilder();
//							wsdSentence.getWords().forEach(w -> {
//								sb.append(w.getValue());
//								if (w.hasAnnotation("wsd")) {
//									sb.append('|');
//									sb.append(w.getAnnotationValue("wsd"));
//								}
//								sb.append(' ');
//							});
//							sb.append('\n');
//							fos.write(sb.toString().getBytes());
//							fos.flush();
//						} catch (IOException e) {
//							e.printStackTrace();
//						}

					});

					nwd.disambiguateBatch(sentenceBatch);
					
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

					logger.trace("Ending " + aar.getTitle());
					fos.flush();
					fos.close();
				}
			}
			// closing wsd
			nwd.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}

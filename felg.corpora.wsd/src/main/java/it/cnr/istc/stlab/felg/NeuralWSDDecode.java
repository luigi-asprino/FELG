package it.cnr.istc.stlab.felg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import getalp.wsd.common.utils.ArgumentParser;
import getalp.wsd.common.wordnet.WordnetHelper;
import getalp.wsd.method.Disambiguator;
import getalp.wsd.method.FirstSenseDisambiguator;
import getalp.wsd.method.neural.NeuralDisambiguator;
import getalp.wsd.ufsac.core.Sentence;
import getalp.wsd.ufsac.core.Word;
import getalp.wsd.ufsac.utils.CorpusPOSTaggerAndLemmatizer;
import getalp.wsd.utils.WordnetUtils;
import it.cnr.istc.stlab.felg.model.AnnotatedWord;

// NeuralWSDDecode adapted from https://github.com/getalp/disambiguate

public class NeuralWSDDecode {

	private static final Logger logger = LogManager.getLogger(NeuralWSDDecode.class);
	private String python_path, data_path;
	private List<String> weights;
	private BlockingQueue<AnnotatedWord> outWords = new LinkedBlockingQueue<AnnotatedWord>();
	private BlockingQueue<String> inSentences = new LinkedBlockingQueue<>();

	public static void main(String[] args) throws Exception {
		new NeuralWSDDecode().decode(args);
	}

	public BlockingQueue<AnnotatedWord> getOutChannel() {
		return outWords;
	}

	public BlockingQueue<String> getInChannel() {
		return inSentences;
	}

	public NeuralWSDDecode(String[] args) throws Exception {
		decode(args);
	}

	public NeuralWSDDecode() throws Exception {
	}

	public NeuralWSDDecode(String python_path, String data_path, List<String> weights) throws Exception {
		this.python_path = python_path;
		this.data_path = data_path;
		this.weights = weights;
	}

	public void decode() throws Exception {
		decode(new String[] {});
	}

	private boolean filterLemma;

	private boolean mfsBackoff;

	private Disambiguator firstSenseDisambiguator;

	private NeuralDisambiguator neuralDisambiguator;

	private AtomicBoolean ready = new AtomicBoolean(false);

	private void decode(String[] args) throws Exception {

		logger.trace("Initializing Neural WSD");

		ArgumentParser parser = new ArgumentParser();
		parser.addArgument("python_path", python_path);
		parser.addArgument("data_path", data_path);
		parser.addArgumentList("weights", weights);
		parser.addArgument("lowercase", "false");
		parser.addArgument("sense_compression_hypernyms", "true");
		parser.addArgument("sense_compression_instance_hypernyms", "false");
		parser.addArgument("sense_compression_antonyms", "false");
		parser.addArgument("sense_compression_file", "");
		parser.addArgument("clear_text", "true");
		parser.addArgument("batch_size", "1");
		parser.addArgument("truncate_max_length", "150");
		parser.addArgument("filter_lemma", "true");
		parser.addArgument("mfs_backoff", "true");
		if (!parser.parse(args))
			return;

		String pythonPath = parser.getArgValue("python_path");
		String dataPath = parser.getArgValue("data_path");
		List<String> weights = parser.getArgValueList("weights");
		boolean lowercase = parser.getArgValueBoolean("lowercase");
		boolean senseCompressionHypernyms = parser.getArgValueBoolean("sense_compression_hypernyms");
		boolean senseCompressionInstanceHypernyms = parser.getArgValueBoolean("sense_compression_instance_hypernyms");
		boolean senseCompressionAntonyms = parser.getArgValueBoolean("sense_compression_antonyms");
		String senseCompressionFile = parser.getArgValue("sense_compression_file");
		boolean clearText = parser.getArgValueBoolean("clear_text");
		int batchSize = parser.getArgValueInteger("batch_size");
		int truncateMaxLength = parser.getArgValueInteger("truncate_max_length");
		filterLemma = parser.getArgValueBoolean("filter_lemma");
		mfsBackoff = parser.getArgValueBoolean("mfs_backoff");

		Map<String, String> senseCompressionClusters = null;
		if (senseCompressionHypernyms || senseCompressionAntonyms) {
			senseCompressionClusters = WordnetUtils.getSenseCompressionClusters(WordnetHelper.wn30(),
					senseCompressionHypernyms, senseCompressionInstanceHypernyms, senseCompressionAntonyms);
		}
		if (!senseCompressionFile.isEmpty()) {
			senseCompressionClusters = WordnetUtils.getSenseCompressionClustersFromFile(senseCompressionFile);
		}

		CorpusPOSTaggerAndLemmatizer tagger = new CorpusPOSTaggerAndLemmatizer();
		logger.trace("POS Tagger initialized");
		firstSenseDisambiguator = new FirstSenseDisambiguator(WordnetHelper.wn30());
		logger.trace("First sense disambiguator initialized");
		neuralDisambiguator = new NeuralDisambiguator(pythonPath, dataPath, weights, clearText, batchSize);
		logger.trace("Neural disambiguator initialized");

		neuralDisambiguator.lowercaseWords = lowercase;
		neuralDisambiguator.filterLemma = filterLemma;
		neuralDisambiguator.reducedOutputVocabulary = senseCompressionClusters;

		List<Sentence> sentences = new ArrayList<>();
		ready.set(true);
		logger.trace("Ready");
		String line;
		while ((line = inSentences.take()) != null) {
			if(line.equals(Main.STOP_TOKEN)) {
				logger.info("Stopping WSD");
				break;
			}
			
			logger.trace("Disambiguating " + line);
			Sentence sentence = new Sentence(line);
			if (sentence.getWords().size() > truncateMaxLength) {
				sentence.getWords().stream().skip(truncateMaxLength).collect(Collectors.toList())
						.forEach(sentence::removeWord);
			}
			if (filterLemma) {
				tagger.tag(sentence.getWords());
			}
			logger.trace("Tagged");
			sentences.add(sentence);
			if (sentences.size() >= batchSize) {
				decodeSentenceBatch(sentences);
				sentences.clear();
			}
			logger.trace("Batch decoded");
			
		}
		decodeSentenceBatch(sentences);
		neuralDisambiguator.close();
	}

	private void decodeSentenceBatch(List<Sentence> sentences) throws IOException {
		neuralDisambiguator.disambiguateDynamicSentenceBatch(sentences, "wsd", "");
		logger.trace("Sentence dynamically disambiguated");
		for (Sentence sentence : sentences) {
			if (mfsBackoff) {
				firstSenseDisambiguator.disambiguate(sentence, "wsd");
			}
			logger.trace(sentence.toString() + " disambiguated!");
			List<Word> words = sentence.getWords();
			Iterator<Word> it = words.iterator();
			while (it.hasNext()) {
				Word word = it.next();
				String wordOut = word.getValue().replace("|", "/");
				AnnotatedWord aw = new AnnotatedWord(wordOut);
				if (/* word.hasAnnotation("lemma") && word.hasAnnotation("pos") && */ word.hasAnnotation("wsd")) {
					aw.setSenseKey(word.getAnnotationValue("wsd"));
				}
				if (!it.hasNext()) {
					aw.setLast(true);
				}
				this.outWords.add(aw);
			}
		}
	}

	public void setPython_path(String python_path) {
		this.python_path = python_path;
	}

	public void setData_path(String data_path) {
		this.data_path = data_path;
	}

	public void setWeights(List<String> weights) {
		this.weights = weights;
	}

	public boolean isReady() {
		return ready.get();
	}

}

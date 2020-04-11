package it.cnr.istc.stlab.felg;

import getalp.wsd.common.wordnet.WordnetHelper;
import getalp.wsd.method.Disambiguator;
import getalp.wsd.method.FirstSenseDisambiguator;
import getalp.wsd.method.neural.NeuralDisambiguator;
import getalp.wsd.ufsac.core.Sentence;
import getalp.wsd.ufsac.core.Word;
import getalp.wsd.ufsac.utils.CorpusPOSTaggerAndLemmatizer;
import getalp.wsd.common.utils.ArgumentParser;
import getalp.wsd.utils.WordnetUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

// NeuralWSDDecode adapted from https://github.com/getalp/disambiguate

public class NeuralWSDDecode {
	
	private static final Logger logger = LogManager.getLogger(NeuralWSDDecode.class);
	private String python_path, data_path;
	private List<String> weights;

	public static void main(String[] args) throws Exception {
		new NeuralWSDDecode().decode(args);
	}

	public NeuralWSDDecode(String[] args) throws Exception {
		decode(args);
	}

	public NeuralWSDDecode() throws Exception {
	}

	public NeuralWSDDecode(String python_path, String data_path, List<String> weights, BufferedWriter writer,
			BufferedReader reader) throws Exception {
		this.python_path = python_path;
		this.data_path = data_path;
		this.weights = weights;
		this.writer = writer;
		this.reader = reader;
	}
	
	public void decode() throws Exception {
		decode(new String[] {});
	}

	private boolean filterLemma;

	private boolean mfsBackoff;

	private Disambiguator firstSenseDisambiguator;

	private NeuralDisambiguator neuralDisambiguator;

	private BufferedWriter writer;

	private BufferedReader reader;

	private AtomicBoolean ready=new AtomicBoolean(false);

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

		if (reader == null)
			reader = new BufferedReader(new InputStreamReader(System.in));
		if (writer == null)
			writer = new BufferedWriter(new OutputStreamWriter(System.out));
		List<Sentence> sentences = new ArrayList<>();
		ready.set(true);
		logger.trace("Ready");
		for (String line = reader.readLine(); line != null; line = reader.readLine()) {
			logger.trace("Disambiguating "+line);
			Sentence sentence = new Sentence(line);
			if (sentence.getWords().size() > truncateMaxLength) {
				sentence.getWords().stream().skip(truncateMaxLength).collect(Collectors.toList())
						.forEach(sentence::removeWord);
			}
			logger.trace("Sentence truncated");
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
		writer.close();
		reader.close();
		neuralDisambiguator.close();
	}

	private void decodeSentenceBatch(List<Sentence> sentences) throws IOException {
		neuralDisambiguator.disambiguateDynamicSentenceBatch(sentences, "wsd", "");
		for (Sentence sentence : sentences) {
			if (mfsBackoff) {
				firstSenseDisambiguator.disambiguate(sentence, "wsd");
			}
			for (Word word : sentence.getWords()) {
				writer.write(word.getValue().replace("|", "/"));
				if (/* word.hasAnnotation("lemma") && word.hasAnnotation("pos") && */ word.hasAnnotation("wsd")) {
					writer.write("|" + word.getAnnotationValue("wsd"));
				}
				writer.write(" ");
			}
			writer.newLine();
		}
		writer.flush();
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

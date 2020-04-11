package it.cnr.istc.stlab.felg;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.jena.ext.com.google.common.collect.Lists;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import getalp.wsd.common.utils.ArgumentParser;
import getalp.wsd.common.wordnet.WordnetHelper;
import getalp.wsd.method.Disambiguator;
import getalp.wsd.method.FirstSenseDisambiguator;
import getalp.wsd.method.neural.NeuralDisambiguator;
import getalp.wsd.ufsac.core.Sentence;
import getalp.wsd.utils.WordnetUtils;

// NeuralWSDDecode adapted from https://github.com/getalp/disambiguate

public class NeuralWSDDecode {

	private static final Logger logger = LogManager.getLogger(NeuralWSDDecode.class);
	private String python_path, data_path;
	private List<String> weights;
	private boolean filterLemma;
	private boolean mfsBackoff;
	private Disambiguator firstSenseDisambiguator;
	private NeuralDisambiguator neuralDisambiguator;

	public static void main(String[] args) throws Exception {
		new NeuralWSDDecode().init(args);
	}

	public NeuralWSDDecode() throws Exception {
	}

	public NeuralWSDDecode(String python_path, String data_path, List<String> weights) throws Exception {
		this.python_path = python_path;
		this.data_path = data_path;
		this.weights = weights;
		init(new String[] {});
	}

	private void init(String[] args) throws Exception {

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

		logger.trace("POS Tagger initialized");
		firstSenseDisambiguator = new FirstSenseDisambiguator(WordnetHelper.wn30());
		logger.trace("First sense disambiguator initialized");
		neuralDisambiguator = new NeuralDisambiguator(pythonPath, dataPath, weights, clearText, batchSize);
		logger.trace("Neural disambiguator initialized");

		neuralDisambiguator.lowercaseWords = lowercase;
		neuralDisambiguator.filterLemma = filterLemma;
		neuralDisambiguator.reducedOutputVocabulary = senseCompressionClusters;

	}

	public void close() throws Exception {
		neuralDisambiguator.close();
	}

	public void disambiguate(Sentence sentence) throws IOException {
		neuralDisambiguator.disambiguateDynamicSentenceBatch(Lists.newArrayList(sentence), "wsd", "");
		if (mfsBackoff) {
			firstSenseDisambiguator.disambiguate(sentence, "wsd");
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

}

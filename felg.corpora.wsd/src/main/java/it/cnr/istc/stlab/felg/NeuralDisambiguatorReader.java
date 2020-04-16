package it.cnr.istc.stlab.felg;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import getalp.wsd.common.utils.POSConverter;
import getalp.wsd.common.utils.RegExp;
import getalp.wsd.common.wordnet.WordnetHelper;
import getalp.wsd.ufsac.core.Sentence;
import getalp.wsd.ufsac.core.Word;
import getalp.wsd.utils.Json;

public class NeuralDisambiguatorReader /* extends DisambiguatorContextSentenceBatch implements AutoCloseable */ {
	private static final String unknownToken = "<unk>";

	private WordnetHelper wn = WordnetHelper.wn30();

	private int inputFeatures;

	private List<String> inputAnnotationNames;

	private List<Boolean> inputClearText;

	private List<Map<String, Integer>> inputVocabulary;

	private int outputFeatures;

	private int senseFeatureIndex;

	private int outputTranslations;

	private List<String> outputAnnotationNames;

	private Map<String, Integer> reversedOutputAnnotationNames;

	private List<Map<String, Integer>> outputVocabulary;

	private List<Map<Integer, String>> reversedOutputVocabulary;

	private List<Map<Integer, String>> reversedOutputTranslationVocabulary;

	private boolean clearText;

	private boolean extraLemma = false;

	// --- begin public options

	public boolean lowercaseWords = true;

	public boolean filterLemma = true;

	public Map<String, String> reducedOutputVocabulary = null;

	// --- end public options

	public NeuralDisambiguatorReader(String pythonPath, String neuralPath, String weightsPath) {
		this(pythonPath, neuralPath, Collections.singletonList(weightsPath));
	}

	public NeuralDisambiguatorReader(String pythonPath, String neuralPath, String weightsPath, boolean clearText,
			int batchSize) {
		this(pythonPath, neuralPath, Collections.singletonList(weightsPath), clearText, batchSize);
	}

	public NeuralDisambiguatorReader(String pythonPath, String neuralPath, String weightsPath, boolean clearText,
			int batchSize, boolean extraLemma) {
		this(pythonPath, neuralPath, Collections.singletonList(weightsPath), clearText, batchSize, extraLemma);
	}

	public NeuralDisambiguatorReader(String pythonPath, String neuralPath, String weightsPath, boolean clearText,
			int batchSize, boolean translate, int beamSize) {
		this(pythonPath, neuralPath, Collections.singletonList(weightsPath), clearText, batchSize, translate, beamSize);
	}

	public NeuralDisambiguatorReader(String pythonPath, String neuralPath, String weightsPath, boolean clearText,
			int batchSize, boolean translate, int beamSize, boolean extraLemma) {
		this(pythonPath, neuralPath, Collections.singletonList(weightsPath), clearText, batchSize, translate, beamSize,
				extraLemma);
	}

	public NeuralDisambiguatorReader(String pythonPath, String neuralPath, List<String> weightsPath) {
		this(pythonPath, neuralPath, weightsPath, false, 1);
	}

	public NeuralDisambiguatorReader(String pythonPath, String neuralPath, List<String> weightsPaths, boolean clearText,
			int batchSize) {
		this(pythonPath, neuralPath, weightsPaths, clearText, batchSize, false, 1);
	}

	public NeuralDisambiguatorReader(String pythonPath, String neuralPath, List<String> weightsPaths, boolean clearText,
			int batchSize, boolean extraLemma) {
		this(pythonPath, neuralPath, weightsPaths, clearText, batchSize, false, 1, extraLemma);
	}

	public NeuralDisambiguatorReader(String pythonPath, String neuralPath, List<String> weightsPaths, boolean clearText,
			int batchSize, boolean translate, int beamSize) {
		this(pythonPath, neuralPath, weightsPaths, clearText, batchSize, translate, beamSize, false);
	}

	public NeuralDisambiguatorReader(String pythonPath, String neuralPath, List<String> weightsPaths, boolean clearText,
			int batchSize, boolean translate, int beamSize, boolean extraLemma) {
//		super(batchSize);
		try {
			this.clearText = clearText;
			this.extraLemma = extraLemma;
			readConfigFile(neuralPath);
			initInputVocabulary(neuralPath);
			initOutputVocabulary(neuralPath);
			initTranslationOutputVocabulary(neuralPath);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public int getInputFeatures() {
		return inputFeatures;
	}

	public List<String> getInputAnnotationNames() {
		return inputAnnotationNames;
	}

	@SuppressWarnings("unchecked")
	private void readConfigFile(String neuralPath) throws IOException {
		Map<Object, Object> config = Json.readMap(neuralPath + "/config.json");
		inputFeatures = (int) config.get("input_features");
		inputAnnotationNames = (List<String>) config.get("input_annotation_name");
		inputClearText = (List<Boolean>) config.get("input_clear_text");
		outputFeatures = (int) config.get("output_features");
		outputAnnotationNames = (List<String>) config.get("output_annotation_name");
		outputTranslations = (int) config.get("output_translation_features");
		senseFeatureIndex = 0;
	}

	private void initInputVocabulary(String neuralPath) throws Exception {
		inputVocabulary = new ArrayList<>();
		for (int i = 0; i < inputFeatures; i++) {
			inputVocabulary.add(initVocabulary(neuralPath + "/input_vocabulary" + i));
		}
	}

	private void initOutputVocabulary(String neuralPath) throws Exception {
		outputVocabulary = new ArrayList<>();
		reversedOutputVocabulary = new ArrayList<>();
		reversedOutputAnnotationNames = new HashMap<>();
		for (int i = 0; i < outputFeatures; i++) {
			Map<String, Integer> vocabulary = initVocabulary(neuralPath + "/output_vocabulary" + i);
			Map<Integer, String> reversedVocabulary = new HashMap<>();
			for (String key : vocabulary.keySet()) {
				reversedVocabulary.put(vocabulary.get(key), key);
			}
			outputVocabulary.add(vocabulary);
			reversedOutputVocabulary.add(reversedVocabulary);
			reversedOutputAnnotationNames.put(outputAnnotationNames.get(i), i);
		}
	}

	private void initTranslationOutputVocabulary(String neuralPath) throws Exception {
		reversedOutputTranslationVocabulary = new ArrayList<>();
		for (int i = 0; i < outputTranslations; i++) {
			Map<String, Integer> vocabulary = initVocabulary(neuralPath + "/output_translation" + i + "_vocabulary0");
			Map<Integer, String> reversedVocabulary = new HashMap<>();
			for (String key : vocabulary.keySet()) {
				reversedVocabulary.put(vocabulary.get(key), key);
			}
			reversedOutputTranslationVocabulary.add(reversedVocabulary);
		}
	}

	private Map<String, Integer> initVocabulary(String filePath) throws Exception {
		Map<String, Integer> ret = new HashMap<>();
		List<String> vocabAsList = new ArrayList<>();
		BufferedReader reader = Files.newBufferedReader(Paths.get(filePath));
		reader.lines().forEach(line -> {
			String[] linesplit = line.split(RegExp.anyWhiteSpaceGrouped.pattern());
			if (linesplit.length == 1)
				vocabAsList.add(linesplit[0]);
			else
				vocabAsList.add(linesplit[1]);
		});
		reader.close();
		for (int i = 0; i < vocabAsList.size(); i++) {
			ret.put(vocabAsList.get(i), i);
		}
		return ret;
	}

	private List<Sentence> readPredictOutput(List<Sentence> sentences, String senseTag, String confidenceTag,
			BufferedReader reader) throws Exception {
		List<Sentence> translations = new ArrayList<>();
		for (int i = 0; i < sentences.size(); i++) {
			Sentence sentence = sentences.get(i);
			if (outputFeatures > 0) {
				List<Word> words = sentence.getWords();
				String line = reader.readLine();
				if (line == null || line.startsWith("Better speed can be achieved with apex installed")) {
					i--;
					continue;
				}
				if (extraLemma) {
					int[][] output = parsePredictOutputExtraLemma(line);
					propagatePredictOutputExtraLemma(words, output, senseTag, confidenceTag);
				} else {
					int[] output = parsePredictOutput(line);
					propagatePredictOutput(words, output, senseTag, confidenceTag);
				}
			}
			// TODO: for (int i = 0 ; i < outputTranslations ; i++)
			if (outputTranslations > 0) {
				String line = reader.readLine();
				if (line.startsWith("Better speed can be achieved with apex installed")) {
					i--;
					continue;
				}
				if (line.isEmpty()) {
					line = "0";
				}
				String[] output = line.split(RegExp.anyWhiteSpaceGrouped.pattern());
				translations.add(processTranslationOutput(output));
			}
		}
		return translations;
	}

	private int[] parsePredictOutput(String line) {
		String[] lineSplit = line.split(RegExp.anyWhiteSpaceGrouped.pattern());
		int[] output = new int[lineSplit.length];
		for (int i = 0; i < lineSplit.length; i++) {
			output[i] = Integer.valueOf(lineSplit[i]);
		}
		return output;
	}

	private int[][] parsePredictOutputExtraLemma(String line) {
		String[] lineSplit = line.split(RegExp.anyWhiteSpaceGrouped.pattern());
		int[][] output = new int[lineSplit.length][];
		for (int i = 0; i < lineSplit.length; i++) {
			String[] wordSplit = lineSplit[i].split("/");
			output[i] = new int[wordSplit.length];
			for (int j = 0; j < wordSplit.length; j++) {
				output[i][j] = Integer.valueOf(wordSplit[j]);
			}
		}
		return output;
	}

	private void propagatePredictOutput(List<Word> words, int[] output, String senseTag, String confidenceTag) {
		for (int i = 0; i < output.length; i++) {
			Word word = words.get(i);
			if (word.hasAnnotation(senseTag))
				continue;
			if (filterLemma) {
				if (!word.hasAnnotation("lemma"))
					continue;
				if (!word.hasAnnotation("pos"))
					continue;
				int wordOutput = output[i];
				String pos = POSConverter.toWNPOS(word.getAnnotationValue("pos"));
				List<String> lemmas = word.getAnnotationValues("lemma", ";");
				for (String lemma : lemmas) {
					String wordKey = lemma + "%" + pos;
					if (!wn.isWordKeyExists(wordKey))
						continue;
					List<String> lemmaSenseKeys = wn.getSenseKeyListFromWordKey(wordKey);
					for (String possibleSenseKey : lemmaSenseKeys) {
						String possibleSynsetKey = wn.getSynsetKeyFromSenseKey(possibleSenseKey);
						if (reducedOutputVocabulary != null) {
							possibleSynsetKey = reducedOutputVocabulary.getOrDefault(possibleSynsetKey,
									possibleSynsetKey);
						}
						if (reversedOutputVocabulary.get(senseFeatureIndex).get(wordOutput).equals(possibleSynsetKey)) {
							word.setAnnotation(senseTag, possibleSenseKey);
							// word.setAnnotation(confidenceTag, confidenceInfo);
						}
					}
				}
			} else {
				int wordOutput = output[i];
				String possibleSynsetKey = reversedOutputVocabulary.get(senseFeatureIndex).get(wordOutput);
				String possibleSenseKey = wn.getSenseKeyListFromSynsetKey(possibleSynsetKey).get(0);
				word.setAnnotation(senseTag, possibleSenseKey);
			}
		}
	}

	private void propagatePredictOutputExtraLemma(List<Word> words, int[][] output, String senseTag,
			String confidenceTag) {
		for (int i = 0; i < output.length; i++) {
			Word word = words.get(i);
			if (word.hasAnnotation(senseTag))
				continue;
			if (!word.hasAnnotation("lemma"))
				continue;
			if (!word.hasAnnotation("pos"))
				continue;
			int[] wordOutput = output[i];
			String pos = POSConverter.toWNPOS(word.getAnnotationValue("pos"));
			List<String> lemmas = word.getAnnotationValues("lemma", ";");
			// TODO: multiple lemmas
			String lemma = lemmas.get(0);
			String wordKey = lemma + "%" + pos;
			if (!wn.isWordKeyExists(wordKey))
				continue;
			if (!reversedOutputAnnotationNames.containsKey(wordKey))
				continue;
			int extraLemmaFeatureIndex = reversedOutputAnnotationNames.get(wordKey);
			List<String> lemmaSenseKeys = wn.getSenseKeyListFromWordKey(wordKey);
			for (String possibleSenseKey : lemmaSenseKeys) {
				String possibleSynsetKey = wn.getSynsetKeyFromSenseKey(possibleSenseKey);
				if (reducedOutputVocabulary != null) {
					possibleSynsetKey = reducedOutputVocabulary.getOrDefault(possibleSynsetKey, possibleSynsetKey);
				}
				if (reversedOutputVocabulary.get(extraLemmaFeatureIndex).get(wordOutput[extraLemmaFeatureIndex])
						.equals(possibleSynsetKey)) {
					word.setAnnotation(senseTag, possibleSenseKey);
					// word.setAnnotation(confidenceTag, confidenceInfo);
				}
			}
		}
	}

	private Sentence processTranslationOutput(String[] output) {
		Sentence translation = new Sentence();
		for (String wordValue : output) {
			new Word(wordValue, translation);
		}
		return translation;
	}

	public void readDisambiguationResultFromBufferedReader(List<Sentence> sentences, String senseTag,
			String confidenceTag, BufferedReader br) throws Exception {
		readPredictOutput(sentences, senseTag, confidenceTag, br);
	}

}

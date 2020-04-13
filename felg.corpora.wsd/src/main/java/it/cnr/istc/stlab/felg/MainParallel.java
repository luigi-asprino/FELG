package it.cnr.istc.stlab.felg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import getalp.wsd.ufsac.utils.CorpusLemmatizer;
import it.cnr.istc.stlab.lgu.commons.files.FileUtils;

public class MainParallel {

	private static final Logger logger = LogManager.getLogger(MainParallel.class);

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

			String outputFolder = config.getString("outputFolder");
			String python_path = config.getString("python_path");
			String data_path = config.getString("data_path");
			int concurent_threads = config.getInt("concurrent_threads");
			boolean useOnlyAbstract = config.getBoolean("useOnlyAbstract");
			boolean excludeWrite = config.getBoolean("excludeWrite");
			List<String> weights = new ArrayList<>();
			weights.add(config.getString("weights"));

			long t0 = System.currentTimeMillis();
			AtomicLong count = new AtomicLong();
			
			// initialize wsd
			NeuralWSDDecode nwd = new NeuralWSDDecode(python_path, data_path, weights);
			logger.info("WSD initialized");

			// splitting input
			List<String> filepaths = FileUtils.getFilesUnderTreeRec(config.getString("wikiFolder"));
			List<List<String>> listsToProcess = new ArrayList<>();
			int listSize = filepaths.size() / concurent_threads;
			for (int i = 0; i < concurent_threads; i++) {
				if ((i + 1) * listSize > filepaths.size()) {
					logger.trace(String.format("From %s to %s", i * listSize, filepaths.size()));
					listsToProcess.add(filepaths.subList(i * listSize, filepaths.size()));
				} else {
					logger.trace(String.format("From %s to %s", i * listSize, (i + 1) * listSize));
					listsToProcess.add(filepaths.subList(i * listSize, (i + 1) * listSize));
				}
			}

			ExecutorService executor = Executors.newFixedThreadPool(concurent_threads);
			for (int i = 0; i < concurent_threads; i++) {
				executor.execute(new WSDWorker(listsToProcess.get(i), nwd, outputFolder,
						count, pipeline, lemmatizer, t0,useOnlyAbstract,excludeWrite));
			}
			executor.awaitTermination(10, TimeUnit.DAYS);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}

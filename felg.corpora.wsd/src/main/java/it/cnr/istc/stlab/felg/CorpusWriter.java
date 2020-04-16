package it.cnr.istc.stlab.felg;

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
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import getalp.wsd.ufsac.utils.CorpusLemmatizer;
import it.cnr.istc.stlab.lgu.commons.files.FileUtils;

public class CorpusWriter {

	private static final Logger logger = LogManager.getLogger(CorpusWriter.class);

	public static void main(String[] args) throws CompressorException, IOException {
		logger.info("Running FELG - Corpus Writer");

		try {
			Configurations configs = new Configurations();
			Configuration config;
			if (args.length > 0) {
				config = configs.properties(args[0]);
			} else {
				config = configs.properties("config.properties");
			}

			Properties props = new Properties();
			props.setProperty("annotators", "tokenize, ssplit, pos");
			StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

			CorpusLemmatizer lemmatizer = new CorpusLemmatizer();

			String outputFolder = config.getString("outputFolder");
			String python_path = config.getString("python_path");
			String data_path = config.getString("data_path");
			int concurent_threads = config.getInt("concurrent_threads");
			boolean useOnlyAbstract = config.getBoolean("useOnlyAbstract");
			boolean useCompression = config.getBoolean("useCompression");

			logger.info(ConfigurationUtils.toString(config));

			AtomicLong count = new AtomicLong();

			// initialize wsd
			NeuralDisambiguatorWriter[] nwds = new NeuralDisambiguatorWriter[concurent_threads];
			for (int i = 0; i < concurent_threads; i++) {
				nwds[i] = new NeuralDisambiguatorWriter(python_path, data_path,
						Lists.newArrayList(config.getString("weights")));
				logger.info("Writer initialized");
			}

			// splitting input
			List<String> filepaths = FileUtils.getFilesUnderTreeRec(config.getString("wikiFolder"));
			List<List<String>> listsToProcess = new ArrayList<>();
			int chunkSize = filepaths.size() / concurent_threads;
			for (int i = 0; i < filepaths.size(); i += chunkSize) {
				listsToProcess.add(filepaths.subList(i, Math.min(i + chunkSize, filepaths.size())));
				logger.trace(String.format("from %s to %s", i, Math.min(i + chunkSize, filepaths.size())));
			}

			ExecutorService executor = Executors.newFixedThreadPool(concurent_threads);
			long t0 = System.currentTimeMillis();
			for (int i = 0; i < concurent_threads; i++) {
				executor.execute(new WorkerWriter(listsToProcess.get(i), nwds[i], outputFolder, count, pipeline,
						lemmatizer, t0, useOnlyAbstract, useCompression));
			}
			executor.shutdown();
			executor.awaitTermination(10, TimeUnit.DAYS);
			logger.info("Number of articles processed " + count.get());
			executor.shutdownNow();

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}

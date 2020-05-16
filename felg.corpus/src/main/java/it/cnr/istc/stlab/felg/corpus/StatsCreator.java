package it.cnr.istc.stlab.felg.corpus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.jsoniter.JsonIterator;

import it.cnr.istc.stlab.felg.frameannotation.Utils;
import it.cnr.istc.stlab.felg.frameannotation.model.AnnotatedArticle;

public class StatsCreator {

	private static Logger logger = LogManager.getLogger(StatsCreator.class);

	public static void main(String[] args) {
		try {
			Configurations configs = new Configurations();
			Configuration config = configs.properties(args[0]);

			logger.info(ConfigurationUtils.toString(config));

			String wikiFolderPath = config.getString("wikiFolder");
			String statFilePath = config.getString("statFile");

			FileOutputStream fos = new FileOutputStream(new File(statFilePath));

			Map<String, AtomicLong> statMap = new HashMap<>();

			final long numberOfFiles = Files.walk(Paths.get(wikiFolderPath)).filter(Files::isRegularFile)
					.filter(f -> FilenameUtils.isExtension(f.getFileName().toString(), "bz2")).count();
			AtomicLong numberOfProcessedPages = new AtomicLong(0);
			AtomicLong numberOfSentences = new AtomicLong(0);
			AtomicLong numberOfSentencesContainingFrames = new AtomicLong(0);

			final long t0 = System.currentTimeMillis();

			Files.walk(Paths.get(wikiFolderPath)).filter(Files::isRegularFile)
					.filter(f -> FilenameUtils.isExtension(f.getFileName().toString(), "bz2")).parallel().forEach(f -> {
						try {
							AnnotatedArticle a = JsonIterator.deserialize(Utils.readBZ2File(f.toString()),
									AnnotatedArticle.class);
							a.getSentences().forEach(sentence -> {
								AtomicBoolean hasFrame = new AtomicBoolean();
								numberOfSentences.incrementAndGet();
								sentence.getAnnotations().forEach(annotation -> {
									if (annotation.getFrames() != null && !annotation.getFrames().isEmpty()) {
										hasFrame.set(true);
									}

									if (annotation.getFrames() != null) {
										annotation.getFrames().forEach(frame -> {
											AtomicLong n = statMap.get(frame);
											if (n == null) {
												synchronized (statMap) {
													if (!statMap.containsKey(frame)) {
														statMap.put(frame, new AtomicLong(1));
													} else {
														statMap.get(frame).incrementAndGet();
													}
												}
											} else {
												n.incrementAndGet();
											}
										});
									}
								});

								if (hasFrame.get()) {
									numberOfSentencesContainingFrames.incrementAndGet();
								}
							});
							if (numberOfProcessedPages.get() % 1000 == 0) {
								long t1 = System.currentTimeMillis();
								long elapsed = t1 - t0;
								int msPerArticle = (int) ((double) elapsed / (double) numberOfProcessedPages.get());
								long articles_to_process = numberOfFiles - numberOfProcessedPages.longValue();
								long finish = t1 + ((long) (articles_to_process * msPerArticle));
								logger.info(numberOfProcessedPages.get() + "/" + numberOfFiles + " ::: " + msPerArticle
										+ " ms " + new Date(finish).toString());
							}
							numberOfProcessedPages.incrementAndGet();

						} catch (CompressorException | IOException e) {
							e.printStackTrace();
						}
					});

			fos.write(("Number of Processed Pages: " + numberOfProcessedPages.get() + "\n").getBytes());
			fos.write(("Number of Sentences: " + numberOfSentences.get() + "\n").getBytes());
			fos.write(("Number of Sentences containing frames: " + numberOfSentencesContainingFrames.get() + "\n\n")
					.getBytes());

			fos.write(("Frame\tFrequency\n").getBytes());

			statMap.forEach((frame, frequency) -> {
				try {
					fos.write(String.format("%s\t%s\n", frame, frequency.get()).getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				}
			});

			fos.flush();
			fos.close();

		} catch (ConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

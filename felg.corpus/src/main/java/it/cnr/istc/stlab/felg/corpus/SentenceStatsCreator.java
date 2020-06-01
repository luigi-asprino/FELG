package it.cnr.istc.stlab.felg.corpus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
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

public class SentenceStatsCreator {
	private static Logger logger = LogManager.getLogger(StatsCreator.class);

	public static void main(String[] args) {
		try {
			Configurations configs = new Configurations();
			Configuration config = configs.properties(args[0]);

			logger.info(ConfigurationUtils.toString(config));

			String wikiFolderPath = config.getString("wikiFolder");
			String statFilePath = config.getString("statSentenceFile");
			String setFilePath = config.getString("setFilePath");

			OutputStream fos = new CompressorStreamFactory().createCompressorOutputStream(CompressorStreamFactory.BZIP2,
					new FileOutputStream(new File(statFilePath)));
			OutputStream fos_set = new CompressorStreamFactory().createCompressorOutputStream(
					CompressorStreamFactory.BZIP2, new FileOutputStream(new File(setFilePath)));

			logger.info("Counting files");
			final long numberOfFiles = Files.walk(Paths.get(wikiFolderPath)).filter(Files::isRegularFile)
					.filter(f -> FilenameUtils.isExtension(f.getFileName().toString(), "bz2")).count();
			AtomicLong numberOfProcessedPages = new AtomicLong(0);
			logger.info("Number of files " + numberOfFiles);

			final long t0 = System.currentTimeMillis();

			logger.info("Start processing");
			Files.walk(Paths.get(wikiFolderPath)).filter(Files::isRegularFile)
					.filter(f -> FilenameUtils.isExtension(f.getFileName().toString(), "bz2")).parallel().forEach(f -> {
						try {
							AnnotatedArticle a = JsonIterator.deserialize(Utils.readBZ2File(f.toString()),
									AnnotatedArticle.class);
							for (int i = 0; i < a.getSentences().size(); i++) {
								Set<String> frames = new HashSet<>();
								a.getSentences().get(i).getAnnotations().forEach(annotation -> {
									if (annotation.getFrames() != null) {
										frames.addAll(annotation.getFrames());
									}
								});

								if (!frames.isEmpty()) {
									List<String> framesOrdered = new ArrayList<>();
									framesOrdered.addAll(frames);
									Collections.sort(framesOrdered);

									try {
										String frameString = String.join("", framesOrdered);
										String md5D = DigestUtils.md5Hex(frameString);
										StringBuilder toWrite = new StringBuilder(1024);
										toWrite.append(f.toString());
										toWrite.append('\t');
										toWrite.append(i);
										toWrite.append('\t');
										toWrite.append(md5D);
										toWrite.append('\n');
										fos.write(toWrite.toString().getBytes());
										fos.flush();

										StringBuilder toWriteSet = new StringBuilder(1024);
										toWriteSet.append(md5D);
										toWriteSet.append('\t');
										toWriteSet.append(frameString);
										toWriteSet.append('\n');
										fos_set.write(toWriteSet.toString().getBytes());
										fos_set.flush();
									} catch (Exception e) {
										logger.error(e.getMessage());
										e.printStackTrace();
									}
								}
							}

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

			fos.flush();
			fos.close();
			fos_set.flush();
			fos_set.close();

		} catch (ConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (CompressorException e1) {
			e1.printStackTrace();
		}
	}

}

package it.cnr.istc.stlab.felg;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
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
import it.cnr.istc.stlab.felg.frameannotation.WikiUtils;
import it.cnr.istc.stlab.felg.frameannotation.model.AnnotatedArticle;

public class Filter {

	private static Logger logger = LogManager.getLogger(Filter.class);

	public static void main(String[] args) {
		try {
			Configurations configs = new Configurations();
			Configuration config = configs.properties(args[0]);

			logger.info(ConfigurationUtils.toString(config));

			String wikiFolderPath = config.getString("wikiFolder");
			String wikiFolderOut = config.getString("wikiFolderOut");

			File fileFolderOut = new File(wikiFolderOut);
			fileFolderOut.mkdirs();

			final long numberOfFiles = Files.walk(Paths.get(wikiFolderPath)).filter(Files::isRegularFile)
					.filter(f -> FilenameUtils.isExtension(f.getFileName().toString(), "bz2")).count();
			AtomicLong al = new AtomicLong(0);
			final long t0 = System.currentTimeMillis();
			AtomicLong disambiguationPages = new AtomicLong(0);
			AtomicLong listPages = new AtomicLong(0);

			Files.walk(Paths.get(wikiFolderPath)).filter(Files::isRegularFile)
					.filter(f -> FilenameUtils.isExtension(f.getFileName().toString(), "bz2")).parallel().forEach(f -> {
						try {
							AnnotatedArticle a = JsonIterator.deserialize(Utils.readBZ2File(f.toString()),
									AnnotatedArticle.class);
							String folderOut = wikiFolderOut + "/" + f.getParent().getParent().getFileName() + "/"
									+ f.getParent().getFileName();
							String fileOut = folderOut + "/" + FilenameUtils.getName(f.getFileName().toString());
							File folderOutFile = new File(folderOut);
							if (!folderOutFile.exists()) {
								folderOutFile.mkdirs();
							}

							if (WikiUtils.isList(a)) {
								logger.trace("List ::::: " + a.getTitle() + " " + a.getUrl() + " "
										+ a.getSentences().size());
								listPages.incrementAndGet();
							} else if (WikiUtils.isDisambiguation(a)) {
								logger.trace("Disambiguation page ::::: " + a.getTitle() + " " + a.getUrl() + " "
										+ a.getSentences().size());
								disambiguationPages.incrementAndGet();
							} else {
								Files.copy(f, Paths.get(fileOut), StandardCopyOption.REPLACE_EXISTING);
							}

							if (al.incrementAndGet() % 1000 == 0) {
								long t1 = System.currentTimeMillis();
								long elapsed = t1 - t0;
								int msPerArticle = (int) ((double) elapsed / (double) al.get());
								long articles_to_process = numberOfFiles - al.longValue();
								long finish = t1 + ((long) (articles_to_process * msPerArticle));
								logger.info(al.get() + "/" + numberOfFiles + " ::: " + msPerArticle + " ms "
										+ new Date(finish).toString());
							}

						} catch (CompressorException | IOException e) {
							e.printStackTrace();
						}

					});

			System.out.println(String.format("Filter out: %s disambiguation pages, %s list pages",
					disambiguationPages.get(), listPages.get(), numberOfFiles));

		} catch (ConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

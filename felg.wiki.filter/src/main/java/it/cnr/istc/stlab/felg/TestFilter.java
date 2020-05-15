package it.cnr.istc.stlab.felg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.Map;
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
import com.jsoniter.output.JsonStream;

import it.cnr.istc.stlab.felg.frameannotation.Utils;
import it.cnr.istc.stlab.felg.frameannotation.WikiUtils;
import it.cnr.istc.stlab.felg.frameannotation.model.AnnotatedArticle;
import it.cnr.istc.stlab.felg.frameannotation.model.AnnotatedMultiWord;
import it.cnr.istc.stlab.felg.frameannotation.model.AnnotatedSentence;

public class TestFilter {

	private static Logger logger = LogManager.getLogger(TestFilter.class);
	private static FileOutputStream fos_frames;
	private static FileOutputStream fos_senses, fos_monosemous;

	public static void main(String[] args) {
		try {
			fos_frames = new FileOutputStream(new File("/Users/lgu/Desktop/withotu_frames.txt"));
			fos_senses = new FileOutputStream(new File("/Users/lgu/Desktop/withotu_senses.txt"));
			fos_monosemous = new FileOutputStream(new File("/Users/lgu/Desktop/mono.txt"));
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
			AtomicLong withFramesInAllSentences = new AtomicLong(0);
			AtomicLong withSenseInAllSentences = new AtomicLong(0);
			AtomicLong hasMonosemous = new AtomicLong(0);
			AtomicLong hasMonosemousWord = new AtomicLong(0);

			Map<String, String> monosemousWords = MosemousUtils.getMonosemousVerbs(
					"/Users/lgu/Dropbox/repository/workspace/FELG/felg.wiki.filter/src/main/resources/bnMonosemousVerbs.tsv");

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

							if (hasOneFramePerSentence(a)) {
								withFramesInAllSentences.incrementAndGet();
							} else {
								logger.trace("Not having a frame per sentence ::::: " + a.getTitle() + " " + a.getUrl()
										+ " " + a.getSentences().size());
							}

							if (hasOneSensePerSentence(a)) {
								withSenseInAllSentences.incrementAndGet();
							} else {
								logger.trace("Not having a sense per sentence ::::: " + a.getTitle() + " " + a.getUrl()
										+ " " + a.getSentences().size());
							}

							if (hasMonosemousAnnotated(a, monosemousWords)) {
								logger.trace("Has a monosemous ::::: " + a.getTitle() + " " + a.getUrl() + " "
										+ a.getSentences().size());
								hasMonosemous.incrementAndGet();
							}

							if (hasMonosemousWord(a, monosemousWords)) {
								hasMonosemousWord.incrementAndGet();
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

			System.out.println(String.format(
					"Filter out: %s disambiguation pages, %s list pages\n %s having at list one frame per sentence\n%s having at list one frame per sentence\n%s articles have monosemousa annotated\n%s have monosemous word\nNumber of analysed articles %s",
					disambiguationPages.get(), listPages.get(), withFramesInAllSentences.get(),
					withSenseInAllSentences.get(), hasMonosemous.get(), hasMonosemousWord.get(), numberOfFiles));

		} catch (ConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static boolean hasOneFramePerSentence(AnnotatedArticle aa) {
		for (AnnotatedSentence as : aa.getSentences()) {
			boolean hasFrame = false;
			for (AnnotatedMultiWord amw : as.getAnnotations()) {
				if (amw.getFrames() != null && !amw.getFrames().isEmpty()) {
					hasFrame = true;
					break;
				}
			}
			if (!hasFrame) {
				if (fos_frames != null) {
					try {
						fos_frames.write((as.getSentence() + "\n").getBytes());
						fos_frames.write((JsonStream.serialize(as) + "\n").getBytes());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				return false;
			}
		}

		return true;
	}

	private static boolean hasOneSensePerSentence(AnnotatedArticle aa) {
		for (AnnotatedSentence as : aa.getSentences()) {
			boolean hasSense = false;
			for (AnnotatedMultiWord amw : as.getAnnotations()) {
				if (amw.getBnSynset() != null && amw.getBnSynset().length() > 0) {
					hasSense = true;

					break;
				}
			}
			if (!hasSense) {
				if (fos_senses != null) {
					try {
						fos_senses.write((as.getSentence() + "\n").getBytes());
						fos_senses.write((JsonStream.serialize(as) + "\n").getBytes());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				return false;
			}
		}

		return true;
	}

	private static boolean hasMonosemousAnnotated(AnnotatedArticle aa, Map<String, String> monosemous) {
		for (AnnotatedSentence as : aa.getSentences()) {
			for (AnnotatedMultiWord amw : as.getAnnotations()) {
				if (amw.getBnSynset() != null && amw.getBnSynset().length() > 0
						&& monosemous.containsKey(amw.getMention())
						&& amw.getBnSynset().equals(monosemous.get(amw.getMention()))) {
					if (fos_monosemous != null) {
						try {
							fos_monosemous.write((as.getSentence().replaceAll("\n", " ") + " " + amw.getBnSynset() + " "
									+ amw.getMention() + "\n").getBytes());
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasMonosemousWord(AnnotatedArticle aa, Map<String, String> monosemous) {
		for (AnnotatedSentence as : aa.getSentences()) {
			for (AnnotatedMultiWord amw : as.getAnnotations()) {
				if (amw.getBnSynset() != null && amw.getBnSynset().length() > 0
						&& monosemous.containsKey(amw.getMention()) && amw.getPos() != null
						&& amw.getPos().startsWith("V")) {
					if (fos_monosemous != null) {
						try {
							fos_monosemous.write(("\t" + as.getSentence().replaceAll("\n", " ") + " "
									+ amw.getBnSynset() + " " + amw.getMention() + "\n").getBytes());
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					return true;
				}
			}
		}
		return false;
	}

}

package it.cnr.istc.stlab.felg.frameannotation;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.jsoniter.JsonIterator;
import com.jsoniter.output.JsonStream;

import it.cnr.istc.stlab.felg.frameannotation.model.AnnotatedArticle;

public class FELGFrameAnnotation {

	private static Logger logger = LoggerFactory.getLogger(FELGFrameAnnotation.class);

	private static final int BUFFER_SIZE = 1024;

	public static void main(String[] args) {
		try {
			Configurations configs = new Configurations();
			Configuration config = configs.properties("config.properties");

			logger.info(ConfigurationUtils.toString(config));

			String wikiFolderPath = config.getString("wikiFolder");
			String fn2syn = config.getString("fn2syn");
			String fsyn2syn = config.getString("fsyn2syn");
			String wikiFolderOut = config.getString("wikiFolderOut");

			File fileFolderOut = new File(wikiFolderOut);
			fileFolderOut.mkdirs();

			final ImmutableMap<String, List<String>> syn2Frames = getBNSynsetToFrameMap(fn2syn);
			final ImmutableMap<String, List<String>> syn2Fsyn = getBNSynsetToFsynMap(fsyn2syn);
			Random random = new Random(System.currentTimeMillis());

			final long numberOfFiles = Files.walk(Paths.get(wikiFolderPath)).filter(Files::isRegularFile)
					.filter(f -> FilenameUtils.isExtension(f.getFileName().toString(), "bz2")).count();
			AtomicLong al = new AtomicLong(0);
			final long t0 = System.currentTimeMillis();

			Files.walk(Paths.get(wikiFolderPath)).filter(Files::isRegularFile)
					.filter(f -> FilenameUtils.isExtension(f.getFileName().toString(), "bz2")).parallel().forEach(f -> {
						try {
							AnnotatedArticle a = JsonIterator.deserialize(readBZ2File(f.toString()),
									AnnotatedArticle.class);
							String folderOut = wikiFolderOut + "/" + f.getParent().getParent().getFileName() + "/"
									+ f.getParent().getFileName();
							File folderOutFile = new File(folderOut);
							if (!folderOutFile.exists()) {
								folderOutFile.mkdirs();
							}
							a.getSentences().forEach(sentence -> {
								sentence.getAnnotations().forEach(annotation -> {
									String bn = annotation.getBnSynset();
									if (bn != null) {
										if (syn2Frames.containsKey(bn)) {
											annotation.setFrame(
													syn2Frames.get(bn).get(random.nextInt(syn2Frames.get(bn).size())));
											annotation.setFrames(syn2Frames.get(bn));
										}
										if (syn2Fsyn.containsKey(bn)) {
											annotation.setFsyn(
													syn2Fsyn.get(bn).get(random.nextInt(syn2Fsyn.get(bn).size())));
											annotation.setFsyns(syn2Fsyn.get(bn));
										}
									}
								});
							});
							String fileOut = folderOut + "/" + FilenameUtils.getName(f.getFileName().toString());
							OutputStream os = new CompressorStreamFactory().createCompressorOutputStream(
									CompressorStreamFactory.BZIP2, new FileOutputStream(new File(fileOut)));
							JsonStream.serialize(a, os);
							al.incrementAndGet();
							if (al.get() % 100 == 0) {
								long t1 = System.currentTimeMillis();
								long elapsed = t1 - t0;
								int msPerArticle = (int) ((double) elapsed / (double) al.get());
								logger.info(al.get() + "/" + numberOfFiles + " ::: " + msPerArticle + " ms");
							}

						} catch (CompressorException | IOException e) {
							e.printStackTrace();
						}

					});

		} catch (ConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String readBZ2File(String file) throws CompressorException, IOException {
		FileInputStream fin = new FileInputStream(file);
		InputStream is = new CompressorStreamFactory()
				.createCompressorInputStream(new BufferedInputStream(fin, BUFFER_SIZE));
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = br.readLine()) != null) {
			sb.append(line);
		}

		return sb.toString();
	}

	private static ImmutableMap<String, List<String>> getBNSynsetToFrameMap(String fn2syn) {
		ImmutableMap.Builder<String, List<String>> syn2Frames = new ImmutableMap.Builder<String, List<String>>();
		Model m = ModelFactory.createDefaultModel();
		RDFDataMgr.read(m, fn2syn);
		String queryString = "SELECT ?syn (GROUP_CONCAT( ?f; separator=\" \")AS ?frames)  WHERE { "
				+ " ?f <http://www.w3.org/2004/02/skos/core#closeMatch> ?syn . } GROUP BY ?syn ";
		QueryExecution qexec = QueryExecutionFactory.create(QueryFactory.create(queryString), m);
		ResultSet rs = qexec.execSelect();
		Map<Integer, Integer> frameDistribution = new HashMap<>();
		while (rs.hasNext()) {
			QuerySolution querySolution = (QuerySolution) rs.next();
			String syn = querySolution.getResource("syn").getURI();
			String frameGroup = querySolution.getLiteral("frames").toString();
			String[] frames = toNamespaceF(frameGroup.split(" "));
			if (frameDistribution.containsKey(frames.length)) {
				frameDistribution.put(frames.length, frameDistribution.get(frames.length) + 1);
			} else {
				frameDistribution.put(frames.length, 1);
			}
			syn2Frames.put(syn, Lists.newArrayList(toNamespaceF(frameGroup.split(" "))));
		}
		System.out.println("Syn2Frame distribution");
		frameDistribution.forEach((k, v) -> {
			System.out.println(k + "\t" + v);
		});
		return syn2Frames.build();
	}

	private static ImmutableMap<String, List<String>> getBNSynsetToFsynMap(String fn2syn) {
		ImmutableMap.Builder<String, List<String>> syn2Frames = new ImmutableMap.Builder<String, List<String>>();
		Model m = ModelFactory.createDefaultModel();
		RDFDataMgr.read(m, fn2syn);
		String queryString = "SELECT ?syn (GROUP_CONCAT( ?f; separator=\" \")AS ?frames)  WHERE { "
				+ " ?f <https://w3id.org/framester/schema/unaryProjection> ?syn . } GROUP BY ?syn ";
		QueryExecution qexec = QueryExecutionFactory.create(QueryFactory.create(queryString), m);
		ResultSet rs = qexec.execSelect();
		Map<Integer, Integer> frameDistribution = new HashMap<>();
		while (rs.hasNext()) {
			QuerySolution querySolution = (QuerySolution) rs.next();
			String syn = querySolution.getResource("syn").getURI();
			String frameGroup = querySolution.getLiteral("frames").toString();
			String[] frames = toNamespaceF(frameGroup.split(" "));
			if (frameDistribution.containsKey(frames.length)) {
				frameDistribution.put(frames.length, frameDistribution.get(frames.length) + 1);
			} else {
				frameDistribution.put(frames.length, 1);
			}

			syn2Frames.put(syn, Lists.newArrayList(toNamespaceFsyn(frameGroup.split(" "))));
		}
		frameDistribution.forEach((k, v) -> {
			System.out.println(k + "\t" + v);
		});
		return syn2Frames.build();
	}

//	private static String toNamespaceBN(String uri) {
//		return uri.replace("http://babelnet.org/rdf/", "bn:");
//	}

	private static String toNamespaceF(String uri) {
		return uri.replace("https://w3id.org/framester/framenet/abox/frame/", "f:");
	}

	private static String toNamespaceFsyn(String uri) {
		return uri.replace("https://w3id.org/framester/data/framestersyn/", "fs:");
	}

	private static String[] toNamespaceF(String[] uris) {
		String[] result = new String[uris.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = toNamespaceF(uris[i]);
		}
		return result;
	}

	private static String[] toNamespaceFsyn(String[] uris) {
		String[] result = new String[uris.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = toNamespaceFsyn(uris[i]);
		}
		return result;
	}

}

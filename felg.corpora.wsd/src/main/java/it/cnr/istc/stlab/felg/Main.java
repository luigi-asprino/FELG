package it.cnr.istc.stlab.felg;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import it.cnr.istc.stlab.felg.model.AnnotatedWord;
import it.cnr.istc.stlab.lgu.commons.files.FileUtils;

public class Main {

	private static final Logger logger = LogManager.getLogger(Main.class);

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

			String outputFolder = config.getString("outputFolder");
			String python_path = config.getString("python_path");
			String data_path = config.getString("data_path");
			List<String> weights = new ArrayList<>();
			weights.add(config.getString("weights"));

			// set in/out
			PipedOutputStream textOutputStream = new PipedOutputStream();
			PipedInputStream wsdInputStream = new PipedInputStream(textOutputStream);

			PipedInputStream annotatedTextInputStream = new PipedInputStream();
			PrintStream wsdOutputStream = new PrintStream(new PipedOutputStream(annotatedTextInputStream));

//			System.setIn(wsdInputStream);
//			System.setOut(wsdOutputStream);

//			BufferedReader br = new BufferedReader(new InputStreamReader(annotatedTextInputStream));

			// initialize WSD
			WSDRunnable r = new WSDRunnable(python_path, data_path, weights,
					new BufferedWriter(new OutputStreamWriter(wsdOutputStream)),
					new BufferedReader(new InputStreamReader(wsdInputStream)));
			new Thread(r).start();


			// wait until the wsd is initialized
			while (!r.isReady()) {
				Thread.sleep(1000);
			}

			logger.info("WSD initialized");

			List<String> filepaths = FileUtils.getFilesUnderTreeRec(config.getString("wikiFolder"));
			BlockingQueue<AnnotatedWord> aws = r.getOutChannel();
			for (String filepath : filepaths) {
				logger.trace("Processing " + filepath);
				if (!FilenameUtils.getExtension(filepath).equals("bz2")) {
					continue;
				}
				ArchiveReader ar = new ArchiveReader(filepath);
				ArticleReader aar;
				while ((aar = ar.nextArticle()) != null) {
					logger.trace("Processing " + aar.getTitle());
					textOutputStream.write(aar.getAbstract(true).getBytes());
					FileOutputStream fos = new FileOutputStream(new File(outputFolder + "/" + aar.getTitle()));

					AnnotatedWord aw;
					boolean stop = false;

					while (!stop) {
						aw = aws.take();
						fos.write((aw.getWord()+"|"+aw.getSenseKey()).getBytes());
						stop = aw.isLast();
					}

					fos.close();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	static class WSDRunnable implements Runnable {

		private NeuralWSDDecode nwd;
		private String python_path;
		private String data_path;
		private List<String> weights;
		private BufferedWriter writer;
		private BufferedReader reader;

		public WSDRunnable(String python_path, String data_path, List<String> weights, BufferedWriter writer,
				BufferedReader reader) {
			super();
			this.python_path = python_path;
			this.data_path = data_path;
			this.weights = weights;
			this.writer = writer;
			this.reader = reader;
		}

		@Override
		public void run() {
			try {
				this.nwd = new NeuralWSDDecode(python_path, data_path, weights, writer, reader);
				this.nwd.decode();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public boolean isReady() {
			return nwd != null && nwd.isReady();
		}

		public BlockingQueue<AnnotatedWord> getOutChannel() {
			return nwd.getOutChannel();
		}
	}
}

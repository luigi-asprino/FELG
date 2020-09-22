package it.cnr.istc.stlab.felg.corpus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ConfigurationUtils;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.opencsv.CSVWriter;

import it.cnr.istc.stlab.lgu.commons.io.FileUtils;
import it.cnr.istc.stlab.lgu.commons.lang.BitMask;

public class Analysis {

	private static Logger logger = LogManager.getLogger(Analysis.class);

	public static void main(String[] args) throws FileNotFoundException {
		try {
			Configurations configs = new Configurations();
			Configuration config = configs.properties(args[0]);

//			HashSet<String> uniqueFrameSetsBitmasks = new HashSet<>();
			HashSet<Integer> uniqueFrameSets = new HashSet<>();

			logger.info(ConfigurationUtils.toString(config));

			String setFilePath = config.getString("setFilePath");
			String frameListPath = config.getString("frameListPath");
			String uniqueFramesetsPath = config.getString("uniqueFramesetsPath");
			String uniqueFramesetsPathCSV = config.getString("uniqueFramesetsPathCSV");

			NumberFormat nf = NumberFormat.getInstance();

			Map<String, long[]> framesToBitMask = frameSetToBitMask(frameListPath);

			BufferedWriter writer = Files.newBufferedWriter(Paths.get(uniqueFramesetsPathCSV));

			CSVWriter csvw = new CSVWriter(writer, ',', CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.NO_ESCAPE_CHARACTER,
					CSVWriter.DEFAULT_LINE_END);
			BufferedReader br = new BufferedReader(new FileReader(new File(setFilePath)));
			OutputStream fos = new FileOutputStream(new File(uniqueFramesetsPath));
			int linenumber = 0;
			String line;
			String[] cols;
			String[] frames;
			long[][] frameMasks;
			long[] framesetMask;
//			int hashCodeFramesetMask;
			String bitmaskString;
			String[] header = getHeader(frameListPath);
			int numberOfFrames = header.length;
			csvw.writeNext(header);
			while ((line = br.readLine()) != null) {
				if (linenumber % 100000 == 0) {

					logger.info("Line " + nf.format(linenumber));
					logger.info("Number of unique framesets " + nf.format(uniqueFrameSets.size()));
				}

				cols = line.split("\t");
				frames = cols[1].split(" ");
				frameMasks = new long[frames.length][];
				for (int i = 0; i < frames.length; i++) {
					frameMasks[i] = framesToBitMask.get(frames[i]);
				}
				framesetMask = BitMask.or(frameMasks);
//				hashCodeFramesetMask = framesetMask.hashCode();
//				bitmaskString = BitMask.longBitMaskToString(framesetMask);
				if (!uniqueFrameSets.contains(cols[0].hashCode())) {
					bitmaskString = BitMask.longBitMaskToString(framesetMask);
					bitmaskString = bitmaskString.substring(bitmaskString.length() - numberOfFrames);
					fos.write(bitmaskString.getBytes());
					fos.write('\n');
					csvw.writeNext(bitmaskString.split(""));
					uniqueFrameSets.add(cols[0].hashCode());
				}
				linenumber++;
			}
			csvw.flush();
			csvw.close();
			br.close();
			fos.flush();
			fos.close();
			logger.info("Number of unique framesets " + uniqueFrameSets.size());
		} catch (ConfigurationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static Map<String, long[]> frameSetToBitMask(String frameListPath) throws IOException {
		Map<String, long[]> result = new HashMap<>();
		BufferedReader br = new BufferedReader(new FileReader(new File(frameListPath)));
		int numberOfFrames = (int) FileUtils.countNumberOfLines(frameListPath);
		String line;
		for (int frameNumber = 0; (line = br.readLine()) != null; frameNumber++) {
			result.put(line.trim(), BitMask.longBitMask(frameNumber, numberOfFrames));
		}
		br.close();
		return result;
	}

	private static String[] getHeader(String frameListPath) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(new File(frameListPath)));
		int numberOfFrames = (int) FileUtils.countNumberOfLines(frameListPath);
		String[] result = new String[numberOfFrames];
		String line;
		for (int frameNumber = numberOfFrames - 1; (line = br.readLine()) != null; frameNumber--) {
			result[frameNumber] = line.trim();
		}
		br.close();
		return result;
	}

}

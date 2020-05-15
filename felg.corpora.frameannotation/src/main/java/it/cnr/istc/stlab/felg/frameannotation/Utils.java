package it.cnr.istc.stlab.felg.frameannotation;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

public class Utils {
	
	private static final int BUFFER_SIZE = 1024;
	
	public static String readBZ2File(String file) throws CompressorException, IOException {
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

}

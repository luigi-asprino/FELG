package it.cnr.istc.stlab.felg;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

public class ArchiveReader {

	public String path;
	public BufferedReader br;

	public ArchiveReader(String path) throws FileNotFoundException, CompressorException {
		super();
		this.path = path;
		init();
	}

	private void init() throws FileNotFoundException, CompressorException {
		FileInputStream fin = new FileInputStream(path);
		BufferedInputStream bis = new BufferedInputStream(fin);
		CompressorInputStream input = new CompressorStreamFactory().createCompressorInputStream(bis);
		br = new BufferedReader(new InputStreamReader(input));
	}

	public ArticleReader nextArticle() throws IOException {
		String line = null;
		if ((line = br.readLine()) != null) {
			return new ArticleReader(line);
		}
		return null;
	}
}

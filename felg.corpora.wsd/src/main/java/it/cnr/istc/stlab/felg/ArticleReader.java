package it.cnr.istc.stlab.felg;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jsoniter.JsonIterator;

public class ArticleReader {
	private static final Pattern pattern = Pattern.compile("(<a href=\"(.*?)\">)(.*?)(</a>)");

	private com.jsoniter.any.Any json;
	private String text, clean = null;

	public ArticleReader(String json) {
		super();
		this.json = JsonIterator.deserialize(json);
		this.text = this.json.get("text").toString();
	}

	public String getTitle() {
		return json.get("title").toString();
	}

	public String getURL() {
		return json.get("url").toString();
	}
	
	public String getText() {
		return getText(false);
	}

	public String getText(boolean clean) {
		if (clean)
			return cleanText(text);
		return text;
	}
	
	public String getAbstract() {
		return getAbstract(false);
	}

	public String getAbstract(boolean clean) {
		if (clean)
			return cleanText(text.substring(0, text.indexOf("Section::::")));
		return text.substring(0, text.indexOf("Section::::"));
	}

	private String cleanText(String rawText) {
		if (clean == null) {
			StringBuilder sb = new StringBuilder(rawText.length());
			Matcher m = pattern.matcher(rawText);
			int lastEnd = 0;
			while (m.find()) {

				int startATag = m.start(1);
				String annotatedMention = m.group(3);
				sb.append(rawText.substring(lastEnd, startATag));
				sb.append(annotatedMention);
				lastEnd = m.end(4);

			}
			sb.append(rawText.substring(lastEnd));
			clean = sb.toString();
		}
		return clean.replaceAll("\\n", " ");
	}

}

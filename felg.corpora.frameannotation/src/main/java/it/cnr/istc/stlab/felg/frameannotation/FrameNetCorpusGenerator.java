package it.cnr.istc.stlab.felg.frameannotation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.ext.com.google.common.collect.Lists;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;

import com.jsoniter.output.JsonStream;

import it.cnr.istc.stlab.felg.frameannotation.model.AnnotatedArticle;
import it.cnr.istc.stlab.felg.frameannotation.model.AnnotatedMultiWord;
import it.cnr.istc.stlab.felg.frameannotation.model.AnnotatedSentence;

public class FrameNetCorpusGenerator {

	private static final Logger logger = org.slf4j.LoggerFactory.getLogger(FrameNetCorpusGenerator.class);

	public static void createCorpus(String fileIn, String folderOutPath) throws FileNotFoundException {
		File folderOut = new File(folderOutPath);
		folderOut.mkdir();

		Model m = ModelFactory.createDefaultModel();
		RDFDataMgr.read(m, fileIn);

		String q = "PREFIX earmark: <http://www.essepuntato.it/2008/12/earmark#>  SELECT DISTINCT ?sd ?content {?sd a earmark:StringDocuverse; earmark:hasContent ?content }";
		QueryExecution qexec = QueryExecutionFactory.create(q, m);

		ParameterizedSparqlString pss = new ParameterizedSparqlString(
				"PREFIX earmark: <http://www.essepuntato.it/2008/12/earmark#> PREFIX semiotics: <http://ontologydesignpatterns.org/cp/owl/semiotics.owl#> PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#> SELECT DISTINCT * {?pr a earmark:PointerRange ; rdfs:label ?label ; semiotics:denotes ?frame ; earmark:begins ?beg ; earmark:ends ?ends ; earmark:refersTo ?sd }");

		int c = 0;
		ResultSet rs = qexec.execSelect();
		while (rs.hasNext()) {
			QuerySolution qs = (QuerySolution) rs.next();
			pss.setIri("sd", qs.get("sd").asResource().getURI());
			String content = qs.get("content").asLiteral().getString();
			List<AnnotatedMultiWord> amws = new ArrayList<>();

			QueryExecution qexec2 = QueryExecutionFactory.create(pss.asQuery(), m);
			ResultSet rs2 = qexec2.execSelect();

			while (rs2.hasNext()) {
				QuerySolution qs2 = (QuerySolution) rs2.next();
				String label = qs2.get("label").asLiteral().getString();
				String frame = qs2.get("frame").asResource().getURI()
						.replace("https://w3id.org/framester/framenet/abox/frame/", "f:");
				int beg = qs2.get("beg").asLiteral().getInt();
				int ends = qs2.get("ends").asLiteral().getInt();

				AnnotatedMultiWord amw = new AnnotatedMultiWord();
				amw.setFrame(frame);
				amw.setMention(label);
				amw.setBeg(beg);
				amw.setEnd(ends);
				amw.setFrames(Lists.newArrayList(frame));
				amws.add(amw);
			}

			AnnotatedSentence as = new AnnotatedSentence();
			as.setAnnotations(amws);
			as.setSentence(content);

			AnnotatedArticle aa = new AnnotatedArticle();
			aa.setSentences(List.of(as));

			JsonStream.serialize(aa, new FileOutputStream(new File(folderOut + "/" + c + ".json")));

			c++;

			if (c % 10000 == 0) {
				logger.info("Processed {}", c);
			}

		}
	}

	public static void main(String[] args) throws FileNotFoundException {
		createCorpus("/Users/lgu/Dropbox/Lavoro/Projects/Framester/f/Framester_v3/input/fn/fn17/fnCorpus.ttl",
				"/Users/lgu/Desktop/FrameNetCorpus/");
	}

}

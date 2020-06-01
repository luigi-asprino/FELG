package it.cnr.istc.stlab.felg.corpus;

import java.io.IOException;

import tech.tablesaw.aggregate.AggregateFunctions;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.io.csv.CsvWriteOptions;

public class Analysis {

	public static void main(String[] args) {
		CsvReadOptions.Builder builder = CsvReadOptions.builder("/Users/lgu/Desktop/sentence_stat.txt").separator('\t')
				.header(false);
		CsvReadOptions options = builder.build();

		try {
			Table t1 = Table.read().usingOptions(options);
//			AggregateFunctions.sum;
//			t1.summarize(t1.column(2),AggregateFunctions.count);
//			System.out.println(t1.summarize(t1.column(0), t1.column(1), AggregateFunctions.count)
//					.by(t1.categoricalColumn(2)).toString());
//			System.out.println(t1.toString());
			Table summary = t1.summarize(t1.column(0), AggregateFunctions.count)
			.by(t1.categoricalColumn(2));
			
			System.out.println(summary.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

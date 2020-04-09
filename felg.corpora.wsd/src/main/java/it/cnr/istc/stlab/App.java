package it.cnr.istc.stlab ;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

	private static Logger logger = LoggerFactory.getLogger(App.class);

	public static void main(String[] args){	
		try {
			Configurations configs = new Configurations();
			Configuration config = configs.properties("config.properties");
			logger.info(config.getString("prova"));
		} catch (ConfigurationException e) {
			e.printStackTrace();
		}
	}
}

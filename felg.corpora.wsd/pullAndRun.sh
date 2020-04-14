#!bin/bash
DISAMBIGUATE=/tf/disambiguate/java
FELG=/tf/FELG/felg.corpora.wsd
cd $DISAMBIGUATE
git pull
mvn clean install
cd $FELG
git pull
mvn clean install
mvn exec:java -Dexec.mainClass="it.cnr.istc.stlab.felg.MainParallel" -Dexec.args="/tf/FELG/felg.corpora.wsd/src/main/resources/kb.properties" -DjvmArgs="-Xmx8g"


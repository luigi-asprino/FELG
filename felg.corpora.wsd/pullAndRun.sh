#!bin/bash
DISAMBIGUATE=/tf/disambiguate/java
FELG=/tf/FELG/felg.corpora.wsd
cd $DISAMBIGUATE
git pull
mvn clean install
cd $FELG
git pull
mvn clean install


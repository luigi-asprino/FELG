FROM maven:3.6.3-jdk-8

RUN cd /opt  && \
	git clone https://github.com/stlab-istc-cnr/FELG.git && \
	cd felg.corpora.frameannotation/ && \
	mvn clean install && \
	cd ../felg.wiki.filter/ && \
	mvn clean install

VOLUME /opt/data

VOLUME /opt/labelMap

VOLUME /opt/corpusDB

VOLUME /opt/lod_laundromat

EXPOSE 9010
EXPOSE 9011


CMD ["/bin/bash", "/opt/fox-domain-analysis/run.sh"]

# Docker

1. Build docker image ``felg_filter``  (from the `Docker` directory).

```
 docker build . -t felg_filter
```
2. Copy the RDF file that you want to analyse in ``vol/`` (e.g. ``lov.nq.gz``).

3. Edit configuration file (``conf.properties``) in ``vol/``.

4. Run run docker image ``edwin_test``

```
 docker run --volume=$(pwd)/vol:/opt/data edwin_test
 ```
 The resulting ESG together with its statistics can be found in ``vol/${esgName}`` (where ``${esgName}`` is the name of the Equivalence Set Graph provided in the configuration file ``vol/conf.properties``.

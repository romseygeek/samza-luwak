Samza-Luwak Proof of Concept
============================

This project is an experimental setup for using [Luwak](https://github.com/flaxsearch/luwak)
within [Samza](http://samza.incubator.apache.org/).

Use case: imagine you want to implement something like
[Google Alerts](https://www.google.com/alerts). Every time a new document is published, you want to
run it through a list of search queries, and notify someone if the document matches one of the
queries. (There's actually a [whole industry](http://en.wikipedia.org/wiki/Media_monitoring) that
specialises in doing this.) A similar use case is Twitter search, when you want to see a stream of
tweets matching a search query.

We're exploring ways of making such streaming search queries scalable (scaling both to large
throughput of documents and large numbers of queries), using the following tools:

* [Luwak](https://github.com/flaxsearch/luwak) is a wrapper library around
  [Lucene](http://lucene.apache.org/). Lucene does the document indexing and querying, and Luwak
  adds some optimisations to improve performance when each document needs to be matched against
  a large number (hundreds of thousands) of queries.
* [Samza](http://samza.incubator.apache.org/) is a distributed stream processing framework
  based on [Kafka](http://kafka.apache.org/) and
  [Hadoop YARN](http://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/YARN.html).
  It provides fault-tolerant message processing and a cluster deployment mechanism.

What we're doing is a bit similar to
[ElasticSearch's percolator](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/search-percolate.html),
but we think it has the potential to perform better and be more scalable.


High-level architecture
-----------------------

There are two input streams and one output stream, which are implemented as Kafka topics:

* **Queries** (input). When a user wants to start being alerted about documents matching some
  search query, they send a message to the queries stream. A unique ID is used as the key of the
  message. When the user wants to modify a query, or stop alerts for a particular query, they can
  update it by sending another message with the same key.
* **Documents** (input). Every document is published to a stream, and Luwak matches each document
  against the current set of stored queries.
* **Matches** (output). For each document that comes in, the job produces an output message that
  lists the IDs of the queries that matched the document. This can then be used by downstream
  consumers to alert the owners of those queries.

Note that there is currently no persistent index of documents. This is pure stream search: search
results include only documents that are published after the query is registered, but not historical
documents.


Partitioning
------------

How do we make this fast and scalable? Evaluating a single search query is fast, but if you're
matching each document against hundreds of thousands of queries (some of which can be very complex),
it can take a significant time to process every document. We are investigating several approaches:

* **Co-partitioning queries and documents**. In some cases, you know in advance that some queries 
  can only apply to certain documents, for example English-language queries are only applied to
  English-language documents. This creates a natural partitioning scheme, but requires that you
  define the partitioning manually.
* **Pre-searching**. If a query requires an unusual word to appear in the document, then that
  query only needs to be attempted on documents containing that word. Luwak already does this to
  some degree, and perhaps we can take it further.
* **Partitioning the query set**. We can have several partitions of the stream processor, and store
  each query on one of the partitions. Then each partition has a smaller number of queries to try,
  so each document can be matched faster. However, each document must be sent to all partitions of
  the stream processor, unless we have some prior knowledge about which queries may apply (using
  pre-searching).
* **Partitioning the document stream**. We can keep the entire list of queries on each stream
  processing node, and parallelise the processing by sending each document to only one of the
  nodes. This does not make the processing of any individual document any faster, but allows the
  process to scale to a higher throughput of documents.

At the moment, we are exploring partitioning of the query set. This requires a multi-stage pipeline:

1. Documents need to be published to *all* partitions of the documents stream.
   Queries need to be partitioned by query ID (and only published to one partition).
2. The matching job matches each documents against the queries within its partition, and emits a
   message to the "matches" stream, indicating which query IDs matched that document ID.
3. A second job consumes the "matches" stream, and waits for the matches from all the partitions
   to arrive. When all partitions have processed a particular document, this job emits a message
   to the "combined-matches" stream, which now includes the matching query IDs from all
   partitions.


Building
--------

This project is very hacky and experimental, and may not work at all. It's also a bit convoluted
to build right now, because it depends on various unreleased components.

Check out [Samza](https://github.com/apache/incubator-samza) (master branch), build it and install
it to your local Maven repository:

```bash
git clone https://github.com/apache/incubator-samza.git samza
cd samza
./gradlew -PscalaVersion=2.10 clean publishToMavenLocal
```

Check out [hello-samza](http://samza.incubator.apache.org/startup/hello-samza/latest/index.html)
(latest branch), and use it to launch Zookeeper, Kafka and YARN locally:

```bash
git clone https://github.com/apache/incubator-samza-hello-samza.git hello-samza
cd hello-samza
git checkout latest
bin/grid bootstrap
bin/grid start all
for topic in documents queries matches1 combinedmatches matches-combiner-changelog; do
  deploy/kafka/bin/kafka-topics.sh --zookeeper localhost:2181 --create --topic $topic --partitions 2 --replication-factor 1
done
```

Check out [Flax](http://www.flax.co.uk/)'s
[fork of Lucene](https://github.com/flaxsearch/lucene-solr-intervals) (positions-5x branch),
which has a new feature that Luwak needs, but is not yet upstream
(it should be committed upstream eventually):

```bash
git clone https://github.com/flaxsearch/lucene-solr-intervals.git
cd lucene-solr-internals
git checkout positions-5x
mvn -DskipTests install
```

Check out [Luwak](https://github.com/flaxsearch/luwak) (1.1.x branch), build it and install it
to your local Maven repository (note this currently doesn't work with JDK8):

```bash
git clone https://github.com/flaxsearch/luwak.git
cd luwak
git checkout 1.1.x
mvn install
```

Build and run this project:

```bash
git clone https://github.com/romseygeek/samza-luwak.git
cd samza-luwak
mvn clean package
mkdir deploy
tar -xzvf target/samza-luwak-1.0-SNAPSHOT-dist.tar.gz -C deploy/
deploy/bin/run-job.sh --config-path=file://$PWD/deploy/config/luwak.properties
deploy/bin/run-job.sh --config-path=file://$PWD/deploy/config/combiner.properties
```

Now you can try adding some test documents and queries to the system, and observe the output:

```bash
hello-samza/deploy/kafka/bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic combinedmatches --from-beginning &
java -jar samza-luwak/target/samza-luwak-1.0-SNAPSHOT.jar
q query1 foo AND bar
q query2 bar AND baz
d doc1 this document contains the words foo and bar only
d doc2 this document, on the other hand, mentions bar and baz.
d doc3 this one goes nuts and mentions foo, bar and baz -- all three!
d doc4 finally, this one mentions none of those words.
quit
```

In that command-line tool, queries are defined with "q", followed by the query ID, followed by a
Lucene query string. Documents are defined with "d", followed by the document ID, followed by the
text of the document.

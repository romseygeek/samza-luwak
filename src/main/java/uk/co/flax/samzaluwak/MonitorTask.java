package uk.co.flax.samzaluwak;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.samza.config.Config;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.system.SystemStream;
import org.apache.samza.task.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.flax.luwak.*;
import uk.co.flax.luwak.matchers.SimpleMatcher;
import uk.co.flax.luwak.presearcher.MatchAllPresearcher;
import uk.co.flax.luwak.queryparsers.LuceneQueryParser;

/**
 * Copyright (c) 2014 Lemur Consulting Ltd.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class MonitorTask implements StreamTask, InitableTask {

    private static final Logger logger = LoggerFactory.getLogger(MonitorTask.class);

    public static final String QUERIES_STREAM = "queries";
    public static final String DOCS_STREAM = "documents";

    private Monitor monitor;
    private Analyzer analyzer = new StandardAnalyzer();
    private MatcherFactory<QueryMatch> matcherFactory = SimpleMatcher.FACTORY;

    public static final SystemStream MATCHES_STREAM = new SystemStream("kafka", "matches1");

    @Override
    public void process(IncomingMessageEnvelope message, MessageCollector collector, TaskCoordinator taskCoordinator) throws Exception {
        String stream = message.getSystemStreamPartition().getStream();
        switch (stream) {
            case QUERIES_STREAM:
                update((String) message.getKey(), (Map<String, String>) message.getMessage());
                break;
            case DOCS_STREAM:
                Matches<QueryMatch> matches = match((String) message.getKey(), (Map<String, String>) message.getMessage());
                collector.send(new OutgoingMessageEnvelope(MATCHES_STREAM, message.getKey(), matches));
                break;
            default:
                throw new RuntimeException("Unknown stream: " + stream);
        }
    }

    @Override
    public void init(Config config, TaskContext taskContext) throws Exception {
        monitor = new Monitor(new LuceneQueryParser("f"), new MatchAllPresearcher());
    }

    private void update(String id, Map<String, String> message) throws IOException {
        MonitorQuery mq = new MonitorQuery(id, message.get("query"), message);
        logger.info("Adding new query: {}", mq);
        for (QueryError error : monitor.update(mq)) {
            logger.warn(error.toString());
        }
    }

    private Matches<QueryMatch> match(String id, Map<String, String> fields) throws IOException {

        InputDocument.Builder builder = InputDocument.builder(id);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            builder.addField(field.getKey(), field.getValue(), analyzer);
        }

        logger.info("Matching document {}", id);
        return monitor.match(builder.build(), matcherFactory);

    }

}

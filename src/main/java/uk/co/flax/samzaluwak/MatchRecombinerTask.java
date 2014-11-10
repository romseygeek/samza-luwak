package uk.co.flax.samzaluwak;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import org.apache.samza.config.Config;
import org.apache.samza.storage.kv.Entry;
import org.apache.samza.storage.kv.KeyValueIterator;
import org.apache.samza.storage.kv.KeyValueStore;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.system.SystemStream;
import org.apache.samza.task.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class MatchRecombinerTask implements StreamTask, InitableTask {

    private static final Logger logger = LoggerFactory.getLogger(MatchRecombinerTask.class);

    public static final int QUERY_PARTITIONS = 2;

    private KeyValueStore<String, Map<String, String>> store;

    public static final SystemStream MATCHES_STREAM = new SystemStream("kafka", "combinedmatches");

    @Override
    public void process(IncomingMessageEnvelope message, MessageCollector collector, TaskCoordinator taskCoordinator) throws Exception {
        String key = (String) message.getKey();
        Map<String, String> matches = (Map<String, String>) message.getMessage();
        store.put(key, matches);
        logger.info("Got partial match for {}", key);

        String originalKey = originalKey(key);

        Map<String, Map<String, String>> parts = collectMatches(originalKey);
        if (parts.size() != QUERY_PARTITIONS)
            return;

        logger.info("All partial matches for {} received", originalKey);
        List<Map<String, String>> combinedMatches = combineMatches(parts);

        collector.send(new OutgoingMessageEnvelope(MATCHES_STREAM, originalKey, combinedMatches));
        for (String storekey : parts.keySet()) {
            store.delete(storekey);
        }

    }

    private List<Map<String, String>> combineMatches(Map<String, Map<String, String>> parts) {
        return Lists.newArrayList(parts.values());
    }

    @Override
    public void init(Config config, TaskContext taskContext) throws Exception {
        this.store = (KeyValueStore<String, Map<String, String>>) taskContext.getStore("matches");
    }

    private Map<String, Map<String, String>> collectMatches(String key) {
        Map<String, Map<String, String>> parts = new HashMap<>();
        KeyValueIterator<String, Map<String, String>> it = store.range(key + "_", key + "_a");
        try {
            while (it.hasNext()) {
                Entry<String, Map<String, String>> entry = it.next();
                parts.put(entry.getKey(), entry.getValue());
            }
            logger.info("Found {} partial matches in store for {}", parts.size(), key);
            return parts;
        }
        finally {
            it.close();
        }

    }

    private String originalKey(String key) {
        return key.replaceAll("_.*?$", "");
    }
}

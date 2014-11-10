package uk.co.flax.samzaluwak;

import com.google.common.collect.ImmutableMap;
import org.apache.samza.Partition;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.system.SystemStreamPartition;
import org.apache.samza.task.MessageCollector;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

public class TestMonitorTask {

    public static final SystemStreamPartition QUERY_PART = new SystemStreamPartition("kafka", MonitorTask.QUERIES_STREAM, new Partition(0));
    public static final SystemStreamPartition DOCS_PART = new SystemStreamPartition("kafka", MonitorTask.DOCS_STREAM, new Partition(0));

    @Test
    public void testTask() throws Exception {

        MonitorTask task = new MonitorTask();
        task.init(null, null);

        MessageCollector collector = mock(MessageCollector.class);

        IncomingMessageEnvelope query = new IncomingMessageEnvelope(QUERY_PART, "", "1", ImmutableMap.of("query", "hello world"));
        task.process(query, collector, null);

        IncomingMessageEnvelope doc = new IncomingMessageEnvelope(DOCS_PART, "", "doc1", ImmutableMap.of("f", "hello world"));
        task.process(doc, collector, null);

        verify(collector, times(1)).send(any(OutgoingMessageEnvelope.class));

    }


}

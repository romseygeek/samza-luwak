package uk.co.flax.samzaluwak;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

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

public class Interact {

    public static void main(String... args) throws IOException {

        Properties props = new Properties();
        props.setProperty("metadata.broker.list", "localhost:9092");
        props.setProperty("serializer.class", "kafka.serializer.StringEncoder");
        ProducerConfig config = new ProducerConfig(props);

        Producer<String, String> producer = new Producer<>(config);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            String cmd = reader.readLine();
            if ("quit".equals(cmd))
                return;
            if (cmd.startsWith("q "))
                sendQueryUpdate(cmd, producer);
            if (cmd.startsWith("d "))
                sendDocument(cmd, producer);
        }

    }

    private static void sendQueryUpdate(String cmd, Producer<String, String> producer) {

        String[] parts = cmd.split("\\s", 3);
        String id = parts[1];
        String query = parts[2];

        KeyedMessage<String, String> message
                = new KeyedMessage<>(MonitorTask.QUERIES_STREAM, id, 0, "{ \"query\" : \"" + query + "\"}");
        producer.send(message);

    }

    private static void sendDocument(String cmd, Producer<String, String> producer) {

        String[] parts = cmd.split("\\s", 3);
        String id = parts[1];
        String doc = parts[2];

        KeyedMessage<String, String> message =
                new KeyedMessage<>(MonitorTask.DOCS_STREAM, id, 0, "{ \"f\" : \"" + doc + "\"}");
        producer.send(message);

    }

}

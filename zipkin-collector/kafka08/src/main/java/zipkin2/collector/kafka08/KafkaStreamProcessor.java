/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.collector.kafka08;

import java.util.Collections;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;

/** Consumes spans from Kafka messages, ignoring malformed input */
final class KafkaStreamProcessor implements Runnable {
  static final Callback<Void> NOOP =
      new Callback<Void>() {
        @Override
        public void onSuccess(Void value) {}

        @Override
        public void onError(Throwable t) {}
      };

  final KafkaStream<byte[], byte[]> stream;
  final Collector collector;
  final CollectorMetrics metrics;

  KafkaStreamProcessor(
      KafkaStream<byte[], byte[]> stream, Collector collector, CollectorMetrics metrics) {
    this.stream = stream;
    this.collector = collector;
    this.metrics = metrics;
  }

  @Override
  public void run() {
    ConsumerIterator<byte[], byte[]> messages = stream.iterator();
    while (messages.hasNext()) {
      byte[] bytes = messages.next().message();
      metrics.incrementMessages();

      if (bytes.length < 2) { // need two bytes to check if protobuf
        metrics.incrementMessagesDropped();
        continue;
      }

      // If we received legacy single-span encoding, decode it into a singleton list
      if (!protobuf3(bytes) && bytes[0] <= 16 && bytes[0] != 12 /* thrift, but not a list */) {
        try {
          metrics.incrementBytes(bytes.length);
          Span span = SpanBytesDecoder.THRIFT.decodeOne(bytes);
          collector.accept(Collections.singletonList(span), NOOP);
        } catch (RuntimeException e) {
          metrics.incrementMessagesDropped();
        }
      } else {
        collector.acceptSpans(bytes, NOOP);
      }
    }
  }

  /* span key or trace ID key */
  static boolean protobuf3(byte[] bytes) {
    return bytes[0] == 10 && bytes[1] != 0; // varint follows and won't be zero
  }
}

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
package zipkin2.collector.scribe;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;
import zipkin2.internal.Nullable;

final class ScribeSpanConsumer implements Scribe {
  final Collector collector;
  final CollectorMetrics metrics;
  final String category;

  ScribeSpanConsumer(ScribeCollector.Builder builder) {
    this.collector = builder.delegate.build();
    this.metrics = builder.metrics;
    this.category = builder.category;
  }

  @Override
  public ListenableFuture<ResultCode> log(List<LogEntry> messages) {
    metrics.incrementMessages();
    List<Span> spans;
    try {
      spans =
          messages
              .stream()
              .filter(m -> m.category.equals(category))
              .map(m -> m.message.getBytes(StandardCharsets.ISO_8859_1))
              .map(b -> Base64.getMimeDecoder().decode(b)) // finagle-zipkin uses mime encoding
              .peek(b -> metrics.incrementBytes(b.length))
              .map(SpanBytesDecoder.THRIFT::decodeOne)
              .collect(Collectors.toList());
    } catch (RuntimeException e) {
      metrics.incrementMessagesDropped();
      return Futures.immediateFailedFuture(e);
    }

    SettableFuture<ResultCode> result = SettableFuture.create();
    collector.accept(
        spans,
        new Callback<Void>() {
          @Override
          public void onSuccess(@Nullable Void value) {
            result.set(ResultCode.OK);
          }

          @Override
          public void onError(Throwable t) {
            result.setException(t);
          }
        });
    return result;
  }
}

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
package zipkin2.autoconfigure.storage.cassandra3;

import brave.Tracing;
import brave.cassandra.driver.TracingSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import zipkin2.storage.cassandra.CassandraStorage.SessionFactory;

/** Sets up the Cassandra tracing in Brave as an initialization. */
@ConditionalOnBean(Tracing.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "cassandra3")
@Configuration
// This component is named .*Cassandra3.* even though the package already says cassandra3 because
// Spring Boot configuration endpoints only printout the simple name of the class
class TracingZipkinCassandra3StorageAutoConfiguration {
  final SessionFactory delegate = SessionFactory.DEFAULT;

  // Lazy to unwind a circular dep: we are tracing the storage used by brave
  @Autowired @Lazy Tracing tracing;

  // NOTE: this doesn't yet trace span consumption commands because the trace context
  // is lost when indirected with SpanConsumer.accept().enqueue(). We'll fix this later
  @Bean
  SessionFactory tracingSessionFactory() {
    return storage -> TracingSession.create(tracing, delegate.create(storage));
  }
}

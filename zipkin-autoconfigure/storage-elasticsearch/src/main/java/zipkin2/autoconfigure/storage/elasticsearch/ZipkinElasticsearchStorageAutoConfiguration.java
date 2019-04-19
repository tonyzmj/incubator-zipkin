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
package zipkin2.autoconfigure.storage.elasticsearch;

import java.util.List;
import java.util.logging.Logger;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.storage.StorageComponent;

@Configuration
@EnableConfigurationProperties(ZipkinElasticsearchStorageProperties.class)
@ConditionalOnProperty(name = "zipkin.storage.type", havingValue = "elasticsearch")
@ConditionalOnMissingBean(StorageComponent.class)
// intentionally public for import by zipkin-autoconfigure-storage-elasticsearch-aws
public class ZipkinElasticsearchStorageAutoConfiguration {

  @Bean
  @Qualifier("zipkinElasticsearchHttp")
  @Conditional(HttpLoggingSet.class)
  Interceptor loggingInterceptor(ZipkinElasticsearchStorageProperties es) {
    Logger logger = Logger.getLogger(ElasticsearchStorage.class.getName());
    return new HttpLoggingInterceptor(logger::info).setLevel(es.getHttpLogging());
  }

  @Bean
  @Qualifier("zipkinElasticsearchHttp")
  @Conditional(BasicAuthRequired.class)
  Interceptor basicAuthInterceptor(ZipkinElasticsearchStorageProperties es) {
    return new BasicAuthInterceptor(es);
  }

  @Bean
  @ConditionalOnMissingBean
  StorageComponent storage(ElasticsearchStorage.Builder esHttpBuilder) {
    return esHttpBuilder.build();
  }

  @Bean
  ElasticsearchStorage.Builder esHttpBuilder(
      ZipkinElasticsearchStorageProperties elasticsearch,
      @Qualifier("zipkinElasticsearchHttp") OkHttpClient client,
      @Value("${zipkin.query.lookback:86400000}") int namesLookback,
      @Value("${zipkin.storage.strict-trace-id:true}") boolean strictTraceId,
      @Value("${zipkin.storage.search-enabled:true}") boolean searchEnabled,
      @Value("${zipkin.storage.autocomplete-keys:}") List<String> autocompleteKeys,
      @Value("${zipkin.storage.autocomplete-ttl:3600000}") int autocompleteTtl,
      @Value("${zipkin.storage.autocomplete-cardinality:20000}") int autocompleteCardinality) {
    return elasticsearch
        .toBuilder(client)
        .namesLookback(namesLookback)
        .strictTraceId(strictTraceId)
        .searchEnabled(searchEnabled)
        .autocompleteKeys(autocompleteKeys)
        .autocompleteTtl(autocompleteTtl)
        .autocompleteCardinality(autocompleteCardinality);
  }

  static final class HttpLoggingSet implements Condition {
    @Override
    public boolean matches(ConditionContext condition, AnnotatedTypeMetadata ignored) {
      return !isEmpty(
          condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.http-logging"));
    }
  }

  static final class BasicAuthRequired implements Condition {
    @Override
    public boolean matches(ConditionContext condition, AnnotatedTypeMetadata ignored) {
      String userName =
          condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.username");
      String password =
          condition.getEnvironment().getProperty("zipkin.storage.elasticsearch.password");
      return !isEmpty(userName) && !isEmpty(password);
    }
  }

  private static boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }
}

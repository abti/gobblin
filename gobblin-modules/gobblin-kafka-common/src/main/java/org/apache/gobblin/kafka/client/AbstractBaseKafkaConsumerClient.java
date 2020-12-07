/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.gobblin.kafka.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;

import javax.annotation.Nonnull;

import org.apache.gobblin.configuration.ConfigurationKeys;
import org.apache.gobblin.source.extractor.extract.kafka.KafkaTopic;
import org.apache.gobblin.util.ConfigUtils;
import org.apache.gobblin.util.DatasetFilterUtils;


/**
 * A base {@link GobblinKafkaConsumerClient} that sets configurations shared by all {@link GobblinKafkaConsumerClient}s
 */
public abstract class AbstractBaseKafkaConsumerClient implements GobblinKafkaConsumerClient {

  public static final String CONFIG_NAMESPACE = "source.kafka";
  public static final String CONFIG_PREFIX = CONFIG_NAMESPACE + ".";
  public static final String CONSUMER_CONFIG = "consumerConfig";
  public static final String CONFIG_KAFKA_FETCH_TIMEOUT_VALUE = CONFIG_PREFIX + "fetchTimeoutMillis";
  public static final int CONFIG_KAFKA_FETCH_TIMEOUT_VALUE_DEFAULT = 1000; // 1 second
  public static final String CONFIG_KAFKA_FETCH_REQUEST_MIN_BYTES = CONFIG_PREFIX + "fetchMinBytes";
  private static final int CONFIG_KAFKA_FETCH_REQUEST_MIN_BYTES_DEFAULT = 1024;
  public static final String CONFIG_KAFKA_SOCKET_TIMEOUT_VALUE = CONFIG_PREFIX + "socketTimeoutMillis";
  public static final int CONFIG_KAFKA_SOCKET_TIMEOUT_VALUE_DEFAULT = 30000; // 30 seconds

  protected final List<String> brokers;
  protected final int fetchTimeoutMillis;
  protected final int fetchMinBytes;
  protected final int socketTimeoutMillis;

  public AbstractBaseKafkaConsumerClient(Config config) {
    this.brokers = ConfigUtils.getStringList(config, ConfigurationKeys.KAFKA_BROKERS);
    if (this.brokers.isEmpty()) {
      throw new IllegalArgumentException("Need to specify at least one Kafka broker.");
    }

    this.socketTimeoutMillis =
        ConfigUtils.getInt(config, CONFIG_KAFKA_SOCKET_TIMEOUT_VALUE, CONFIG_KAFKA_SOCKET_TIMEOUT_VALUE_DEFAULT);
    this.fetchTimeoutMillis =
        ConfigUtils.getInt(config, CONFIG_KAFKA_FETCH_TIMEOUT_VALUE, CONFIG_KAFKA_FETCH_TIMEOUT_VALUE_DEFAULT);
    this.fetchMinBytes =
        ConfigUtils.getInt(config, CONFIG_KAFKA_FETCH_REQUEST_MIN_BYTES, CONFIG_KAFKA_FETCH_REQUEST_MIN_BYTES_DEFAULT);

    Preconditions.checkArgument((this.fetchTimeoutMillis < this.socketTimeoutMillis),
        "Kafka Source configuration error: FetchTimeout " + this.fetchTimeoutMillis
            + " must be smaller than SocketTimeout " + this.socketTimeoutMillis);
  }

  @Override
  public List<KafkaTopic> getFilteredTopics(final List<Pattern> blacklist, final List<Pattern> whitelist) {
    return Lists.newArrayList(Iterables.filter(getTopics(), new Predicate<KafkaTopic>() {
      @Override
      public boolean apply(@Nonnull KafkaTopic kafkaTopic) {
        return DatasetFilterUtils.survived(kafkaTopic.getName(), blacklist, whitelist);
      }
    }));
  }

  /**
   * A helper method that returns the canonical metric name for a kafka metric. A typical canonicalized metric name would
   * be of the following format: "{metric-group}_{client-id}_{metric-name}". This method is invoked in {@link GobblinKafkaConsumerClient#getMetrics()}
   * implementations to convert KafkaMetric names to a Coda Hale metric name. Note that the canonicalization is done on every invocation of the
   * {@link GobblinKafkaConsumerClient#getMetrics()} ()} API.
   * @param metricGroup the type of the Kafka metric e.g."consumer-fetch-manager-metrics", "consumer-coordinator-metrics" etc.
   * @param metricTags any tags associated with the Kafka metric, typically include the kafka client id, topic name, partition number etc.
   * @param metricName the name of the Kafka metric e.g. "records-lag-max", "fetch-throttle-time-max" etc.
   * @return the canonicalized metric name.
   */
  protected String canonicalMetricName(String metricGroup, Collection<String> metricTags, String metricName) {
    List<String> nameParts = new ArrayList<>();
    nameParts.add(metricGroup);
    nameParts.addAll(metricTags);
    nameParts.add(metricName);

    StringBuilder builder = new StringBuilder();
    for (String namePart : nameParts) {
      builder.append(namePart);
      builder.append(".");
    }
    // Remove the trailing dot.
    builder.setLength(builder.length() - 1);
    String processedName = builder.toString().replace(' ', '_').replace("\\.", "_");

    return processedName;
  }


  /**
   * Get a list of all kafka topics
   */
  public abstract List<KafkaTopic> getTopics();
}

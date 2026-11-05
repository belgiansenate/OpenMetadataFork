/*
 *  Copyright 2026 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.service.search;

/** Bounds shared by the ElasticSearch and OpenSearch by-query writes. */
public final class SearchPropagationLimits {

  private SearchPropagationLimits() {}

  /**
   * Sustained write ceiling for a child fan-out, in documents per second.
   *
   * <p>The widest parent is a service, so one tag edit can rewrite every asset it ingested. The
   * client default is unlimited, which lets that scan saturate the cluster and starve concurrent
   * ingestion and search. 2000/s finishes a million-document service in around eight minutes while
   * leaving headroom; propagation is a background task whose latency nobody waits on, so trading
   * completion time for cluster stability is the right way round.
   */
  public static final float REQUESTS_PER_SECOND = 2000f;

  /**
   * Keep-alive of the scroll behind every update-by-query, delete-by-query and reindex. A user
   * without the cluster-level {@code indices:data/read/scroll/clear} permission cannot have the
   * engine clear these contexts, so each lives until this expires: 1 minute outlives any batch and
   * keeps a shared cluster under {@code search.max_open_scroll_context}
   * (ADR:2026-11-04-by-query-writes-name-a-one-minute-scroll-keep-alive).
   */
  public static final String BY_QUERY_SCROLL_KEEP_ALIVE = "1m";
}

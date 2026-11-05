package org.openmetadata.service.search.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.co.elastic.clients.elasticsearch.ElasticsearchClient;
import es.co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import es.co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import es.co.elastic.clients.elasticsearch.core.ReindexRequest;
import es.co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import es.co.elastic.clients.elasticsearch.core.UpdateByQueryResponse;
import es.co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import es.co.elastic.clients.util.ObjectBuilder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.openmetadata.schema.api.lineage.EsLineageData;
import org.openmetadata.service.Entity;
import org.openmetadata.service.search.SearchPropagationLimits;
import org.openmetadata.service.search.SearchRepository;

/**
 * Every by-query write names the scroll keep-alive, so a context the engine could not clear (a
 * role without {@code indices:data/read/scroll/clear}) expires after one minute, not five.
 */
class ElasticSearchEntityManagerScrollKeepAliveTest {

  private static final String INDEX = "table_search_index";
  private static final String KEEP_ALIVE = SearchPropagationLimits.BY_QUERY_SCROLL_KEEP_ALIVE;

  private ElasticsearchClient client;
  private MockedStatic<Entity> entity;
  private ElasticSearchEntityManager manager;

  @BeforeEach
  void setUp() {
    client = mock(ElasticsearchClient.class);
    when(client.indices()).thenReturn(mock(ElasticsearchIndicesClient.class));
    SearchRepository searchRepository = mock(SearchRepository.class);
    when(searchRepository.getIndexOrAliasName(anyString())).thenAnswer(call -> call.getArgument(0));
    entity = mockStatic(Entity.class);
    entity.when(Entity::getSearchRepository).thenReturn(searchRepository);
    manager = new ElasticSearchEntityManager(client);
  }

  @AfterEach
  void tearDown() {
    entity.close();
  }

  @Test
  void aLineageUpdateNamesTheKeepAlive() throws Exception {
    UpdateByQueryResponse response = updateResponse();
    when(client.updateByQuery(any(UpdateByQueryRequest.class))).thenReturn(response);

    manager.updateLineage(INDEX, Pair.of("_id", UUID.randomUUID().toString()), new EsLineageData());

    ArgumentCaptor<UpdateByQueryRequest> request =
        ArgumentCaptor.forClass(UpdateByQueryRequest.class);
    verify(client).updateByQuery(request.capture());
    assertEquals(KEEP_ALIVE, request.getValue().scroll().time());
  }

  @Test
  void aBackgroundChildPropagationNamesTheKeepAlive() {
    UpdateByQueryRequest request =
        manager.buildUpdateChildrenRequest(
            List.of(INDEX), Pair.of("service.id", "s1"), Pair.of("ctx._source.x = 1", Map.of()));

    assertEquals(KEEP_ALIVE, request.scroll().time());
  }

  @Test
  @SuppressWarnings("unchecked")
  void anUpdateBuiltInlineNamesTheKeepAlive() throws Exception {
    ArgumentCaptor<Function<UpdateByQueryRequest.Builder, ObjectBuilder<UpdateByQueryRequest>>>
        builder = ArgumentCaptor.forClass(Function.class);
    UpdateByQueryResponse response = updateResponse();
    when(client.updateByQuery(any(Function.class))).thenReturn(response);

    manager.updateChildrenByNestedField(
        List.of(INDEX),
        List.of("d1"),
        List.of("columns"),
        "tags.tagFQN",
        List.of("PII.Sensitive"),
        Pair.of("ctx._source.x = 1", Map.of()));

    verify(client).updateByQuery(builder.capture());
    UpdateByQueryRequest request =
        builder.getValue().apply(new UpdateByQueryRequest.Builder()).build();
    assertEquals(KEEP_ALIVE, request.scroll().time());
  }

  @Test
  @SuppressWarnings("unchecked")
  void aDeleteByQueryNamesTheKeepAlive() throws Exception {
    ArgumentCaptor<Function<DeleteByQueryRequest.Builder, ObjectBuilder<DeleteByQueryRequest>>>
        builder = ArgumentCaptor.forClass(Function.class);
    DeleteByQueryResponse response = mock(DeleteByQueryResponse.class);
    when(response.failures()).thenReturn(List.of());
    when(client.deleteByQuery(any(Function.class))).thenReturn(response);

    manager.deleteEntityByFields(List.of(INDEX), List.of(Pair.of("service.id", "s1")));

    verify(client).deleteByQuery(builder.capture());
    DeleteByQueryRequest request =
        builder.getValue().apply(new DeleteByQueryRequest.Builder()).build();
    assertEquals(KEEP_ALIVE, request.scroll().time());
  }

  @Test
  @SuppressWarnings("unchecked")
  void aReindexNamesTheKeepAlive() throws Exception {
    ArgumentCaptor<Function<ReindexRequest.Builder, ObjectBuilder<ReindexRequest>>> builder =
        ArgumentCaptor.forClass(Function.class);

    manager.reindexWithEntityIds(
        List.of(INDEX), "table_vector_index", "pipeline", "table", List.of(UUID.randomUUID()));

    verify(client).reindex(builder.capture());
    ReindexRequest request = builder.getValue().apply(new ReindexRequest.Builder()).build();
    assertEquals(KEEP_ALIVE, request.scroll().time());
  }

  private static UpdateByQueryResponse updateResponse() {
    UpdateByQueryResponse response = mock(UpdateByQueryResponse.class);
    when(response.updated()).thenReturn(1L);
    when(response.versionConflicts()).thenReturn(0L);
    when(response.failures()).thenReturn(List.of());
    return response;
  }
}

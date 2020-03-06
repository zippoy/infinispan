package org.infinispan.server.extensions;

import static org.junit.Assert.assertEquals;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryModifiedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryRemovedEvent;
import org.infinispan.client.hotrod.event.ClientEvent;
import org.infinispan.server.test.junit4.InfinispanServerRule;
import org.infinispan.server.test.junit4.InfinispanServerTestMethodRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Tristan Tarrant &lt;tristan@infinispan.org&gt;
 * @since 10.0
 **/
public class CustomEventFilter {

   @ClassRule
   public static final InfinispanServerRule SERVERS = ExtensionsIT.SERVERS;

   @Rule
   public InfinispanServerTestMethodRule SERVER_TEST = new InfinispanServerTestMethodRule(SERVERS);

   @Test
   public void testEventFilteringStatic() {

      RemoteCache remoteCache = SERVER_TEST.hotrod().create();

      final StaticFilteredEventLogListener eventListener = new StaticFilteredEventLogListener();
      remoteCache.addClientListener(eventListener);
      try {
         expectNoEvents(eventListener);
         remoteCache.put(1, "one");
         expectNoEvents(eventListener);
         remoteCache.put(2, "two");
         expectOnlyCreatedEvent(2, eventListener);
         remoteCache.remove(1);
         expectNoEvents(eventListener);
         remoteCache.remove(2);
         expectOnlyRemovedEvent(2, eventListener);
      } finally {
         remoteCache.removeClientListener(eventListener);
      }
   }

   public static <K> void expectOnlyRemovedEvent(K key, EventLogListener eventListener) {
      expectSingleEvent(key, eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_REMOVED);
      expectNoEvents(eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_CREATED);
      expectNoEvents(eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_MODIFIED);
   }

   public static <K> void expectOnlyModifiedEvent(K key, EventLogListener eventListener) {
      expectSingleEvent(key, eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_MODIFIED);
      expectNoEvents(eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_CREATED);
      expectNoEvents(eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_REMOVED);
   }

   public static <K> void expectSingleEvent(K key, EventLogListener eventListener, ClientEvent.Type type) {
      switch (type) {
         case CLIENT_CACHE_ENTRY_CREATED:
            ClientCacheEntryCreatedEvent createdEvent = eventListener.pollEvent(type);
            assertEquals(key, createdEvent.getKey());
            break;
         case CLIENT_CACHE_ENTRY_MODIFIED:
            ClientCacheEntryModifiedEvent modifiedEvent = eventListener.pollEvent(type);
            assertEquals(key, modifiedEvent.getKey());
            break;
         case CLIENT_CACHE_ENTRY_REMOVED:
            ClientCacheEntryRemovedEvent removedEvent = eventListener.pollEvent(type);
            assertEquals(key, removedEvent.getKey());
            break;
      }
      assertEquals(0, eventListener.queue(type).size());
   }

   public static <K> void expectOnlyCreatedEvent(K key, EventLogListener eventListener) {
      expectSingleEvent(key, eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_CREATED);
      expectNoEvents(eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_MODIFIED);
      expectNoEvents(eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_REMOVED);
   }

   public static void expectNoEvents(EventLogListener eventListener) {
      expectNoEvents(eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_CREATED);
      expectNoEvents(eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_MODIFIED);
      expectNoEvents(eventListener, ClientEvent.Type.CLIENT_CACHE_ENTRY_REMOVED);
   }

   public static void expectNoEvents(EventLogListener eventListener, ClientEvent.Type type) {
      switch (type) {
         case CLIENT_CACHE_ENTRY_CREATED:
            assertEquals(0, eventListener.createdEvents.size());
            break;
         case CLIENT_CACHE_ENTRY_MODIFIED:
            assertEquals(0, eventListener.modifiedEvents.size());
            break;
         case CLIENT_CACHE_ENTRY_REMOVED:
            assertEquals(0, eventListener.removedEvents.size());
            break;
      }
   }
   @ClientListener(filterFactoryName = "static-filter-factory")
   public static class StaticFilteredEventLogListener extends EventLogListener {}
}

package org.infinispan.server.extensions;

import org.infinispan.notifications.cachelistener.filter.CacheEventFilterFactory;
import org.infinispan.server.test.core.ServerRunMode;
import org.infinispan.server.test.junit4.InfinispanServerRule;
import org.infinispan.server.test.junit4.InfinispanServerRuleBuilder;
import org.infinispan.tasks.ServerTask;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * @author Tristan Tarrant &lt;tristan@infinispan.org&gt;
 * @since 10.0
 **/
@RunWith(Suite.class)
@Suite.SuiteClasses({
      ScriptingTasks.class,
      ServerTasks.class,
      CustomEventFilter.class
})
public class ExtensionsIT {
   @ClassRule
   public static final InfinispanServerRule SERVERS =
         InfinispanServerRuleBuilder.config("configuration/ClusteredServerTest.xml")
                                    .runMode(ServerRunMode.CONTAINER)
                                    .numServers(2)
                                    .artifacts(artifacts())
                                    .build();

   public static JavaArchive[] artifacts() {
      JavaArchive hello = ShrinkWrap.create(JavaArchive.class, "hello-server-task.jar");
      hello.addClass(HelloServerTask.class);
      hello.addAsServiceProvider(ServerTask.class, HelloServerTask.class);

      JavaArchive distHello = ShrinkWrap.create(JavaArchive.class, "distributed-hello-server-task.jar");
      distHello.addClass(DistributedHelloServerTask.class);
      distHello.addAsServiceProvider(ServerTask.class, DistributedHelloServerTask.class);

      JavaArchive staticFilterFactory = ShrinkWrap.create(JavaArchive.class, "static-filter-factory.jar");
      staticFilterFactory.addClass(StaticCacheEventFilterFactory.class);
      staticFilterFactory.addAsServiceProvider(CacheEventFilterFactory.class, StaticCacheEventFilterFactory.class);

      return new JavaArchive[] {hello, distHello, staticFilterFactory};
   }
}

/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.infinispan.commands;

import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.commands.remote.BaseRpcCommand;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.context.Flag;
import org.infinispan.context.InvocationContext;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Command to create/start a cache on a subset of Infinispan cluster nodes
 * @author Vladimir Blagojevic
 * @since 5.2
 */
public class CreateCacheCommand extends BaseRpcCommand {

   private static final Log log = LogFactory.getLog(CreateCacheCommand.class);
   public static final byte COMMAND_ID = 29;

   private EmbeddedCacheManager cacheManager;
   private String cacheNameToCreate;
   private String cacheConfigurationName;
   private boolean start;
   private int size;

   private CreateCacheCommand() {
      super(null);
   }
   
   public CreateCacheCommand(String ownerCacheName) {
      super(ownerCacheName);      
   }

   public CreateCacheCommand(String ownerCacheName, String cacheNameToCreate, String cacheConfigurationName) {
      this(ownerCacheName, cacheNameToCreate, cacheConfigurationName, false, 0);
   }

   public CreateCacheCommand(String cacheName, String cacheNameToCreate, String cacheConfigurationName, boolean start, int size) {
      super(cacheName);
      this.cacheNameToCreate = cacheNameToCreate;
      this.cacheConfigurationName = cacheConfigurationName;
      this.start = start;
      this.size = size;
   }



   public void init(EmbeddedCacheManager cacheManager){
      this.cacheManager = cacheManager;
   }

   @Override
   public Object perform(InvocationContext ctx) throws Throwable {
      Configuration cacheConfig = cacheManager.getCacheConfiguration(cacheConfigurationName);
      if (cacheConfig == null) {
         // our sensible default
         cacheConfig = new ConfigurationBuilder().clustering().stateTransfer()
                  .fetchInMemoryState(false).unsafe().unreliableReturnValues(true).expiration()
                  .lifespan(2, TimeUnit.MINUTES).maxIdle(2, TimeUnit.MINUTES)
                  .wakeUpInterval(30, TimeUnit.SECONDS).enableReaper().clustering()
                  .cacheMode(CacheMode.DIST_SYNC).hash().numOwners(2).sync().build();
         cacheManager.defineConfiguration(cacheNameToCreate, cacheConfig);
         log.debugf("Using default tmp cache configuration, defined as ", cacheNameToCreate);
      }
      Cache<Object,Object> cache = cacheManager.getCache(cacheNameToCreate);
      if (start) {
         waitForClusterToForm(cache);
      }

      log.debugf("Defined and started cache %s", cacheNameToCreate);
      return true;
   }

   private void waitForClusterToForm(Cache<Object, Object> cache) throws InterruptedException {
      RpcManager rpcManager = cache.getAdvancedCache().getRpcManager();
      //wait till we see all the expected members
      while (rpcManager.getMembers().size() != size) {
         Thread.sleep(50);
      }
      //now make sure that all the expected members have also seen us
      //do this for 15 secs
      Address localAddress = cacheManager.getTransport().getAddress();
      for (int i = 0; i < 300; i++) {
         cache.getAdvancedCache().withFlags(Flag.SKIP_LOCKING, Flag.FORCE_ASYNCHRONOUS, Flag.SKIP_REMOTE_LOOKUP).put(localAddress, "0");
         boolean clusterFormed = true;
         for (Address a : rpcManager.getMembers()) {
            if (!cache.containsKey(a)) {
               clusterFormed = false;
               break;
            }

         }
         if (clusterFormed) break;
         Thread.sleep(50);
      }
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   public Object[] getParameters() {
      return new Object[] {cacheNameToCreate, cacheConfigurationName, start, size};
   }

   @Override
   public void setParameters(int commandId, Object[] parameters) {
      if (commandId != COMMAND_ID)
         throw new IllegalStateException("Invalid method id " + commandId + " but " +
                                               this.getClass() + " has id " + getCommandId());
      int i = 0;
      cacheNameToCreate = (String) parameters[i++];
      cacheConfigurationName = (String) parameters[i++];
      start = (Boolean) parameters[i++];
      size = (Integer) parameters[i];
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result
               + ((cacheConfigurationName == null) ? 0 : cacheConfigurationName.hashCode());
      result = prime * result + ((cacheNameToCreate == null) ? 0 : cacheNameToCreate.hashCode());
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (obj == null) {
         return false;
      }
      if (!(obj instanceof CreateCacheCommand)) {
         return false;
      }
      CreateCacheCommand other = (CreateCacheCommand) obj;
      if (cacheConfigurationName == null) {
         if (other.cacheConfigurationName != null) {
            return false;
         }
      } else if (!cacheConfigurationName.equals(other.cacheConfigurationName)) {
         return false;
      }
      if (cacheNameToCreate == null) {
         if (other.cacheNameToCreate != null) {
            return false;
         }
      } else if (!cacheNameToCreate.equals(other.cacheNameToCreate)) {
         return false;
      }
      return this.start == other.start && this.size == other.size;
   }

   @Override
   public String toString() {
      return "CreateCacheCommand{" +
            "cacheManager=" + cacheManager +
            ", cacheNameToCreate='" + cacheNameToCreate + '\'' +
            ", cacheConfigurationName='" + cacheConfigurationName + '\'' +
            ", start=" + start + '\'' +
            ", size=" + size +
            '}';
   }

   @Override
   public boolean isReturnValueExpected() {
      return true;
   }

   @Override
   public boolean canBlock() {
      return true;
   }
}

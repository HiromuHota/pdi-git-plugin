/*
 * Copyright 2017 Hitachi America, Ltd., R&D.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pentaho.di.git.spoon.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.pentaho.di.core.KettleClientEnvironment;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.metastore.api.IMetaStore;
import org.pentaho.metastore.api.exceptions.MetaStoreException;
import org.pentaho.metastore.persist.MetaStoreFactory;
import org.pentaho.metastore.stores.memory.MemoryMetaStore;

public class GitRepositoryTest {

  public static final String NAMESPACE = "testnamespace";
  public static final String NAME = "test";
  public static final String DESCRIPTION = "test description";
  public static final String DIRECTORY = "/tmp/test";

  protected IMetaStore metaStore;
  protected GitRepository repo;

  @Before
  public void setUp() throws KettleException {
    KettleClientEnvironment.init();
    metaStore = new MemoryMetaStore();

    repo = new GitRepository();
    repo.setName( NAME );
    repo.setDescription( DESCRIPTION );
    repo.setDirectory( DIRECTORY );
  }

  @Test
  public void testSerialisation() throws MetaStoreException {
    MetaStoreFactory<GitRepository> repoFactory = new MetaStoreFactory<GitRepository>( GitRepository.class, metaStore, NAMESPACE );
    repoFactory.saveElement( repo );

    GitRepository verify = repoFactory.loadElement( NAME );
    assertEquals( NAME, verify.getName() );
    assertEquals( DESCRIPTION, verify.getDescription() );
    assertEquals( DIRECTORY, verify.getDirectory() );
  }
}

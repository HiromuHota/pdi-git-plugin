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
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.subversion.javahl.ISVNRepos;
import org.apache.subversion.javahl.SVNRepos;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;

public class SVNTest {
  private VCS vcs;
  private ISVNRepos admin;
  private File rootServer;
  private File rootClient;
  private String diff;

  @Before
  public void setUp() throws Exception {
    // Set up a central remote repository
    rootServer = File.createTempFile( "svn_test_", "_server" );
    rootServer.delete();
    rootServer.mkdirs();
    admin = new SVNRepos();
    admin.create( rootServer, true, false, null, ISVNRepos.FSFS );

    rootClient = File.createTempFile( "svn_test_", "_client" );
    rootClient.delete();
    vcs = spy( new SVN() );
    doNothing().when( vcs ).showMessageBox( anyString(), anyString() );
    vcs.cloneRepo( rootClient.getPath(), "file://" + rootServer.getPath() );
    vcs.openRepo( rootClient.getPath() );
  }

  @After
  public void tearDown() throws Exception {
    admin.dispose();
    FileUtils.deleteDirectory( rootServer );
    FileUtils.deleteDirectory( rootClient );
  }

  @Test
  public void testCommit() throws Exception {
    UIRepositoryObjectRevisions revisions = vcs.getRevisions();
    File file = new File( rootClient.getPath(), "test.txt" );
    FileUtils.write( file, "Hello World" );
    diff = vcs.diff( IVCS.INDEX, IVCS.WORKINGTREE, "test.txt" );
    assertEquals( "Unversioned", diff );

    vcs.add( "test.txt" );
    diff = vcs.diff( Constants.HEAD, IVCS.INDEX, "test.txt" );
    assertTrue( diff.contains( "nonexistent" ) );
    assertEquals( vcs.getRevisions().size(), revisions.size() + 1 );
    revisions = vcs.getRevisions();
    assertEquals( VCS.WORKINGTREE, revisions.get( 0 ).getName() );

    vcs.resetPath( "test.txt" );
    vcs.add( "test.txt" );

    vcs.commit( "user", "message" );
    vcs.pull();
    assertEquals( revisions.size(), vcs.getRevisions().size() );
    revisions = vcs.getRevisions();
    assertEquals( "1", revisions.get( 0 ).getName() );
    assertEquals( "message", vcs.getCommitMessage( "1" ) );

    List<UIFile> files = vcs.getStagedFiles( "0", "1" );
    assertEquals( "test.txt", files.get( 0 ).getName() );

    InputStream inputStream = vcs.open( "test.txt", "1" );
    StringWriter writer = new StringWriter();
    IOUtils.copy( inputStream, writer, "UTF-8" );
    assertEquals( "Hello World", writer.toString() );

    inputStream = vcs.open( "test.txt", Constants.HEAD );
    writer = new StringWriter();
    IOUtils.copy( inputStream, writer, "UTF-8" );
    assertEquals( "Hello World", writer.toString() );

    inputStream = vcs.open( "test.txt", IVCS.WORKINGTREE );
    writer = new StringWriter();
    IOUtils.copy( inputStream, writer, "UTF-8" );
    assertEquals( "Hello World", writer.toString() );

    diff = vcs.diff( "0", "1", "test.txt" );
    assertTrue( diff.contains( "+Hello World" ) );

    // Test Update to a past commit
    File file2 = new File( rootClient.getPath(), "test2.txt" );
    FileUtils.write( file2, "Hello World" );
    vcs.add( "test2.txt" );
    vcs.commit( "user", "message2" );
    vcs.pull();
    assertEquals( "2", vcs.getRevisions().get( 0 ).getName() );
    vcs.checkout( "1" );
    assertEquals( "1", vcs.getRevisions().get( 0 ).getName() );

    // Test rm
    vcs.pull();
    file.delete();
    vcs.rm( "test.txt" );
    vcs.commit( "user", "Removed test.txt" );
    files = vcs.getStagedFiles( "2", "3" );
    assertEquals( "test.txt", files.get( 0 ).getName() );

    // Test revert
    file2.delete();
    assertFalse( file2.exists() );
    vcs.revertPath( file2.getName() );
    assertTrue( file2.exists() );
  }

  @Test
  public void testPull() throws Exception {
    vcs.pull();
  }

  @Test
  public void testGetRemote() {
    String remote = vcs.getRemote();
    assertNotNull( remote );
  }

  @Test
  public void testGetUnStagedFiles() throws Exception {
    List<UIFile> files;
    // Create a new file
    File file = new File( rootClient.getPath(), "test.txt" );
    FileUtils.write( file, "Hello World" );

    File dir = new File( rootClient.getPath(), "folder" );
    dir.mkdir();

    // New file should be listed in the list of unstaged files
    files = vcs.getUnstagedFiles();
    assertTrue( files.stream().anyMatch( f -> f.getName().equals( "test.txt" ) && f.getChangeType() == ChangeType.ADD ) );
    assertTrue( files.stream().anyMatch( f -> f.getName().equals( "folder" ) && f.getChangeType() == ChangeType.ADD ) );

    // First commit
    vcs.add( "test.txt" );
    vcs.commit( "user", "message" );

    // Test if delete files are listed in the list of staged files
    file.delete();
    files = vcs.getUnstagedFiles();
    assertTrue( files.stream().anyMatch( f -> f.getName().equals( "test.txt" ) && f.getChangeType() == ChangeType.DELETE ) );
  }

  @Test
  public void testCreateDeleteBranchTag() throws Exception {
    vcs.createBranch( "trunk" );
    vcs.checkoutBranch( "trunk" );
    assertEquals( "trunk", vcs.getBranch() );

    vcs.createBranch( "branches/branch1" );
    assertTrue( vcs.getBranches().contains( "branches/branch1" ) );

    vcs.deleteBranch( "branches/branch1", false );
    assertFalse( vcs.getBranches().contains( "branches/branch1" ) );

    vcs.createTag( "tags/tag1" );
    assertTrue( vcs.getTags().contains( "tags/tag1" ) );

    vcs.deleteTag( "tags/tag1" );
    assertFalse( vcs.getTags().contains( "tags/tag1" ) );

    // Create a new file
    File file = new File( rootClient.getPath(), "test.txt" );
    FileUtils.write( file, "Hello World" );
    vcs.add( "test.txt" );
    vcs.createBranch( "branches/branch2" );
    vcs.createTag( "tags/tag2" );
    verify( vcs, times( 2 ) ).showMessageBox( anyString(), anyString() );
    assertFalse( vcs.getBranches().contains( "branches/branch2" ) );
    assertFalse( vcs.getTags().contains( "tags/tag2" ) );
  }

  @Test
  public void testInvalidRepoShouldInvokeError() throws Exception {
    vcs.getBranch();
    verify( vcs, never() ).showMessageBox( BaseMessages.getString( vcs.PKG, "Dialog.Error"),
        BaseMessages.getString( vcs.PKG, "SVN.InvalidRepository" ) );

    FileUtils.deleteDirectory( rootClient );
    vcs.getBranch();
    verify( vcs ).showMessageBox( BaseMessages.getString( vcs.PKG, "Dialog.Error"),
        BaseMessages.getString( vcs.PKG, "SVN.InvalidRepository" ) );
  }
}

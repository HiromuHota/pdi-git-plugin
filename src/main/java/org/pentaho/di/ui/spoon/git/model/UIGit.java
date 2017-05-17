package org.pentaho.di.ui.spoon.git.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.RemoteRemoveCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.UserConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.pentaho.di.repository.ObjectRevision;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.pentaho.ui.xul.XulEventSourceAdapter;

import com.google.common.annotations.VisibleForTesting;

public class UIGit extends XulEventSourceAdapter {

  private Git git;

  public String findGitRepository( String pathname ) {
    Repository repository;
    try {
      repository = ( new FileRepositoryBuilder() ).readEnvironment() // scan environment GIT_* variables
          .findGitDir( new File( pathname ).getParentFile() ) // scan up the file system tree
          .build();
      return repository.getDirectory().getParent();
    } catch ( IOException e ) {
      return null;
    }
  }

  public boolean isOpen() {
    return git != null;
  }

  @VisibleForTesting
  void setGit( Git git ) {
    this.git = git;
  }

  public String getAuthorName() {
    Config config = git.getRepository().getConfig();
    return config.get( UserConfig.KEY ).getAuthorName()
        + " <" + config.get( UserConfig.KEY ).getAuthorEmail() + ">";
  }

  public String getBranch() {
    try {
      return git.getRepository().getBranch();
    } catch ( Exception e ) {
      return "";
    }
  }

  public String getFullBranch() throws IOException {
    return git.getRepository().getFullBranch();
  }

  public String getRemote() {
    try {
      StoredConfig config = git.getRepository().getConfig();
      RemoteConfig remoteConfig = new RemoteConfig( config, Constants.DEFAULT_REMOTE_NAME );
      return remoteConfig.getURIs().iterator().next().toString();
    } catch ( Exception e ) {
      return "";
    }
  }

  public RemoteConfig addRemote( String s ) throws Exception {
    // Make sure you have only one URI for push
    removeRemote();

    URIish uri = new URIish( s );
    RemoteAddCommand cmd = git.remoteAdd();
    cmd.setName( Constants.DEFAULT_REMOTE_NAME );
    cmd.setUri( uri );
    firePropertyChange( "remote", null, s );
    return cmd.call();
  }

  public RemoteConfig removeRemote() throws GitAPIException {
    RemoteRemoveCommand cmd = git.remoteRemove();
    cmd.setName( Constants.DEFAULT_REMOTE_NAME );
    firePropertyChange( "remote", null, "" );
    return cmd.call();
  }

  public boolean hasRemote() throws IOException {
    StoredConfig config = git.getRepository().getConfig();
    Set<String> remotes = config.getSubsections( "remote" );
    return remotes.contains( Constants.DEFAULT_REMOTE_NAME );
  }

  public RevCommit commit( String name, String email, String message ) throws Exception {
    return git.commit().setAuthor( name, email ).setMessage( message ).call();
  }

  public UIRepositoryObjectRevisions getRevisionObjects() {
    UIRepositoryObjectRevisions revisions = new UIRepositoryObjectRevisions();
    try {
      Iterable<RevCommit> iterable = git.log().call();
      for ( RevCommit commit : iterable ) {
        PurObjectRevision rev = new PurObjectRevision(
          commit.getName(),
          commit.getAuthorIdent().getName(),
          commit.getAuthorIdent().getWhen(),
          commit.getShortMessage() );
        revisions.add( new UIRepositoryObjectRevision( (ObjectRevision) rev ) );
      }
    } catch ( Exception e ) {

    }
    return revisions;
  }

  public List<UIFile> getUnstagedObjects() throws Exception {
    Set<String> files = new HashSet<String>();
    try {
      Status status = git.status().call();
      files.addAll( status.getModified() );
      files.addAll( status.getUntracked() );
    } catch ( Exception e ) {
    }
    return getObjects( files );
  }

  public List<UIFile> getStagedObjects() throws Exception {
    Set<String> files = new HashSet<String>();
    try {
      Status status = git.status().call();
      files.addAll( status.getAdded() );
      files.addAll( status.getChanged() );
    } catch ( Exception e ) {
    }
    return getObjects( files );
  }

  public boolean hasStagedObjects() throws Exception {
    return getStagedObjects().size() != 0;
  }

  private List<UIFile> getObjects( Set<String> files ) throws Exception {
    List<UIFile> objs = new ArrayList<UIFile>();
    for ( String file : files ) {
      UIFile obj;
      if ( file.endsWith( ".ktr" ) ) {
        obj = new UITransformation();
      } else if ( file.endsWith( ".kjb" ) ) {
        obj = new UIJob();
      } else {
        obj = new UIFile();
      }
      obj.setName( file );
      objs.add( obj );
    }
    return objs;
  }

  public void initGit( String baseDirectory ) throws IllegalStateException, GitAPIException {
    git = Git.init().setDirectory( new File( baseDirectory ) ).call();
  }

  public void openGit( String baseDirectory ) throws IOException {
    git = Git.open( new File( baseDirectory ) );
  }

  public void closeGit() {
    git.close();
    git = null;
  }

  public DirCache add( String filepattern ) throws Exception {
    return git.add().addFilepattern( filepattern ).call();
  }

  public Ref reset( String path ) throws Exception {
    return git.reset().addPath( path ).call();
  }

  /**
   * Equivalent of <tt>git fetch; git merge --ff</tt>
   *
   * @return PullResult
   * @throws Exception
   * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-pull.html">Git documentation about Pull</a>
   */
  public PullResult pull() throws Exception {
    return git.pull().call();
  }

  public Ref resetHard() throws Exception {
    return git.reset().setMode( ResetType.HARD ).call();
  }

  public Iterable<PushResult> push() throws Exception {
    return git.push().call();
  }

  public String diff() throws Exception {
    OutputStream out = new ByteArrayOutputStream();
    git.diff().setOutputStream( out ).call();
    return out.toString();
  }

  public String diff( String file, boolean isCached ) throws Exception {
    OutputStream out = new ByteArrayOutputStream();
    git.diff().setOutputStream( out )
      .setPathFilter( PathFilter.create( file ) )
      .setCached( isCached )
      .call();
    return out.toString();
  }

  public String show( String commitId ) throws Exception {
    AbstractTreeIterator newTree = getTreeIterator( commitId );
    AbstractTreeIterator oldTree = getTreeIterator( commitId + "^" );

    OutputStream out = new ByteArrayOutputStream();
    git.diff().setOutputStream( out )
      .setOldTree( oldTree )
      .setNewTree( newTree )
      .call();
    return out.toString();
  }

  private AbstractTreeIterator getTreeIterator( String name ) throws IOException {
    final ObjectId id = git.getRepository().resolve( name );
    if ( id == null ) {
      return new EmptyTreeIterator();
    }
    final CanonicalTreeParser p = new CanonicalTreeParser();
    try ( ObjectReader or = git.getRepository().newObjectReader();
        RevWalk rw = new RevWalk( git.getRepository() ) ) {
      p.reset( or, rw.parseTree( id ) );
    }

    return p;
  }
}

package org.pentaho.di.ui.spoon.git.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.RemoteRemoveCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.UserConfig;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FileUtils;
import org.pentaho.di.repository.ObjectRevision;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.pentaho.ui.xul.XulEventSourceAdapter;

import com.google.common.annotations.VisibleForTesting;

public class UIGit extends XulEventSourceAdapter {

  static {
    /**
     * Use Apache HTTP Client instead of Sun HTTP client.
     * This resolves the issue that Git commands (e.g., push, clone) via http(s) do not work in EE.
     * This issue is caused by the fact that weka plugins (namely, knowledge-flow, weka-forecasting, and weka-scoring)
     * calls java.net.Authenticator.setDefault().
     * See here https://bugs.eclipse.org/bugs/show_bug.cgi?id=296201 for more details.
     */
    HttpTransport.setConnectionFactory( new HttpClientConnectionFactory() );
  }

  private Git git;

  /**
   * Find a Git repository by scanning up the file system tree
   * @param pathname of a Kettle file
   * @return
   */
  public String findGitRepository( String pathname ) {
    File parentFile = null;
    try {
      parentFile = new File( new URI( pathname ) ).getParentFile();
    } catch ( URISyntaxException e1 ) {
      e1.printStackTrace();
    }
    Repository repository;
    try {
      repository = ( new FileRepositoryBuilder() ).readEnvironment() // scan environment GIT_* variables
          .findGitDir( parentFile ) // scan up the file system tree
          .build();
      return repository.getDirectory().getParent();
    } catch ( IOException e ) {
      return null;
    } catch ( IllegalArgumentException e ) {
      if ( e.getMessage().equals( "One of setGitDir or setWorkTree must be called." ) ) {
        // git repository not found
        return parentFile.getPath();
      } else {
        return null;
      }
    }
  }

  /**
   * If git is null or not
   * @return
   */
  public boolean isOpen() {
    return git != null;
  }

  @VisibleForTesting
  void setGit( Git git ) {
    this.git = git;
  }

  /**
   * Get the author name as defined in the .git
   * @return
   */
  public String getAuthorName() {
    Config config = git.getRepository().getConfig();
    return config.get( UserConfig.KEY ).getAuthorName()
        + " <" + config.get( UserConfig.KEY ).getAuthorEmail() + ">";
  }

  /**
   * Get the author name for a commit
   * @param commitId
   * @return
   * @throws Exception
   */
  public String getAuthorName( String commitId ) throws Exception {
    final ObjectId id = git.getRepository().resolve( commitId );
    try ( RevWalk rw = new RevWalk( git.getRepository() ) ) {
      RevObject obj = rw.parseAny( id );
      RevCommit commit = (RevCommit) obj;
      PersonIdent author = commit.getAuthorIdent();
      final StringBuilder r = new StringBuilder();
      r.append( author.getName() );
      r.append( " <" ); //$NON-NLS-1$
      r.append( author.getEmailAddress() );
      r.append( ">" ); //$NON-NLS-1$
      return r.toString();
    }
  }

  public String getCommitMessage( String commitId ) throws Exception {
    final ObjectId id = git.getRepository().resolve( commitId );
    try ( RevWalk rw = new RevWalk( git.getRepository() ) ) {
      RevObject obj = rw.parseAny( id );
      RevCommit commit = (RevCommit) obj;
      return commit.getFullMessage();
    }
  }

  /**
   * Get SHA-1 commit Id
   * @param revstr: (e.g., HEAD, SHA-1)
   * @return
   * @throws Exception
   */
  public String getCommitId( String revstr ) throws Exception {
    final ObjectId id = git.getRepository().resolve( revstr );
    return id.getName();
  }

  /**
   * Get the current branch
   * @return Current branch
   */
  public String getBranch() {
    try {
      return git.getRepository().getBranch();
    } catch ( Exception e ) {
      return "";
    }
  }

  /**
   * Get a list of branches
   * @return
   */
  public List<String> getBranches() {
    List<String> branches = new ArrayList<String>();
    try {
      for ( Ref ref : git.branchList().call() ) {
        branches.add( Repository.shortenRefName( ref.getName() ) );
      }
    } catch ( Exception e ) {
      e.printStackTrace();
    }
    return branches;
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

  public boolean hasRemote() {
    StoredConfig config = git.getRepository().getConfig();
    Set<String> remotes = config.getSubsections( ConfigConstants.CONFIG_REMOTE_SECTION );
    return remotes.contains( Constants.DEFAULT_REMOTE_NAME );
  }

  public RevCommit commit( PersonIdent author, String message ) throws Exception {
    return git.commit().setAuthor( author ).setMessage( message ).call();
  }

  public UIRepositoryObjectRevisions getRevisions() throws Exception {
    UIRepositoryObjectRevisions revisions = new UIRepositoryObjectRevisions();
    try {
      if ( !git.status().call().isClean() ) {
        PurObjectRevision rev = new PurObjectRevision(
            "",
            "*",
            new Date(),
            " // WIP" );
        revisions.add( new UIRepositoryObjectRevision( (ObjectRevision) rev ) );
      }
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
      // Do nothing
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
      // Do nothing
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
      // Do nothing
    }
    return getObjects( files );
  }

  public boolean hasStagedObjects() throws Exception {
    return getStagedObjects().size() != 0;
  }

  private List<UIFile> getObjects( Set<String> files ) throws Exception {
    List<UIFile> objs = new ArrayList<UIFile>();
    for ( String file : files ) {
      UIFile obj = new UIFile( file );
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

  public PullResult pull( String username, String password ) throws Exception {
    PullCommand cmd = git.pull();
    CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider( username, password );
    cmd.setCredentialsProvider( credentialsProvider );
    return cmd.call();
  }

  public Ref resetHard() throws Exception {
    return git.reset().setMode( ResetType.HARD ).call();
  }

  public Iterable<PushResult> push() throws Exception {
    return git.push().call();
  }

  public Iterable<PushResult> push( String username, String password ) throws Exception {
    PushCommand cmd = git.push();
    CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider( username, password );
    cmd.setCredentialsProvider( credentialsProvider );
    return cmd.call();
  }

  public String diff( String file, boolean isCached ) throws Exception {
    OutputStream out = new ByteArrayOutputStream();
    git.diff().setOutputStream( out )
      .setPathFilter( PathFilter.create( file ) )
      .setCached( isCached )
      .call();
    return out.toString();
  }

  /**
   * Show diff for a commit
   * @param commitId
   * @return
   * @throws Exception
   */
  public String show( String commitId ) throws Exception {
    RevTree newTree = null;
    RevTree oldTree = null;
    final ObjectId id = git.getRepository().resolve( commitId );
    try ( RevWalk rw = new RevWalk( git.getRepository() ) ) {
      RevObject obj = rw.parseAny( id );
      RevCommit commit = (RevCommit) obj;
      newTree = commit.getTree();
      if ( commit.getParentCount() != 0 ) {
        RevCommit parentCommit = rw.parseCommit( commit.getParent( 0 ).getId() );
        oldTree = parentCommit.getTree();
      }
    }

    OutputStream out = new ByteArrayOutputStream();
    DiffFormatter formatter = new DiffFormatter( out );
    formatter.setRepository( git.getRepository() );
    formatter.setDetectRenames( true );
    formatter.format( oldTree, newTree );
    formatter.close();
    return out.toString();
  }

  public ObjectStream open( String file, String commitId ) throws Exception {
    ObjectId id = git.getRepository().resolve( commitId );
    try ( RevWalk rw = new RevWalk( git.getRepository() ) ) {
      RevObject obj = rw.parseAny( id );
      RevCommit commit = (RevCommit) obj;
      RevTree tree = commit.getTree();
      try ( TreeWalk tw = new TreeWalk( git.getRepository() ) ) {
        tw.addTree( tree );
        tw.setFilter( PathFilter.create( file ) );
        tw.setRecursive( true );
        tw.next();
        ObjectLoader loader = git.getRepository().open( tw.getObjectId( 0 ) );
        return loader.openStream();
      }
    }
  }

  public static Git cloneRepo( String directory, String uri ) throws Exception {
    CloneCommand cmd = Git.cloneRepository();
    cmd.setDirectory( new File( directory ) );
    cmd.setURI( uri );
    try {
      Git git = cmd.call();
      return git;
    } catch ( Exception e ) {
      try {
        FileUtils.delete( new File( directory ), FileUtils.RECURSIVE );
        throw e;
      } catch ( IOException e1 ) {
        e1.printStackTrace();
      }
      return null;
    }
  }

  public static Git cloneRepo( String directory, String uri, String username, String password ) throws Exception {
    CloneCommand cmd = Git.cloneRepository();
    cmd.setDirectory( new File( directory ) );
    cmd.setURI( uri );
    CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider( username, password );
    cmd.setCredentialsProvider( credentialsProvider );
    return cmd.call();
  }

  /**
   * Checkout a branch or commit
   * @param name
   * @throws Exception
   */
  public void checkout( String name ) throws Exception {
    git.checkout().setName( name ).call();
  }

  public Ref createBranch( String value ) throws Exception {
    return git.branchCreate().setName( value ).call();
  }

  public List<String> deleteBranch( String name, boolean force ) throws Exception {
    return git.branchDelete()
        .setBranchNames( name )
        .setForce( force )
        .call();
  }

  public MergeResult mergeBranch( String value ) throws Exception {
    return mergeBranch( value, MergeStrategy.RECURSIVE.getName() );
  }

  public MergeResult mergeBranch( String value, String mergeStrategy ) throws Exception {
    Ref ref = git.getRepository().exactRef( Constants.R_HEADS + value );
    return git.merge()
        .include( ref )
        .setStrategy( MergeStrategy.get( mergeStrategy ) )
        .call();
  }

  public Ref checkoutPath( String path ) throws Exception {
    return git.checkout().addPath( path ).call();
  }
}

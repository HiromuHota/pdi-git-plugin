package org.pentaho.di.git.spoon.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.RemoteRemoveCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
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
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
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

  public static final String WORKINGTREE = "WORKINGTREE";
  public static final String INDEX = "INDEX";
  private Git git;
  private String directory;

  public String getDirectory() {
    return directory;
  }

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
      Ref head = git.getRepository().exactRef( Constants.HEAD );
      String branch = git.getRepository().getBranch();
      if ( head.getLeaf().getName().equals( Constants.HEAD ) ) { // if detached
        return Constants.HEAD + " detached at " + branch.substring( 0, 7 );
      } else {
        return branch;
      }
    } catch ( Exception e ) {
      return "";
    }
  }

  /**
   * Get a list of local branches
   * @return
   */
  public List<String> getBranches() {
    return getBranches( null );
  }

  /**
   * Get a list of branches based on mode
   * @param mode
   * @return
   */
  public List<String> getBranches( ListMode mode ) {
    try {
      return git.branchList().setListMode( mode ).call().stream()
        .filter( ref -> !ref.getName().endsWith( Constants.HEAD ) )
        .map( ref -> Repository.shortenRefName( ref.getName() ) )
        .collect( Collectors.toList() );
    } catch ( Exception e ) {
      e.printStackTrace();
    }
    return null;
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
    List<UIFile> files = new ArrayList<UIFile>();
    try {
      Status status = git.status().call();
      status.getUntracked().forEach( name -> {
        files.add( new UIFile( name, ChangeType.ADD ) );
      } );
      status.getModified().forEach( name -> {
        files.add( new UIFile( name, ChangeType.MODIFY ) );
      } );
      status.getMissing().forEach( name -> {
        files.add( new UIFile( name, ChangeType.DELETE ) );
      } );
    } catch ( Exception e ) {
      // Do nothing
    }
    return files;
  }

  public List<UIFile> getStagedObjects( String commitId ) throws Exception {
    List<UIFile> files = new ArrayList<UIFile>();
    try {
      if ( commitId.equals( "" ) ) {
        Status status = git.status().call();
        status.getAdded().forEach( name -> {
          files.add( new UIFile( name, ChangeType.ADD ) );
        } );
        status.getChanged().forEach( name -> {
          files.add( new UIFile( name, ChangeType.MODIFY ) );
        } );
        status.getRemoved().forEach( name -> {
          files.add( new UIFile( name, ChangeType.DELETE ) );
        } );
      } else {
        List<DiffEntry> diffs = getDiffCommand( commitId, commitId + "^" )
          .setShowNameAndStatusOnly( true )
          .call();
        RenameDetector rd = new RenameDetector( git.getRepository() );
        rd.addAll( diffs );
        diffs = rd.compute();
        diffs.forEach( diff -> {
          files.add( new UIFile( diff.getChangeType() == ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath(),
              diff.getChangeType() ) );
        } );
      }
    } catch ( Exception e ) {
      // Do nothing
    }
    return files;
  }

  public boolean hasStagedObjects() throws Exception {
    return getStagedObjects( "" ).size() != 0;
  }

  public void initGit( String baseDirectory ) throws IllegalStateException, GitAPIException {
    git = Git.init().setDirectory( new File( baseDirectory ) ).call();
    directory = baseDirectory;
  }

  public void openGit( String baseDirectory ) throws IOException {
    git = Git.open( new File( baseDirectory ) );
    directory = baseDirectory;
  }

  public void closeGit() {
    git.close();
    git = null;
  }

  public DirCache add( String filepattern ) throws Exception {
    return git.add().addFilepattern( filepattern ).call();
  }

  public DirCache rm( String filepattern ) throws NoFilepatternException, GitAPIException {
    return git.rm().addFilepattern( filepattern ).call();
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
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    git.diff().setOutputStream( out )
      .setPathFilter( PathFilter.create( file ) )
      .setCached( isCached )
      .call();
    return out.toString( "UTF-8" );
  }

  public String diff( String newCommitId, String oldCommitId ) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    getDiffCommand( newCommitId, oldCommitId )
      .setOutputStream( out )
      .call();
    return out.toString( "UTF-8" );
  }

  public String diff( String file, String newCommitId, String oldCommitId ) throws Exception {
    // DiffFormatter does not detect renames with path filters on
//    DiffFormatter formatter = new DiffFormatter( out );
//    formatter.setRepository( git.getRepository() );
//    formatter.setDetectRenames( true );
//    formatter.setPathFilter( PathFilter.create( file ) );
//    formatter.format( getTreeIterator( oldCommitId ), getTreeIterator( newCommitId ) );
//    formatter.close();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    List<DiffEntry> diffs = getDiffCommand( newCommitId, oldCommitId )
        .setShowNameAndStatusOnly( true ).call();

    // Detect renames
    RenameDetector rd = new RenameDetector( git.getRepository() );
    rd.addAll( diffs );
    diffs = rd.compute();

    // Write out to String after filtering
    DiffFormatter formatter = new DiffFormatter( out );
    formatter.setRepository( git.getRepository() );
    formatter.format(
        diffs.stream()
        .filter( diff -> diff.getNewPath().equals( file ) )
        .collect( Collectors.toList() ) );
    formatter.close();
    return out.toString( "UTF-8" );
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

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DiffFormatter formatter = new DiffFormatter( out );
    formatter.setRepository( git.getRepository() );
    formatter.setDetectRenames( true );
    formatter.format( oldTree, newTree );
    formatter.close();
    return out.toString( "UTF-8" );
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

  private DiffCommand getDiffCommand( String newCommitId, String oldCommitId ) throws Exception {
    return git.diff()
      .setOldTree( getTreeIterator( oldCommitId ) )
      .setNewTree( getTreeIterator( newCommitId ) );
  }

  private AbstractTreeIterator getTreeIterator( String commitId ) throws Exception {
    AbstractTreeIterator treeIterator;
    if ( commitId.equals( WORKINGTREE ) ) {
      treeIterator = new FileTreeIterator( git.getRepository() );
    } else if ( commitId.equals( INDEX ) ) {
      treeIterator = new DirCacheIterator( git.getRepository().readDirCache() );
    } else {
      treeIterator = new CanonicalTreeParser();
      try ( RevWalk rw = new RevWalk( git.getRepository() ) ) {
        RevTree tree = rw.parseTree( git.getRepository().resolve( commitId ) );
        try ( ObjectReader reader = git.getRepository().newObjectReader() ) {
          ( (CanonicalTreeParser) treeIterator ).reset( reader, tree.getId() );
        }
      }
    }
    return treeIterator;
  }
}

package org.pentaho.di.git.spoon.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
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
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.SystemReader;
import org.pentaho.di.core.Const;
import org.pentaho.di.repository.ObjectRevision;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;

import com.google.common.annotations.VisibleForTesting;

public class UIGit implements VCS {

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
  private String directory;
  private CredentialsProvider credentialsProvider;

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getDirectory()
   */
  @Override
  public String getDirectory() {
    return directory;
  }

  @VisibleForTesting
  void setDirectory( String directory ) {
    this.directory = directory;
  }

  @VisibleForTesting
  void setGit( Git git ) {
    this.git = git;
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getAuthorName()
   */
  @Override
  public String getAuthorName() {
    Config config = git.getRepository().getConfig();
    return config.get( UserConfig.KEY ).getAuthorName()
        + " <" + config.get( UserConfig.KEY ).getAuthorEmail() + ">";
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getAuthorName(java.lang.String)
   */
  @Override
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

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getCommitMessage(java.lang.String)
   */
  @Override
  public String getCommitMessage( String commitId ) throws Exception {
    final ObjectId id = git.getRepository().resolve( commitId );
    try ( RevWalk rw = new RevWalk( git.getRepository() ) ) {
      RevObject obj = rw.parseAny( id );
      RevCommit commit = (RevCommit) obj;
      return commit.getFullMessage();
    }
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getCommitId(java.lang.String)
   */
  @Override
  public String getCommitId( String revstr ) throws Exception {
    final ObjectId id = git.getRepository().resolve( revstr );
    if ( id == null ) {
      return null;
    } else {
      return id.getName();
    }
  }

  @Override
  public String getParentCommitId( String revstr ) throws Exception {
    return getCommitId( revstr + "~" );
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getBranch()
   */
  @Override
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

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getLocalBranches()
   */
  @Override
  public List<String> getLocalBranches() {
    return getBranches( null );
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getBranches()
   */
  @Override
  public List<String> getBranches() {
    return getBranches( ListMode.ALL );
  }

  /**
   * Get a list of branches based on mode
   * @param mode
   * @return
   */
  private List<String> getBranches( ListMode mode ) {
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

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getRemote()
   */
  @Override
  public String getRemote() {
    try {
      StoredConfig config = git.getRepository().getConfig();
      RemoteConfig remoteConfig = new RemoteConfig( config, Constants.DEFAULT_REMOTE_NAME );
      return remoteConfig.getURIs().iterator().next().toString();
    } catch ( Exception e ) {
      return "";
    }
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#addRemote(java.lang.String)
   */
  @Override
  public void addRemote( String s ) throws Exception {
    // Make sure you have only one URI for push
    removeRemote();

    URIish uri = new URIish( s );
    RemoteAddCommand cmd = git.remoteAdd();
    cmd.setName( Constants.DEFAULT_REMOTE_NAME );
    cmd.setUri( uri );
    cmd.call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#removeRemote()
   */
  @Override
  public void removeRemote() throws Exception {
    RemoteRemoveCommand cmd = git.remoteRemove();
    cmd.setName( Constants.DEFAULT_REMOTE_NAME );
    cmd.call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#hasRemote()
   */
  @Override
  public boolean hasRemote() {
    StoredConfig config = git.getRepository().getConfig();
    Set<String> remotes = config.getSubsections( ConfigConstants.CONFIG_REMOTE_SECTION );
    return remotes.contains( Constants.DEFAULT_REMOTE_NAME );
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#commit(java.lang.String, java.lang.String)
   */
  @Override
  public void commit( String authorName, String message ) throws Exception {
    PersonIdent author = RawParseUtils.parsePersonIdent( authorName );
    // Set the local time
    PersonIdent author2 = new PersonIdent( author.getName(), author.getEmailAddress(),
        SystemReader.getInstance().getCurrentTime(),
        SystemReader.getInstance().getTimezone( SystemReader.getInstance().getCurrentTime() ) );
    git.commit().setAuthor( author2 ).setMessage( message ).call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getRevisions()
   */
  @Override
  public UIRepositoryObjectRevisions getRevisions() throws Exception {
    UIRepositoryObjectRevisions revisions = new UIRepositoryObjectRevisions();
    try {
      if ( !git.status().call().isClean() ) {
        PurObjectRevision rev = new PurObjectRevision(
            WORKINGTREE,
            "*",
            new Date(),
            " // " + VCS.WORKINGTREE );
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

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getUnstagedFiles()
   */
  @Override
  public List<UIFile> getUnstagedFiles() throws Exception {
    List<UIFile> files = new ArrayList<UIFile>();
    Status status = git.status().call();
    status.getUntracked().forEach( name -> {
      files.add( new UIFile( name, ChangeType.ADD, false ) );
    } );
    status.getModified().forEach( name -> {
      files.add( new UIFile( name, ChangeType.MODIFY, false ) );
    } );
    status.getMissing().forEach( name -> {
      files.add( new UIFile( name, ChangeType.DELETE, false ) );
    } );
    return files;
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getStagedFiles()
   */
  @Override
  public List<UIFile> getStagedFiles() throws Exception {
    List<UIFile> files = new ArrayList<UIFile>();
    Status status = git.status().call();
    status.getAdded().forEach( name -> {
      files.add( new UIFile( name, ChangeType.ADD, true ) );
    } );
    status.getChanged().forEach( name -> {
      files.add( new UIFile( name, ChangeType.MODIFY, true ) );
    } );
    status.getRemoved().forEach( name -> {
      files.add( new UIFile( name, ChangeType.DELETE, true ) );
    } );
    return files;
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#getStagedFiles(java.lang.String, java.lang.String)
   */
  @Override
  public List<UIFile> getStagedFiles( String oldCommitId, String newCommitId ) throws Exception {
    List<UIFile> files = new ArrayList<UIFile>();
    List<DiffEntry> diffs = getDiffCommand( oldCommitId, newCommitId )
      .setShowNameAndStatusOnly( true )
      .call();
    RenameDetector rd = new RenameDetector( git.getRepository() );
    rd.addAll( diffs );
    diffs = rd.compute();
    diffs.forEach( diff -> {
      files.add( new UIFile( diff.getChangeType() == ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath(),
        diff.getChangeType(), false ) );
    } );
    return files;
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#hasStagedFiles()
   */
  @Override
  public boolean hasStagedFiles() throws Exception {
    return !getStagedFiles().isEmpty();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#initRepo(java.lang.String)
   */
  @Override
  public void initRepo( String baseDirectory ) throws Exception {
    git = Git.init().setDirectory( new File( baseDirectory ) ).call();
    directory = baseDirectory;
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#openRepo(java.lang.String)
   */
  @Override
  public void openRepo( String baseDirectory ) throws Exception {
    git = Git.open( new File( baseDirectory ) );
    directory = baseDirectory;
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#closeRepo()
   */
  @Override
  public void closeRepo() {
    git.close();
    git = null;
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#add(java.lang.String)
   */
  @Override
  public void add( String filepattern ) throws Exception {
    git.add().addFilepattern( filepattern ).call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#rm(java.lang.String)
   */
  @Override
  public void rm( String filepattern ) throws Exception {
    git.rm().addFilepattern( filepattern ).call();
  }

  /**
   * Reset to a commit (mixed)
   * @see org.pentaho.di.git.spoon.model.VCS#reset(java.lang.String)
   */
  @Override
  public void reset( String name ) throws Exception {
    git.reset().setRef( name ).call();
  }

  /**
   * Reset a file to a commit (mixed)
   */
  @Override
  public void reset( String name, String path ) throws Exception {
    git.reset().setRef( name ).addPath( path ).call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#resetHard()
   */
  @Override
  public void resetHard() throws Exception {
    git.reset().setMode( ResetType.HARD ).call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#pull()
   */
  @Override
  public PullResult pull() throws Exception {
    PullCommand cmd = git.pull();
    cmd.setCredentialsProvider( credentialsProvider );
    return cmd.call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#push()
   */
  @Override
  public Iterable<PushResult> push() throws Exception {
    return push( null );
  }

  @Override
  public Iterable<PushResult> push( String name ) throws Exception {
    PushCommand cmd = git.push();
    cmd.setCredentialsProvider( credentialsProvider );
    if ( name != null ) {
      cmd.setRefSpecs( new RefSpec( name ) );
    }
    return cmd.call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#show(java.lang.String)
   */
  @Override
  public String show( String commitId ) throws Exception {
    if ( commitId.equals( WORKINGTREE ) ) {
      return diff( Constants.HEAD, WORKINGTREE );
    } else {
      return diff( commitId + "^", commitId );
    }
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#diff(java.lang.String, java.lang.String)
   */
  @Override
  public String diff( String oldCommitId, String newCommitId ) throws Exception {
    return diff( oldCommitId, newCommitId, null );
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#diff(java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public String diff( String oldCommitId, String newCommitId, String file ) throws Exception {
    // DiffFormatter does not detect renames with path filters on
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DiffFormatter formatter = new DiffFormatter( out );
    formatter.setRepository( git.getRepository() );
    formatter.setDetectRenames( true );
    List<DiffEntry> diffs = formatter.scan( getTreeIterator( oldCommitId ), getTreeIterator( newCommitId ) );

    if ( file == null ) {
      formatter.format( diffs );
    } else {
      formatter.format(
          diffs.stream()
          .filter( diff -> diff.getNewPath().equals( file ) )
          .collect( Collectors.toList() ) );
    }
    formatter.close();
    return out.toString( "UTF-8" );
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#open(java.lang.String, java.lang.String)
   */
  @Override
  public InputStream open( String file, String commitId ) throws Exception {
    if ( commitId.equals( WORKINGTREE ) ) {
      String baseDirectory = getDirectory();
      String filePath = baseDirectory + Const.FILE_SEPARATOR + file;
      return new FileInputStream( new File( filePath ) );
    }
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

  public static void cloneRepo( String directory, String uri ) throws Exception {
    CloneCommand cmd = Git.cloneRepository();
    cmd.setDirectory( new File( directory ) );
    cmd.setURI( uri );
    try {
      Git git = cmd.call();
      git.close();
    } catch ( Exception e ) {
      try {
        FileUtils.delete( new File( directory ), FileUtils.RECURSIVE );
        throw e;
      } catch ( IOException e1 ) {
        e1.printStackTrace();
      }
    }
  }

  public static void cloneRepo( String directory, String uri, String username, String password ) throws Exception {
    CloneCommand cmd = Git.cloneRepository();
    cmd.setDirectory( new File( directory ) );
    cmd.setURI( uri );
    CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider( username, password );
    cmd.setCredentialsProvider( credentialsProvider );
    Git git = cmd.call();
    git.close();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#checkout(java.lang.String)
   */
  @Override
  public void checkout( String name ) throws Exception {
    git.checkout().setName( name ).call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#checkout(java.lang.String, java.lang.String)
   */
  @Override
  public void checkout( String name, String path ) throws Exception {
    git.checkout().setName( name ).addPath( path ).call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#createBranch(java.lang.String)
   */
  @Override
  public void createBranch( String value ) throws Exception {
    git.branchCreate().setName( value ).call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#deleteBranch(java.lang.String, boolean)
   */
  @Override
  public void deleteBranch( String name, boolean force ) throws Exception {
    git.branchDelete()
        .setBranchNames( getExpandedName( name, VCS.TYPE_BRANCH ) )
        .setForce( force )
        .call();
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#mergeBranch(java.lang.String)
   */
  @Override
  public MergeResult mergeBranch( String value ) throws Exception {
    return mergeBranch( value, MergeStrategy.RECURSIVE.getName() );
  }

  /* (non-Javadoc)
   * @see org.pentaho.di.git.spoon.model.VCS#mergeBranch(java.lang.String, java.lang.String)
   */
  @Override
  public MergeResult mergeBranch( String value, String mergeStrategy ) throws Exception {
    Ref ref = git.getRepository().exactRef( Constants.R_HEADS + value );
    return git.merge()
        .include( ref )
        .setStrategy( MergeStrategy.get( mergeStrategy ) )
        .call();
  }

  private DiffCommand getDiffCommand( String oldCommitId, String newCommitId ) throws Exception {
    return git.diff()
      .setOldTree( getTreeIterator( oldCommitId ) )
      .setNewTree( getTreeIterator( newCommitId ) );
  }

  private AbstractTreeIterator getTreeIterator( String commitId ) throws Exception {
    if ( commitId == null ) {
      return new EmptyTreeIterator();
    }
    if ( commitId.equals( WORKINGTREE ) ) {
      return new FileTreeIterator( git.getRepository() );
    } else if ( commitId.equals( INDEX ) ) {
      return new DirCacheIterator( git.getRepository().readDirCache() );
    } else {
      ObjectId id = git.getRepository().resolve( commitId );
      if ( id == null ) { // commitId does not exist
        return new EmptyTreeIterator();
      } else {
        CanonicalTreeParser treeIterator = new CanonicalTreeParser();
        try ( RevWalk rw = new RevWalk( git.getRepository() ) ) {
          RevTree tree = rw.parseTree( id );
          try ( ObjectReader reader = git.getRepository().newObjectReader() ) {
            treeIterator.reset( reader, tree.getId() );
          }
        }
        return treeIterator;
      }
    }
  }

  @Override
  public String getShortenedName( String name, String type ) {
    if ( name.length() == Constants.OBJECT_ID_STRING_LENGTH ) {
      return name.substring( 0, 7 );
    } else {
      return Repository.shortenRefName( name );
    }
  }

  @Override
  public boolean isClean() {
    try {
      return git.status().call().isClean();
    } catch ( Exception e ) {
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public List<String> getTags() {
    try {
      return git.tagList().call()
        .stream().map( ref -> Repository.shortenRefName( ref.getName() ) )
        .collect( Collectors.toList() );
    } catch ( GitAPIException e ) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public void createTag( String name ) throws Exception {
    git.tag().setName( name ).call();
  }

  @Override
  public void deleteTag( String name ) throws Exception {
    git.tagDelete().setTags( getExpandedName( name, VCS.TYPE_TAG ) ).call();
  }

  @Override
  public String getExpandedName( String name, String type ) throws Exception {
    switch ( type ) {
    case TYPE_TAG:
      return Constants.R_TAGS + name;
    case TYPE_BRANCH:
      try {
        return git.getRepository().findRef( Constants.R_HEADS + name ).getName();
      } catch ( Exception e ) {
        return git.getRepository().findRef( Constants.R_REMOTES + name ).getName();
      }
    default:
      return getCommitId( name );
    }
  }

  @Override
  public void setCredential( String username, String password ) {
    credentialsProvider = new UsernamePasswordCredentialsProvider( username, password );
  }
}

package org.pentaho.di.git.spoon.model;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.subversion.javahl.ClientException;
import org.apache.subversion.javahl.types.Depth;
import org.apache.subversion.javahl.types.Revision;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectRevision;
import org.pentaho.di.repository.pur.PurObjectRevision;
import org.pentaho.di.ui.core.dialog.EnterSelectionDialog;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevision;
import org.pentaho.di.ui.repository.pur.repositoryexplorer.model.UIRepositoryObjectRevisions;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.ISVNDirEntry;
import org.tigris.subversion.svnclientadapter.ISVNInfo;
import org.tigris.subversion.svnclientadapter.ISVNLogMessage;
import org.tigris.subversion.svnclientadapter.ISVNStatus;
import org.tigris.subversion.svnclientadapter.SVNClientAdapterFactory;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNInfoUnversioned;
import org.tigris.subversion.svnclientadapter.SVNNodeKind;
import org.tigris.subversion.svnclientadapter.SVNRevision;
import org.tigris.subversion.svnclientadapter.SVNRevisionRange;
import org.tigris.subversion.svnclientadapter.SVNStatusKind;
import org.tigris.subversion.svnclientadapter.SVNUrl;
import org.tigris.subversion.svnclientadapter.javahl.AbstractJhlClientAdapter;
import org.tigris.subversion.svnclientadapter.javahl.JhlClientAdapterFactory;

public class SVN extends VCS implements IVCS {

  static {
    try {
      JhlClientAdapterFactory.setup();
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
  }

  private ISVNClientAdapter svnClient;
  private File root;

  public SVN() {
    svnClient = SVNClientAdapterFactory.createSVNClient( JhlClientAdapterFactory.JAVAHL_CLIENT );
  }

  @Override
  public String getType() {
    return IVCS.SVN;
  }

  @Override
  public void add( String name ) {
    try {
      svnClient.addFile( new File( directory, name ) );
    } catch ( SVNClientException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    }
  }

  @Override
  public void addRemote( String value ) {
    try {
      svnClient.relocate( getRemote(), value, directory, true );
    } catch ( SVNClientException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    }
  }

  @Override
  public boolean cloneRepo( String directory, String url ) {
    try {
      svnClient.checkout( new SVNUrl( url ), new File( directory ), SVNRevision.HEAD, true );
      return true;
    } catch ( SVNClientException e ) {
      if ( e.getMessage().contains( "Authorization" ) ) {
        if ( promptUsernamePassword() ) {
          return cloneRepo( directory, url );
        }
      }
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      return false;
    } catch ( MalformedURLException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      return false;
    }
  }

  @Override
  public boolean createBranch( String name ) {
    if ( !isClean() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Dirty working-tree" );
      return false;
    }
    try {
      svnClient.copy( new SVNUrl( getRemoteRoot() + File.separator + getBranch() ),
          new SVNUrl( getRemoteRoot() + File.separator + name ),
          "Created a branch by Spoon", SVNRevision.HEAD, true );
      return true;
    } catch ( MalformedURLException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    } catch ( SVNClientException e ) {
      if ( e.getMessage().contains( "Authorization" ) ) {
        if ( promptUsernamePassword() ) {
          return createBranch( name );
        }
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
    return false;
  }

  @Override
  public boolean deleteBranch( String name, boolean force ) {
    try {
      svnClient.remove( new SVNUrl[]{ new SVNUrl( getRemoteRoot() + File.separator + name ) },
          "Deleted a branch by Spoon" );
      return true;
    } catch ( MalformedURLException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    } catch ( SVNClientException e ) {
      if ( e.getMessage().contains( "Authorization" ) ) {
        if ( promptUsernamePassword() ) {
          return deleteBranch( name, force );
        }
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
    return false;
  }

  @Override
  public boolean createTag( String name ) {
    if ( !isClean() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Dirty working-tree" );
      return false;
    }
    try {
      svnClient.copy( new SVNUrl( getRemoteRoot() + File.separator + getBranch() ),
          new SVNUrl( getRemoteRoot() + File.separator + name ),
          "Created a tag by Spoon", SVNRevision.HEAD, true );
      return true;
    } catch ( MalformedURLException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    } catch ( SVNClientException e ) {
      if ( e.getMessage().contains( "Authorization" ) ) {
        if ( promptUsernamePassword() ) {
          return createTag( name );
        }
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
    return false;
  }

  @Override
  public boolean deleteTag( String name ) {
    try {
      svnClient.remove( new SVNUrl[]{ new SVNUrl( getRemoteRoot() + File.separator + name ) },
          "Deleted a tag by Spoon" );
      return true;
    } catch ( MalformedURLException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    } catch ( SVNClientException e ) {
      if ( e.getMessage().contains( "Authorization" ) ) {
        if ( promptUsernamePassword() ) {
          return deleteTag( name );
        }
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
      }
    }
    return false;
  }

  @Override
  public void revertPath( String path ) {
    try {
      svnClient.revert( new File( directory + File.separator + FilenameUtils.separatorsToSystem( path ) ), false );
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
  }

  @Override
  public void resetPath( String path ) {
    String fullpath = directory + File.separator + FilenameUtils.separatorsToSystem( path );
    try {
      ISVNStatus status = svnClient.getSingleStatus( new File( fullpath ) );
      if ( !status.getTextStatus().toString().equals( "added" ) ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Versioned files are always staged for the next commit" );
        return;
      }
    } catch ( SVNClientException e1 ) {
      e1.printStackTrace();
    }
    AbstractJhlClientAdapter client = (AbstractJhlClientAdapter) svnClient;
    try {
      client.getSVNClient().remove(
          new HashSet<String>( Arrays.asList( fullpath ) ),
          false, true, null, null, null );
    } catch ( ClientException e ) {
      e.printStackTrace();
    }
  }

  @Override
  public String diff( String oldCommitId, String newCommitId, String file ) {
    AbstractJhlClientAdapter client = (AbstractJhlClientAdapter) svnClient;
    OutputStream outStream = new ByteArrayOutputStream();
    try {
      String target = directory + File.separator + FilenameUtils.separatorsToSystem( file );
      ISVNInfo info = svnClient.getInfoFromWorkingCopy( new File( target ) );
      if ( info instanceof SVNInfoUnversioned ) {
        return "Unversioned";
      }
      if ( info.getRevision() == null || info.isCopied() ) { // not commited yet or copied
        oldCommitId = null;
      }
      client.getSVNClient().diff(
          target,
          null, resolveRevision( oldCommitId ), resolveRevision( newCommitId ),
          directory.replace( "\\", "/" ), outStream, Depth.infinityOrImmediates( true ), null, true, false, false, true, false, false );
      return outStream.toString().replaceAll( "\n", System.getProperty( "line.separator" ) );
    } catch ( Exception e ) {
      return e.getMessage();
    }
  }

  @Override
  public String getAuthorName( String commitId ) {
    UIRepositoryObjectRevisions revisions = getRevisions();
    UIRepositoryObjectRevision revision = revisions.stream()
        .filter( rev -> rev.getName().equals( commitId ) )
        .findFirst().get();
    return revision.getLogin();
  }

  @Override
  public String getCommitMessage( String commitId ) {
    UIRepositoryObjectRevisions revisions = getRevisions();
    UIRepositoryObjectRevision revision = revisions.stream()
        .filter( rev -> rev.getName().equals( commitId ) )
        .findFirst().get();
    return revision.getComment();
  }

  @Override
  public String getCommitId( String revstr ) {
    return revstr;
  }

  @Override
  public String getParentCommitId( String revstr ) {
    try {
      return getCommitId( Long.toString( ( Long.parseLong( revstr ) - 1 ) ) );
    } catch ( NumberFormatException e ) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public String getBranch() {
    try {
      String branch = svnClient.getInfoFromWorkingCopy( root ).getUrlString().replaceFirst( getRemoteRoot(), "" );
      return branch.replaceAll( "^/", "" );
    } catch ( SVNClientException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public List<String> getBranches() {
    ISVNDirEntry[] dirEntries = null;
    SVNUrl url = null;
    try {
      url = new SVNUrl( getRemoteRoot() );
      dirEntries = svnClient.getList( url, SVNRevision.HEAD, true );
    } catch ( SVNClientException e ) {
      e.printStackTrace();
      return null;
    } catch ( MalformedURLException e ) {
      e.printStackTrace();
    }
    return Arrays.stream( dirEntries )
      .filter( dirEntry -> dirEntry.getNodeKind() == SVNNodeKind.DIR )
      .map( dirEntry -> dirEntry.getPath() )
      .collect( Collectors.toList() );
  }

  @Override
  public List<String> getLocalBranches() {
    return getBranches();
  }

  @Override
  public List<String> getTags() {
    return getBranches();
  }

  @Override
  public String getRemote() {
    try {
      return svnClient.getInfoFromWorkingCopy( root ).getUrlString();
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
    return null;
  }

  private String getRemoteRoot() {
    try {
      return svnClient.getInfoFromWorkingCopy( root ).getRepository().toString();
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public boolean hasRemote() {
    return true;
  }

  @Override
  public boolean commit( String authorName, String message ) {
    try {
      svnClient.commit( new File[]{ root }, message, true );
      return true;
    } catch ( SVNClientException e ) {
      if ( e.getMessage().contains( "Authorization" ) && promptUsernamePassword() ) {
        return commit( authorName, message );
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
        return false;
      }
    } catch ( Exception e ) {
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public UIRepositoryObjectRevisions getRevisions() {
    UIRepositoryObjectRevisions revisions = new UIRepositoryObjectRevisions();
    ISVNLogMessage[] messages = null;
    try {
      messages = svnClient.getLogMessages( root, new SVNRevision.Number( 0 ),
          svnClient.getInfoFromWorkingCopy( root ).getRevision(), false, false, 0 );
    } catch ( SVNClientException e ) {
      if ( e.getMessage().contains( "Authorization" ) && promptUsernamePassword() ) {
        return getRevisions();
      } else {
        e.printStackTrace();
      }
    }
    if ( messages != null ) {
      Arrays.stream( messages )
        .filter( logMessage -> logMessage.getRevision().getNumber() != 0 )
        .forEach( logMessage -> {
          PurObjectRevision rev = new PurObjectRevision(
            logMessage.getRevision().toString(),
            logMessage.getAuthor(),
            logMessage.getDate(),
            logMessage.getMessage() );
          revisions.add( new UIRepositoryObjectRevision( (ObjectRevision) rev ) );
        } );
    }
    if ( !isClean() ) {
      PurObjectRevision rev = new PurObjectRevision(
          WORKINGTREE,
          "*",
          new Date(),
          " // " + VCS.WORKINGTREE );
      revisions.add( new UIRepositoryObjectRevision( (ObjectRevision) rev ) );
    }
    Collections.reverse( revisions );
    return revisions;
  }

  @Override
  public List<UIFile> getUnstagedFiles() {
    List<UIFile> files = new ArrayList<UIFile>();
    try {
      svnClient.getStatus( root, true, false, false,
        false, false, ( String path, ISVNStatus status ) -> {
          if ( status.getTextStatus().equals( SVNStatusKind.UNVERSIONED ) ) {
            files.add( new UIFile( path.replaceFirst( directory.replace( "\\", "/" ) + "/", "" ), convertTypeToGit( status.getTextStatus().toString() ), false ) );
          }
        } );
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
    return files;
  }

  @Override
  public List<UIFile> getStagedFiles() {
    List<UIFile> files = new ArrayList<UIFile>();
    try {
      svnClient.getStatus( root, true, false, false,
        false, false, ( String path, ISVNStatus status ) -> {
          if ( !status.getTextStatus().equals( SVNStatusKind.UNVERSIONED ) ) {
            files.add( new UIFile( path.replaceFirst( directory.replace( "\\", "/" ) + "/", "" ), convertTypeToGit( status.getTextStatus().toString() ), true ) );
          }
        } );
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
    return files;
  }

  @Override
  public List<UIFile> getStagedFiles( String oldCommitId, String newCommitId ) {
    List<UIFile> files = new ArrayList<UIFile>();
    try {
      Arrays.stream( svnClient.diffSummarize( svnClient.getInfoFromWorkingCopy( root ).getUrl(), null, new SVNRevision.Number( Long.parseLong( oldCommitId ) ),
           new SVNRevision.Number( Long.parseLong( newCommitId ) ),
          100, true ) )
        .forEach( diffStatus -> {
            files.add( new UIFile( diffStatus.getPath().replaceFirst( directory.replace( "\\", "\\\\" ), "" ),
                convertTypeToGit( diffStatus.getDiffKind().toString() ), false ) );
        }
        );
    } catch ( NumberFormatException e ) {
      e.printStackTrace();
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
    return files;
  }

  @Override
  public boolean hasStagedFiles() {
    return !getStagedFiles().isEmpty();
  }

  @Override
  public boolean isClean() {
    try {
      return getStagedFiles().isEmpty() & getUnstagedFiles().isEmpty();
    } catch ( Exception e ) {
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public InputStream open( String file, String commitId ) {
    try {
      if ( commitId.equals( IVCS.WORKINGTREE ) ) {
        return new FileInputStream( new File( directory + File.separator + FilenameUtils.separatorsToSystem( file ) ) );
      } else if ( commitId.equals( Constants.HEAD ) ) {
        return svnClient.getContent( new File( directory + File.separator + FilenameUtils.separatorsToSystem( file ) ),
          SVNRevision.HEAD );
      } else {
        return svnClient.getContent( svnClient.getInfoFromWorkingCopy( root ).getUrl().appendPath( file ),
          new SVNRevision.Number( Long.parseLong( commitId ) ) );
      }
    } catch ( NumberFormatException e ) {
      e.printStackTrace();
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    } catch ( FileNotFoundException e ) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public void openRepo( String baseDirectory ) throws Exception {
    directory = baseDirectory;
    root = new File( directory );
  }

  @Override
  public boolean pull() {
    try {
      SVNRevision.Number lastRevision = svnClient.getInfoFromWorkingCopy( root ).getRevision();
      long newLastRevision = svnClient.update( root, SVNRevision.HEAD, true );
      if ( lastRevision.getNumber() == newLastRevision ) {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), "Up-to-date" );
      } else {
        showMessageBox( BaseMessages.getString( PKG, "Dialog.Success" ), BaseMessages.getString( PKG, "Dialog.Success" ) );
      }
      return true;
    } catch ( SVNClientException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    }
    return false;
  }

  @Override
  public boolean merge() {
    String name = null;
    List<String> names = getBranches();
    EnterSelectionDialog esd = new EnterSelectionDialog( shell, names.toArray( new String[names.size()] ),
      "Select Branch", "Select the branch to be merged (reintegrated) into the current working copy" );
    name = esd.open();
    if ( name == null ) {
      return false;
    }
    try {
      svnClient.mergeReintegrate( new SVNUrl( getRemoteRoot() + File.separator + name ),
          SVNRevision.HEAD, root, false, false );
      return true;
    } catch ( MalformedURLException e ) {
      e.printStackTrace();
    } catch ( SVNClientException e ) {
      e.printStackTrace();
    }
    return false;
  }

  @Override
  public boolean rollback( String name ) {
    if ( !isClean() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Dirty working-tree" );
      return false;
    }
    try {
      svnClient.merge( new SVNUrl( getRemote() ),
          null,
          new SVNRevisionRange[] { new SVNRevisionRange(
              svnClient.getInfoFromWorkingCopy( root ).getRevision(),
              new SVNRevision.Number( Long.parseLong( name ) )
              ) },
          root,
          false, 100, true, false, false );
      return true;
    } catch ( NumberFormatException e ) {
      e.printStackTrace();
    } catch ( SVNClientException e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    } catch ( MalformedURLException e ) {
      e.printStackTrace();
    }
    return false;
  }

  @Override
  public void closeRepo() {
    root = null;
  }

  @Override
  public void checkout( String name ) {
    if ( !isClean() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Dirty working-tree" );
      return;
    }
    try {
      svnClient.update( root, new SVNRevision.Number( Long.parseLong( name ) ), true );
    } catch ( Exception e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    }
  }

  @Override
  public void checkoutBranch( String name ) {
    if ( !isClean() ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), "Dirty working-tree" );
      return;
    }
    try {
      svnClient.switchToUrl( root, new SVNUrl( getRemoteRoot() + "/" + name ), SVNRevision.HEAD, true );
    } catch ( Exception e ) {
      showMessageBox( BaseMessages.getString( PKG, "Dialog.Error" ), e.getMessage() );
    }
  }

  @Override
  public void checkoutTag( String name ) {
    checkoutBranch( name );
  }

  private static ChangeType convertTypeToGit( String type ) {
    if ( type.equals( "added" ) | type.equals( "unversioned" ) ) {
      return ChangeType.ADD;
    } else if ( type.equals( "deleted" ) | type.equals( "missing" ) ) {
      return ChangeType.DELETE;
    } else if ( type.equals( "modified" ) | type.equals( "normal" ) | type.equals( "replaced" ) ) {
      return ChangeType.MODIFY;
    } else {
      return ChangeType.MODIFY;
    }
  }

  @Override
  public void setCredential( String username, String password ) {
    svnClient.setUsername( username );
    svnClient.setPassword( password );
  }

  private Revision resolveRevision( String commitId ) {
    if ( commitId == null ) {
      return Revision.BASE;
    } else if ( commitId.equals( Constants.HEAD ) ) {
      return Revision.HEAD;
    } else if ( commitId.equals( IVCS.INDEX ) ) {
      return Revision.WORKING;
    } else {
      return Revision.getInstance( Long.parseLong( commitId ) );
    }
  }
}

package org.pentaho.di.git.spoon;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.git.spoon.dialog.CloneRepositoryDialog;
import org.pentaho.di.git.spoon.dialog.EditRepositoryDialog;
import org.pentaho.di.git.spoon.dialog.UsernamePasswordDialog;
import org.pentaho.di.git.spoon.model.GitRepository;
import org.pentaho.di.git.spoon.model.UIGit;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.ui.core.dialog.EnterSelectionDialog;
import org.pentaho.di.ui.spoon.ISpoonMenuController;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.metastore.api.IMetaStore;
import org.pentaho.metastore.api.exceptions.MetaStoreException;
import org.pentaho.metastore.persist.MetaStoreFactory;
import org.pentaho.metastore.util.PentahoDefaults;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.util.XulDialogCallback.Status;
import org.pentaho.ui.xul.util.XulDialogLambdaCallback;

import com.google.common.annotations.VisibleForTesting;

public class GitSpoonMenuController extends AbstractXulEventHandler implements ISpoonMenuController {

  private static final Class<?> PKG = GitController.class;

  private GitController gitController;

  public void setGitController( GitController gitController ) {
    this.gitController = gitController;
  }

  public void openRepo() {
    IMetaStore metaStore = Spoon.getInstance().getMetaStore();
    MetaStoreFactory<GitRepository> repoFactory = new MetaStoreFactory<GitRepository>( GitRepository.class, metaStore, PentahoDefaults.NAMESPACE );

    try {
      List<String> names = repoFactory.getElementNames();
      Collections.sort( names );
      EnterSelectionDialog esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Repository", "Select the repository to open..." );
      String name = esd.open();

      if ( name == null ) {
        return;
      }
      GitRepository repo = repoFactory.loadElement( name );
      gitController.openGit( repo );
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  @Override
  public void updateMenu( Document doc ) {
  }

  @Override
  public String getName() {
    return "gitSpoonMenuController";
  }

  public void addRepo() throws MetaStoreException, XulException {
    IMetaStore metaStore = Spoon.getInstance().getMetaStore();
    MetaStoreFactory<GitRepository> repoFactory = new MetaStoreFactory<GitRepository>( GitRepository.class, metaStore, PentahoDefaults.NAMESPACE );
    GitRepository repo = new GitRepository();
    EditRepositoryDialog dialog = new EditRepositoryDialog( getShell(), repo );
    if ( dialog.open() == Window.OK ) {
      repoFactory.saveElement( repo );

      XulConfirmBox confirmBox = (XulConfirmBox) document.createElement( "confirmbox" );
      confirmBox.setTitle( "Success" );
      confirmBox.setMessage( "Open now?" );
      confirmBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
      confirmBox.setCancelLabel( BaseMessages.getString( PKG, "Dialog.Cancel" ) );
      confirmBox.addDialogCallback( (XulDialogLambdaCallback<Object>) ( sender, returnCode, retVal ) -> {
        if ( returnCode == Status.ACCEPT ) {
          gitController.openGit( repo );
        }
      } );
      confirmBox.open();
    }
  }

  public void removeRepo() throws MetaStoreException {
    IMetaStore metaStore = Spoon.getInstance().getMetaStore();
    MetaStoreFactory<GitRepository> repoFactory = new MetaStoreFactory<GitRepository>( GitRepository.class, metaStore, PentahoDefaults.NAMESPACE );

    List<String> names = repoFactory.getElementNames();
    Collections.sort( names );
    EnterSelectionDialog esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Repository", "Select the repository to remove..." );
    String name = esd.open();

    if ( name != null ) {
      repoFactory.deleteElement( name );
      showMessageBox( "Success", "Success" );
    }
  }

  public void editRepo() throws MetaStoreException {
    IMetaStore metaStore = Spoon.getInstance().getMetaStore();
    MetaStoreFactory<GitRepository> repoFactory = new MetaStoreFactory<GitRepository>( GitRepository.class, metaStore, PentahoDefaults.NAMESPACE );

    List<String> names = repoFactory.getElementNames();
    Collections.sort( names );
    EnterSelectionDialog esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Repository", "Select the repository to edit..." );
    String name = esd.open();

    if ( name == null ) {
      return;
    }
    GitRepository repo = repoFactory.loadElement( name );
    EditRepositoryDialog dialog = new EditRepositoryDialog( getShell(), repo );
    if ( dialog.open() == Window.OK ) {
      repoFactory.saveElement( repo );
    }
  }

  public void cloneRepo() {
    GitRepository repo = new GitRepository();
    CloneRepositoryDialog dialog = getCloneRepositoryDialog( repo );
    if ( dialog.open() == Window.OK ) {
      if ( !new File( dialog.getDirectory() ).exists() ) {
        showMessageBox( "Error", dialog.getDirectory() + " does not exist" );
        return;
      }
      String url = dialog.getURL();
      String directory = null;
      try {
        directory = dialog.getDirectory() + File.separator + dialog.getCloneAs();
        Git git = UIGit.cloneRepo( directory, url );
        git.close();
        showMessageBox( "Success", "Success" );
        saveRepository( repo );
        gitController.openGit( repo );
      } catch ( Exception e ) {
        if ( e instanceof TransportException
            && e.getMessage().contains( "Authentication is required but no CredentialsProvider has been registered" ) ) {
          cloneRepoWithUsernamePassword( directory, url );
          try {
            saveRepository( repo );
            gitController.openGit( repo );
          } catch ( MetaStoreException e1 ) {
            showMessageBox( "Error", e1.getLocalizedMessage() );
          }
        } else {
          showMessageBox( "Error", e.getLocalizedMessage() );
        }
      }
    }
  }

  public void cloneRepoWithUsernamePassword( String directory, String url ) {
    UsernamePasswordDialog dialog = getUsernamePasswordDialog();
    if ( dialog.open() == Window.OK ) {
      String username = dialog.getUsername();
      String password = dialog.getPassword();
      try {
        Git git = UIGit.cloneRepo( directory, url, username, password );
        git.close();
        showMessageBox( "Success", "Success" );
      } catch ( Exception e ) {
        showMessageBox( "Error", e.getLocalizedMessage() );
      }
    }
  }

  @VisibleForTesting
  void showMessageBox( String title, String message ) {
    try {
      XulMessageBox messageBox = (XulMessageBox) document.createElement( "messagebox" );
      messageBox.setTitle( title );
      messageBox.setMessage( message );
      messageBox.open();
    } catch ( XulException e ) {
      e.printStackTrace();
    }
  }

  @VisibleForTesting
  CloneRepositoryDialog getCloneRepositoryDialog( GitRepository repo ) {
    return new CloneRepositoryDialog( getShell(), repo );
  }

  @VisibleForTesting
  UsernamePasswordDialog getUsernamePasswordDialog() {
    return new UsernamePasswordDialog( getShell() );
  }

  @VisibleForTesting
  void saveRepository( GitRepository repo ) throws MetaStoreException {
    IMetaStore metaStore = Spoon.getInstance().getMetaStore();
    MetaStoreFactory<GitRepository> repoFactory = new MetaStoreFactory<GitRepository>( GitRepository.class, metaStore, PentahoDefaults.NAMESPACE );
    repoFactory.saveElement( repo );
  }

  Shell getShell() {
    return Spoon.getInstance().getShell();
  }
}
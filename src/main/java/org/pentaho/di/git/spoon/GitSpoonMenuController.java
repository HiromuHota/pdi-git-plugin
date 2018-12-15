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

package org.pentaho.di.git.spoon;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.git.spoon.dialog.CloneRepositoryDialog;
import org.pentaho.di.git.spoon.dialog.EditRepositoryDialog;
import org.pentaho.di.git.spoon.dialog.UsernamePasswordDialog;
import org.pentaho.di.git.spoon.model.GitRepository;
import org.pentaho.di.git.spoon.model.IVCS;
import org.pentaho.di.git.spoon.model.SVN;
import org.pentaho.di.git.spoon.model.UIGit;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.ui.core.dialog.EnterSelectionDialog;
import org.pentaho.di.ui.core.dialog.ErrorDialog;
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

    MetaStoreFactory<GitRepository> repoFactory = getRepoFactory();
    try {
      List<String> names = repoFactory.getElementNames();
      Collections.sort( names );
      EnterSelectionDialog esd = new EnterSelectionDialog( getShell(), names.toArray( new String[names.size()] ), "Select Repository", "Select the repository to open..." );
      String name = esd.open();

      if ( name == null ) {
        return;
      }

      openRepo( repoFactory, name );
    } catch ( Exception e ) {
      new ErrorDialog( Spoon.getInstance().getShell(), "Error", "Error opening Git Reposisory", e );
    }
  }

  private void openRepo( MetaStoreFactory<GitRepository> repoFactory, String repositoryName ) throws MetaStoreException {
    GitRepository repo = repoFactory.loadElement( repositoryName );
    gitController.openGit( repo );
  }

  /**
   * Open a repository with a given name.
   * Convenience method so we can call this method from other places/plugins.
   *
   * @param repositoryName The name of the repository to open
   * @throws MetaStoreException
   */
  public void openRepo( String repositoryName ) throws MetaStoreException {
    MetaStoreFactory<GitRepository> repoFactory = getRepoFactory();
    openRepo(repoFactory, repositoryName);
  }

  /**
   * Allow other plugins to see which Git repositories are defined.
   * @return A list of Git Reposisoty names
   * @throws MetaStoreException
   */
  public List<String> getRepoNames() throws MetaStoreException {
    MetaStoreFactory<GitRepository> repoFactory = getRepoFactory();
    return repoFactory.getElementNames();
  }

  public Boolean isRepoEmpty() throws MetaStoreException {
    return getRepoFactory().getElementNames().isEmpty();
  }

  @Override
  public void updateMenu( Document doc ) {
  }

  @Override
  public String getName() {
    return "gitSpoonMenuController";
  }

  public void addRepo() throws MetaStoreException, XulException {
    MetaStoreFactory<GitRepository> repoFactory = getRepoFactory();
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
    MetaStoreFactory<GitRepository> repoFactory = getRepoFactory();

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
    MetaStoreFactory<GitRepository> repoFactory = getRepoFactory();

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
      if ( !new File( repo.getActualDirectory() ).exists() ) {
        showMessageBox( "Error", repo.getActualDirectory() + " does not exist" );
        return;
      }
      String url = dialog.getURL();
      String directory = repo.getActualDirectory();
      IVCS vcs = getVCS( repo );
      vcs.setShell( getShell() );
      if ( vcs.cloneRepo( directory, url ) ) {
        showMessageBox( "Success", "Success" );
        saveRepository( repo );
        gitController.openGit( repo );
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
  void saveRepository( GitRepository repo ) {
    MetaStoreFactory<GitRepository> repoFactory = getRepoFactory();
    try {
      repoFactory.saveElement( repo );
    } catch ( MetaStoreException e ) {
      e.printStackTrace();
    }
  }

  Shell getShell() {
    return Spoon.getInstance().getShell();
  }

  private MetaStoreFactory<GitRepository> getRepoFactory() {
    IMetaStore metaStore = Spoon.getInstance().getMetaStore();
    return new MetaStoreFactory<GitRepository>( GitRepository.class, metaStore, PentahoDefaults.NAMESPACE );
  }

  @VisibleForTesting
  IVCS getVCS( GitRepository repo ) {
    if ( repo.getType() == null || repo.getType().equals( IVCS.GIT ) ) {
      return new UIGit();
    } else {
      return new SVN();
    }
  }
}

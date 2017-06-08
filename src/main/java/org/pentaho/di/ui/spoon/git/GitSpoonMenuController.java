package org.pentaho.di.ui.spoon.git;

import java.io.File;

import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.ui.spoon.ISpoonMenuController;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.git.dialog.CloneRepositoryDialog;
import org.pentaho.di.ui.spoon.git.dialog.UsernamePasswordDialog;
import org.pentaho.di.ui.spoon.git.model.UIGit;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;

import com.google.common.annotations.VisibleForTesting;

public class GitSpoonMenuController extends AbstractXulEventHandler implements ISpoonMenuController {

  @Override
  public void updateMenu( Document doc ) {
  }

  @Override
  public String getName() {
    return "gitSpoonMenuController";
  }

  public void cloneRepo() {
    CloneRepositoryDialog dialog = getCloneRepositoryDialog();
    if ( dialog.open() == Window.OK ) {
      if ( !new File( dialog.getDirectory() ).exists() ) {
        showMessageBox( "Error", dialog.getDirectory() + " does not exist" );
        return;
      }
      String url = dialog.getURL();
      String directory = null;
      try {
        URIish uri = new URIish( url );
        directory = dialog.getDirectory() + File.separator + uri.getHumanishName();
        Git git = UIGit.cloneRepo( directory, url );
        git.close();
        showMessageBox( "Success", "Success" );
      } catch ( Exception e ) {
        if ( e instanceof TransportException
            && e.getMessage().contains( "Authentication is required but no CredentialsProvider has been registered" ) ) {
          cloneRepoWithUsernamePassword( directory, url );
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
  CloneRepositoryDialog getCloneRepositoryDialog() {
    return new CloneRepositoryDialog( getShell() );
  }

  @VisibleForTesting
  UsernamePasswordDialog getUsernamePasswordDialog() {
    return new UsernamePasswordDialog( getShell() );
  }

  Shell getShell() {
    return Spoon.getInstance().getShell();
  }
}

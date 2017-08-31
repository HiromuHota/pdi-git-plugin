package org.pentaho.di.git.spoon;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Composite;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.SpoonPerspectiveImageProvider;
import org.pentaho.di.ui.spoon.SpoonPerspectiveListener;
import org.pentaho.di.ui.spoon.XulSpoonResourceBundle;
import org.pentaho.di.ui.xul.KettleXulLoader;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.XulOverlay;
import org.pentaho.ui.xul.XulRunner;
import org.pentaho.ui.xul.components.XulConfirmBox;
import org.pentaho.ui.xul.containers.XulMenupopup;
import org.pentaho.ui.xul.containers.XulVbox;
import org.pentaho.ui.xul.impl.XulEventHandler;
import org.pentaho.ui.xul.swt.SwtXulRunner;
import org.pentaho.ui.xul.swt.tags.SwtDeck;
import org.pentaho.ui.xul.util.XulDialogCallback.Status;
import org.pentaho.ui.xul.util.XulDialogLambdaCallback;

public class GitPerspective implements SpoonPerspectiveImageProvider {

  private static Class<?> PKG = GitPerspective.class;
  private ResourceBundle resourceBundle = new XulSpoonResourceBundle( PKG );

  final String PERSPECTIVE_ID = "010-git"; //$NON-NLS-1$
  final String PERSPECTIVE_NAME = "gitPerspective"; //$NON-NLS-1$

  private XulDomContainer container;
  private GitController controller;
  private GitSpoonMenuController gitSpoonMenuController;
  private XulVbox box;

  public GitPerspective() throws XulException {
    // Loading Xul Document
    KettleXulLoader loader = new KettleXulLoader();
    loader.registerClassLoader( getClass().getClassLoader() );
    container = loader.loadXul( "org/pentaho/di/git/spoon/xul/git_perspective.xul", resourceBundle );

    // Adding Event Handlers
    controller = new GitController();
    gitSpoonMenuController = new GitSpoonMenuController();
    gitSpoonMenuController.setGitController( controller );
    container.addEventHandler( controller );
    container.addEventHandler( gitSpoonMenuController );

    final XulRunner runner = new SwtXulRunner();
    runner.addContainer( container );
    runner.initialize(); //calls any onload events

    /*
     * To make compatible with webSpoon
     * Create a temporary parent for the UI and then call layout().
     * A different parent will be assigned to the UI in SpoonPerspectiveManager.PerspectiveManager.performInit().
     */
    SwtDeck deck = (SwtDeck) Spoon.getInstance().getXulDomContainer().getDocumentRoot().getElementById( "canvas-deck" );
    box = deck.createVBoxCard();
    getUI().setParent( (Composite) box.getManagedObject() );
    getUI().layout();

    /**
     * Hack: setAccelerator 'CTRL(CMD) + D' to "Data Integration" menu
     */
    int mask = 'D';
    if ( System.getProperty( "KETTLE_CONTEXT_PATH" ) == null ) { // Spoon
      boolean isMac = System.getProperty( "os.name" ).toLowerCase().indexOf( "mac" ) >= 0;
      mask += isMac ? SWT.COMMAND : SWT.CTRL;
    } else { // webSpoon
      mask += SWT.CTRL;
    }
    int keyCode = mask;
    XulMenupopup menuPopup = (XulMenupopup) Spoon.getInstance().getXulDomContainer().getDocumentRoot().getElementById( "view-perspectives-popup" );
    MenuManager menuMgr = (MenuManager) menuPopup.getManagedObject();
    // No guarantee that "Data Integration" gets "menuitem-0" as its ID, but looks ok so far
    Stream.of( menuMgr.getItems() )
      .filter( menu -> menu.getId().equals( "menuitem-0" ) ).findFirst().ifPresent( menu -> {
        IAction action = ( (ActionContributionItem) menu ).getAction();
        action.setAccelerator( keyCode );
      } );
  }

  @Override
  public String getId() {
    return PERSPECTIVE_ID;
  }

  public String getName() {
    return PERSPECTIVE_NAME;
  }

  @Override
  public Composite getUI() {
    return (Composite) container.getDocumentRoot().getRootElement().getFirstChild().getManagedObject();
  }

  @Override
  public String getDisplayName( Locale l ) {
    return BaseMessages.getString( PKG, "Git.Perspective.perspectiveName" );
  }

  @Override
  public InputStream getPerspectiveIcon() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setActive( boolean active ) {
    try { // Dispose the temporary parent
      ( (Composite) box.getManagedObject() ).dispose();
    } catch ( SWTException e ) {
      // To nothing
    }
    if ( active ) {
      if ( !controller.isOpen() ) {
        try {
          if ( gitSpoonMenuController.isRepoEmpty() ) {
            XulConfirmBox confirmBox = (XulConfirmBox) container.getDocumentRoot().createElement( "confirmbox" );
            confirmBox.setTitle( "No repository" );
            confirmBox.setMessage( "Add a new one?" );
            confirmBox.setAcceptLabel( BaseMessages.getString( PKG, "Dialog.Ok" ) );
            confirmBox.setCancelLabel( BaseMessages.getString( PKG, "Dialog.Cancel" ) );
            confirmBox.addDialogCallback( (XulDialogLambdaCallback<Object>) ( sender, returnCode, retVal ) -> {
              if ( returnCode == Status.ACCEPT ) {
                try {
                  gitSpoonMenuController.addRepo();
                } catch ( Exception e ) {
                  e.printStackTrace();
                }
              }
            } );
            confirmBox.open();
          } else {
            gitSpoonMenuController.openRepo();
          }
        } catch ( Exception e ) {
          e.printStackTrace();
        }
      }
      if ( controller.isOpen() ) {
        controller.fireSourceChanged();
      }
    }
  }

  @Override
  public List<XulOverlay> getOverlays() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<XulEventHandler> getEventHandlers() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void addPerspectiveListener( SpoonPerspectiveListener listener ) {
    // TODO Auto-generated method stub

  }

  @Override
  public EngineMetaInterface getActiveMeta() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getPerspectiveIconPath() {
    // TODO Auto-generated method stub
    return null;
  }

  public GitController getController() {
    return this.controller;
  }
}

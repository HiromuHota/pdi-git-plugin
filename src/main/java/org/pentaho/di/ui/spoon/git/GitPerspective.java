package org.pentaho.di.ui.spoon.git;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.SpoonPerspectiveImageProvider;
import org.pentaho.di.ui.spoon.SpoonPerspectiveListener;
import org.pentaho.di.ui.xul.KettleXulLoader;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.XulOverlay;
import org.pentaho.ui.xul.XulRunner;
import org.pentaho.ui.xul.containers.XulBox;
import org.pentaho.ui.xul.containers.XulVbox;
import org.pentaho.ui.xul.impl.XulEventHandler;
import org.pentaho.ui.xul.swt.SwtXulRunner;
import org.pentaho.ui.xul.swt.tags.SwtDeck;

public class GitPerspective implements SpoonPerspectiveImageProvider {

  private static Class<?> PKG = GitPerspective.class;

  final String PERSPECTIVE_ID = "010-git"; //$NON-NLS-1$
  final String PERSPECTIVE_NAME = "gitPerspective"; //$NON-NLS-1$

  private XulDomContainer container;
  private GitController controller;
  private XulVbox box;

  public GitPerspective() throws XulException {
    // Loading Xul Document
    KettleXulLoader loader = new KettleXulLoader();
    loader.registerClassLoader( getClass().getClassLoader() );
    container = loader.loadXul( "org/pentaho/di/ui/spoon/git/xul/git_perspective.xul" );
    addWidgets();

    // Adding Event Handlers
    controller = new GitController();
    container.addEventHandler( controller );
    controller.setXulDomContainer( container );

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
  }

  private void addWidgets() {
    XulBox boxBranch = (XulBox) container.getDocumentRoot().getElementById( "boxBranch" );
    Combo comboDropDown = new Combo( (Composite) boxBranch.getManagedObject(), SWT.DROP_DOWN );
    ( (Composite) boxBranch.getManagedObject() ).setLayout( new FillLayout() );
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
      controller.setActive();
    } else {
      controller.setInactive();
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

}

package org.pentaho.di.spoon.git;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.widgets.Composite;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.ui.spoon.SpoonPerspectiveImageProvider;
import org.pentaho.di.ui.spoon.SpoonPerspectiveListener;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.XulOverlay;
import org.pentaho.ui.xul.XulRunner;
import org.pentaho.ui.xul.impl.XulEventHandler;
import org.pentaho.ui.xul.swt.SwtXulLoader;
import org.pentaho.ui.xul.swt.SwtXulRunner;

public class GitPerspective implements SpoonPerspectiveImageProvider {

  private static Class<?> PKG = GitPerspective.class;

  final String PERSPECTIVE_ID = "010-git"; //$NON-NLS-1$
  final String PERSPECTIVE_NAME = "gitPerspective"; //$NON-NLS-1$

  XulDomContainer container;
  XulEventHandler controller;

  public GitPerspective() throws XulException {
    // Loading Xul Document
    SwtXulLoader loader = new SwtXulLoader();
    loader.registerClassLoader( getClass().getClassLoader() );
    container = loader.loadXul( "org/pentaho/di/spoon/git/perspective.xul" );

    // Adding Event Handlers
    controller = new GitController();
    container.addEventHandler( controller );

    final XulRunner runner = new SwtXulRunner();
    runner.addContainer( container );
    runner.initialize(); //calls any onload events

    controller.setXulDomContainer( container );
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
  public String getDisplayName(Locale l) {
    return BaseMessages.getString( PKG, "Git.Perspective.perspectiveName" );
  }

  @Override
  public InputStream getPerspectiveIcon() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setActive(boolean active) {
    // TODO Auto-generated method stub

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
  public void addPerspectiveListener(SpoonPerspectiveListener listener) {
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

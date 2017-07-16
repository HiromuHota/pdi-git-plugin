package org.pentaho.di.git.spoon;

import static org.pentaho.di.git.spoon.PdiDiff.*;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.extension.ExtensionPoint;
import org.pentaho.di.core.extension.ExtensionPointInterface;
import org.pentaho.di.core.gui.BasePainter;
import org.pentaho.di.core.gui.GCInterface;
import org.pentaho.di.core.gui.Point;
import org.pentaho.di.core.gui.PrimitiveGCInterface.EImage;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.job.JobPainter;
import org.pentaho.di.ui.core.PropsUI;

@ExtensionPoint(
    id = "DrawDiffOnJobEntryExtensionPoint",
    description = "Draws a marker on top of a job entry if it has some change",
    extensionPointId = "JobPainterEnd" )
public class DrawDiffOnJobEntryExtensionPoint implements ExtensionPointInterface {

  @Override
  public void callExtensionPoint( LogChannelInterface log, Object object ) throws KettleException {
    if ( !( object instanceof JobPainter ) ) {
      return;
    }
    int iconsize = PropsUI.getInstance().getIconSize();
    JobPainter painter = (JobPainter) object;
    Point offset = painter.getOffset();
    GCInterface gc = painter.getGc();
    JobMeta jobMeta = painter.getJobMeta();
    jobMeta.getJobCopies().stream().filter( je -> je.getAttribute( ATTR_GIT, ATTR_STATUS ) != null )
      .forEach( je -> {
        if ( jobMeta.getJobversion() == null ? false : jobMeta.getJobversion().startsWith( "git" ) ) {
          String status = je.getAttribute( ATTR_GIT, ATTR_STATUS );
          Point n = je.getLocation();
          String location = "org/pentaho/di/ui/spoon/git/images/";
          if ( status.equals( REMOVED ) ) {
            location += "removed.svg";
          } else if ( status.equals( CHANGED ) ) {
            location += "changed.svg";
          } else if ( status.equals( ADDED ) ) {
            location += "added.svg";
          } else { // Unchanged
            return;
          }
          gc.drawImage( location, getClass().getClassLoader(), ( n.x + iconsize + offset.x ) - ( BasePainter.MINI_ICON_SIZE / 2 ), n.y + offset.y - ( BasePainter.MINI_ICON_SIZE / 2 ) );
        } else {
          je.getAttributesMap().remove( ATTR_GIT );
        }
      } );
  }
}

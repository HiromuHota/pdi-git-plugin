package org.pentaho.di.ui.spoon.git;

import java.util.Map;
import java.util.Optional;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.job.entry.JobEntryCopy;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepMeta;

public class PdiDiff {
  public static String ATTR_GIT = "Git";
  public static String ATTR_STATUS = "Status";

  public static String UNCHANGED = "UNCHANGED";
  public static String CHANGED = "CHANGED";
  public static String REMOVED = "REMOVED";
  public static String ADDED = "ADDED";

  public static TransMeta compareSteps( TransMeta transMeta1, TransMeta transMeta2, boolean isForward ) {
    transMeta1.getSteps().forEach( step -> {
      Optional<StepMeta> step2 = transMeta2.getSteps().stream()
          .filter( obj -> step.getName().equals( obj.getName() ) ).findFirst();
      String status = null;
      if ( step2.isPresent() ) {
        Map<String, String> tmp = null, tmp2 = null;
        try {
          // AttributeMap("Git") cannot affect the XML comparison
          tmp = step.getAttributesMap().remove( ATTR_GIT );
          tmp2 = step2.get().getAttributesMap().remove( ATTR_GIT );
          if ( step.getXML().equals( step2.get().getXML() ) ) {
            status = UNCHANGED;
          } else {
            status = CHANGED;
          }
        } catch ( KettleException e ) {
          e.printStackTrace();
        } finally {
          step.setAttributes( ATTR_GIT, tmp );
          step2.get().setAttributes( ATTR_GIT, tmp2 );
        }
      } else {
        if ( isForward ) {
          status = REMOVED;
        } else {
          status = ADDED;
        }
      }
      step.setAttribute( ATTR_GIT, ATTR_STATUS, status.toString() );
    } );
    return transMeta1;
  }

  public static JobMeta compareJobEntries( JobMeta jobMeta1, JobMeta jobMeta2, boolean isForward ) {
    jobMeta1.getJobCopies().forEach( je -> {
      Optional<JobEntryCopy> je2 = jobMeta2.getJobCopies().stream()
          .filter( obj -> je.getName().equals( obj.getName() ) ).findFirst();
      String status = null;
      if ( je2.isPresent() ) {
        Map<String, String> tmp = null, tmp2 = null;
        // AttributeMap("Git") cannot affect the XML comparison
        tmp = je.getAttributesMap().remove( ATTR_GIT );
        tmp2 = je2.get().getAttributesMap().remove( ATTR_GIT );
        if ( je.getXML().equals( je2.get().getXML() ) ) {
          status = UNCHANGED;
        } else {
          status = CHANGED;
        }
        je.setAttributes( ATTR_GIT, tmp );
        je2.get().setAttributes( ATTR_GIT, tmp2 );
      } else {
        if ( isForward ) {
          status = REMOVED;
        } else {
          status = ADDED;
        }
      }
      je.setAttribute( ATTR_GIT, ATTR_STATUS, status.toString() );
    } );
    return jobMeta1;
  }
}

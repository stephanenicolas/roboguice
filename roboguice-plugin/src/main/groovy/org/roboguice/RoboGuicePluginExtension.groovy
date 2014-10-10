package org.roboguice

/**
 * DSL for the {@link RoboGuicePlugin}.
 * @author SNI
 */
public class RoboGuicePluginExtension {
  /** The name of the annotation database package that
   * RoboBlender will generate when processing main sources.
   * If this property is null, the default package is used.*/
  String annotationDatabasePackageName
  /** The name of the annotation database package that
   * RoboBlender will generate when processing main tests.
   * By default ,this property is the source annotation database package name + ".test" */
  String testAnnotationDatabasePackageName
}

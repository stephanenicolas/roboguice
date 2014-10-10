package org.roboguice

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile

/**
 * The RoboGuice Gradle plugin helps creating roboguice based apps
 * via gradle builds. It will :
 * <ul>
 *   <li> apply all needed dependencies to the project and its build :
 *   <ul>
 *     <li> for roboblender to optimize guice in depth;
 *     <li> to configure byte code weaving used by RG 4 for injection of views, resources and extras.
 *   </ul>
 *   <li> manage the annotation database package name for sources and tests
 *   <li> create the annotation database in the generated folder
 * </ul>
 * The plugin uses a DSL :
 * @see RoboGuicePluginExtension
 * @author SNI
 */
public class RoboGuicePlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    def hasApp = project.plugins.withType(AppPlugin)
    def hasLib = project.plugins.withType(LibraryPlugin)
    if (!hasApp && !hasLib) {
      throw new IllegalStateException(
          "Project is not android lib or app project. Roboguice plugin can't be applied.")
    }

    project.extensions.create("roboguice", RoboGuicePluginExtension)

    final def log = project.logger
    final String LOG_TAG = this.getClass().getName()

    configure(project)

    final def variants
    if (hasApp) {
      variants = project.android.applicationVariants
    } else {
      variants = project.android.libraryVariants
    }

    //create annotation database in generated folder to
    //avoid it to be instrumented (which would fail)
    variants.all { variant ->
      variant.javaCompile.options.compilerArgs += ['-s', project.file('build/generated')]
    }

    project.tasks.withType(JavaCompile) { task ->
      //create annotation databases according to roboguice extension
      //at execution time.. http://stackoverflow.com/q/23962154/693752
      final def roboguiceExtension = project.roboguice
      String annotationDatabaseName = roboguiceExtension.annotationDatabasePackageName
      String testAnnotationDatabaseName = roboguiceExtension.testAnnotationDatabasePackageName

      if (annotationDatabaseName == null) {
        testAnnotationDatabaseName = "test"
      } else if (testAnnotationDatabaseName == null) {
        testAnnotationDatabaseName = annotationDatabaseName + ".test"
      }

      if (!task.name.contains('Test')) {
        if (annotationDatabaseName != null) {
          log.info(LOG_TAG,
              "RoboBlender will process sources in package ${annotationDatabaseName}");
          options.compilerArgs << "-AguiceAnnotationDatabasePackageName=${annotationDatabaseName}"
        }
      } else {
        log.info(LOG_TAG,
            "RoboBlender will process tests sources in package ${testAnnotationDatabaseName}");
        options.compilerArgs << "-AguiceAnnotationDatabasePackageName=${testAnnotationDatabaseName}"
      }
    }
  }

  protected void configure(Project project) {
    //TODO add DSL switches
    project.plugins.apply('com.github.stephanenicolas.injectview')
    project.plugins.apply('com.github.stephanenicolas.injectresource')
    project.plugins.apply('com.github.stephanenicolas.injectextra')
    log.info(LOG_TAG, "Project configured for RG byte code weaving injections.")

    //TODO add DSL switch
    project.dependencies {
      provided 'org.roboguice:roboblender:4.0.0-SNAPSHOT'
    }
    log.info(LOG_TAG, "Project configured for RoboBlender.")

    //TODO add DSL switch
    project.dependencies {
      compile 'org.roboguice:roboguice:4.0.0-SNAPSHOT'
    }
    log.info(LOG_TAG, "Project configured for RoboGuice.")
  }
}
